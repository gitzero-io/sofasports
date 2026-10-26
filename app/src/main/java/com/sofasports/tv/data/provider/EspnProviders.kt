package com.sofasports.tv.data.provider

import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.data.espn.EspnParser
import com.sofasports.tv.data.sportsdb.SportsDbParser
import com.sofasports.tv.domain.DataSource
import com.sofasports.tv.domain.EventFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.NflTeams
import com.sofasports.tv.domain.Scoreboard
import com.sofasports.tv.domain.TeamInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EspnScoresProvider @Inject constructor(
    private val http: HttpClient,
    private val bases: ApiBases,
) : ScoresProvider {
    override suspend fun scoreboard(league: League): Scoreboard {
        if (league.source == DataSource.THE_SPORTS_DB) return boxing()
        val query = league.scoreboardQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
        val body = http.get("${bases.espnBase(league)}/scoreboard$query").bodyAsText()
        return withContext(Dispatchers.Default) { EspnParser.parseScoreboard(body, league) }
    }

    /** TheSportsDB's free key gives the next and the last fight; both are worth showing. */
    private suspend fun boxing(): Scoreboard = coroutineScope {
        val next = async { getOrNull("${bases.sportsDbBase}/eventsnextleague.php?id=$BOXING_LEAGUE_ID") }
        val past = async { getOrNull("${bases.sportsDbBase}/eventspastleague.php?id=$BOXING_LEAGUE_ID") }
        val n = next.await()
        val p = past.await()
        if (n == null && p == null) error("Couldn't reach TheSportsDB")
        withContext(Dispatchers.Default) { SportsDbParser.parseBoxing(n, p) }
    }

    private suspend fun getOrNull(url: String): String? = runCatching {
        val res = http.get(url)
        if (res.status.isSuccess()) res.bodyAsText() else null
    }.getOrNull()

    private companion object {
        /** TheSportsDB's "Boxing" league. */
        const val BOXING_LEAGUE_ID = 4445
    }
}

@Singleton
class EspnGameStatsProvider @Inject constructor(
    private val http: HttpClient,
    private val bases: ApiBases,
) : GameStatsProvider {
    override suspend fun summary(league: League, gameId: String): GameSummary {
        val body = http.get("${bases.espnBase(league)}/summary") { parameter("event", gameId) }.bodyAsText()
        return withContext(Dispatchers.Default) { EspnParser.parseSummary(body, league) }
    }
}

@Serializable private data class TeamsDto(val sports: List<TeamsSportDto> = emptyList())
@Serializable private data class TeamsSportDto(val leagues: List<TeamsLeagueDto> = emptyList())
@Serializable private data class TeamsLeagueDto(val teams: List<TeamsEntryDto> = emptyList())
@Serializable private data class TeamsEntryDto(val team: TeamsTeamDto)

@Serializable
private data class TeamsTeamDto(
    val abbreviation: String = "",
    val displayName: String = "",
    val shortDisplayName: String = "",
    val isActive: Boolean = true,
    val logos: List<TeamsLogoDto> = emptyList(),
)

@Serializable private data class TeamsLogoDto(val href: String? = null)

object EspnTeamsParser {
    fun parse(json: String, league: League): List<TeamInfo> =
        SofaJson.decodeFromString(TeamsDto.serializer(), json).sports.flatMap { it.leagues }.flatMap { it.teams }
            .map { it.team }
            .filter { it.isActive && it.abbreviation.isNotBlank() }
            .map { t -> TeamInfo(league, t.abbreviation, t.displayName, t.shortDisplayName.ifEmpty { t.displayName }, t.logos.firstOrNull()?.href) }
            .distinctBy { it.abbr }
            .sortedBy { it.name }
}

/**
 * Who can be picked as a favorite. NFL teams from the built-in list (works offline, grouped by
 * division); other leagues' teams from ESPN. Fighters come from UFC cards over the last few months
 * and the next few (or the boxing fights we know of); drivers from the current race's entry list.
 * Cached for the session.
 */
@Singleton
class EspnTeamsProvider @Inject constructor(
    private val http: HttpClient,
    private val bases: ApiBases,
    private val scores: ScoresProvider,
) : TeamsProvider {
    private val cache = mutableMapOf<League, List<TeamInfo>>()
    private val lock = Mutex()

    override suspend fun teams(league: League): List<TeamInfo> {
        if (league == League.NFL) return NflTeams.all.map { TeamInfo(League.NFL, it.abbr, "${it.city} ${it.name}", it.name, it.logoUrl, it.division) }
        lock.withLock { cache[league] }?.let { return it }
        val list = when (league.format) {
            EventFormat.TEAMS -> espnTeams(league)
            EventFormat.FIGHT -> fighters(league)
            EventFormat.RACE -> athletes(scores.scoreboard(league).races.flatMap { it.entrants }.map { Triple(it.name, it.shortName, it.flagUrl) }, league)
        }
        lock.withLock { cache[league] = list }
        return list
    }

    private suspend fun fighters(league: League): List<TeamInfo> {
        val board = if (league == League.UFC) {
            val day = DateTimeFormatter.BASIC_ISO_DATE
            val today = LocalDate.now()
            val body = http.get("${bases.espnBase(league)}/scoreboard") {
                parameter("dates", "${today.minusDays(FIGHTERS_BACK_DAYS).format(day)}-${today.plusDays(FIGHTERS_AHEAD_DAYS).format(day)}")
            }.bodyAsText()
            withContext(Dispatchers.Default) { EspnParser.parseScoreboard(body, league) }
        } else {
            scores.scoreboard(league)
        }
        return athletes(board.games.flatMap { it.teams }.map { Triple(it.name, it.abbr, it.logoUrl) }, league)
    }

    /** (full name, short name, flag or photo) → picker entries, one per name, by last name. */
    private fun athletes(people: List<Triple<String, String, String?>>, league: League): List<TeamInfo> =
        people.filter { it.first.isNotBlank() && it.first != "TBA" }
            .distinctBy { it.first.lowercase() }
            .map { (name, short, logo) -> TeamInfo(league, name, name, short, logo, sortName = plain(name.substringAfterLast(' '))) }
            .sortedBy { it.sortName.lowercase() }

    /** "Álvarez" → "Alvarez", so accented names sort and file under their letter. */
    private fun plain(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private suspend fun espnTeams(league: League): List<TeamInfo> {
        if (league.espnPath == null) return emptyList()
        val res = http.get("${bases.espnBase(league)}/teams") { parameter("limit", 1000) }
        if (!res.status.isSuccess()) error("ESPN teams HTTP ${res.status.value}")
        val body = res.bodyAsText()
        return withContext(Dispatchers.Default) { EspnTeamsParser.parse(body, league) }
    }

    private companion object {
        const val FIGHTERS_BACK_DAYS = 150L
        const val FIGHTERS_AHEAD_DAYS = 90L
    }
}
