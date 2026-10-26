package com.sofasports.tv.data.provider

import android.content.Context
import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.data.sleeper.SleeperParser
import com.sofasports.tv.data.sleeper.SleeperPlayerDto
import com.sofasports.tv.domain.FantasyLeague
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyPlayer
import com.sofasports.tv.domain.FantasyUser
import com.sofasports.tv.domain.League
import java.time.LocalDate
import java.time.ZoneId
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleeperFantasyProvider @Inject constructor(
    private val http: HttpClient,
    private val bases: ApiBases,
    private val players: PlayerDirectory,
) : FantasyProvider {

    override suspend fun findUser(username: String): FantasyUser? {
        val res = http.get("${bases.sleeperBase}/user/${username.trim().encodeURLPathPart()}")
        if (!res.status.isSuccess()) return null
        return SleeperParser.parseUser(res.bodyAsText())
    }

    /** NFL leagues, then NBA ones (experimental). An NBA failure never hides the NFL leagues. */
    override suspend fun leagues(userId: String): List<FantasyLeague> = coroutineScope {
        val nfl = async { leagues(userId, "nfl") }
        val nba = async { runCatching { leagues(userId, "nba") }.getOrDefault(emptyList()) }
        nfl.await() + nba.await()
    }

    private suspend fun leagues(userId: String, sport: String): List<FantasyLeague> {
        val state = SleeperParser.parseState(http.get("${bases.sleeperBase}/state/$sport").bodyAsText())
        val season = state.season.ifBlank { null } ?: return emptyList()
        return SleeperParser.parseLeagues(http.get("${bases.sleeperBase}/user/$userId/leagues/$sport/$season").bodyAsText())
    }

    override suspend fun currentMatchup(leagueId: String, userId: String): FantasyMatchup? = coroutineScope {
        val base = bases.sleeperBase
        val league = async { SleeperParser.parseLeagueDto(http.get("$base/league/$leagueId").bodyAsText()) }
        val users = async { SleeperParser.parseLeagueUsers(http.get("$base/league/$leagueId/users").bodyAsText()) }
        val rosters = async { SleeperParser.parseRosters(http.get("$base/league/$leagueId/rosters").bodyAsText()) }
        val l = league.await()
        val sport = SleeperParser.toLeague(l).sport
        val st = SleeperParser.parseState(http.get("$base/state/${sport.id}").bodyAsText())
        val week = (st.displayWeek ?: st.week).coerceAtLeast(1)
        val matchups = async { SleeperParser.parseMatchups(http.get("$base/league/$leagueId/matchups/$week").bodyAsText()) }
        // Projections are nice to have: a failure just means no projected totals.
        val proj = if (sport == League.NFL) runCatching { projections(st.season, week, l.scoringSettings?.get("rec") ?: 1.0) }.getOrDefault(emptyMap()) else emptyMap()
        val weekProj = if (sport == League.NBA) runCatching { nbaProjections(st.season, week, l.scoringSettings.orEmpty()) }.getOrDefault(emptyMap()) else emptyMap()
        SleeperParser.buildMatchup(
            SleeperParser.toLeague(l), week, userId,
            users.await(), rosters.await(), matchups.await(), players.get(sport),
            rosterPositions = l.rosterPositions.orEmpty(), projections = proj, weekProjections = weekProj,
        )
    }

    private val nbaCache = mutableMapOf<String, Pair<Long, String>>()

    /** Every NBA game's projected stats for the week (one request, kept 15 minutes), scored for this league. */
    private suspend fun nbaProjections(season: String, week: Int, scoring: Map<String, Double>): Map<String, SleeperParser.WeekProjection> {
        val key = "$season-$week"
        val now = System.currentTimeMillis()
        val body = nbaCache[key]?.takeIf { now - it.first < 15 * 60_000L }?.second ?: run {
            val root = bases.sleeperBase.removeSuffix("/").removeSuffix("/v1")
            val res = http.get("$root/projections/nba/$season/$week?season_type=regular")
            if (!res.status.isSuccess()) return emptyMap()
            res.bodyAsText().also { nbaCache[key] = now to it }
        }
        // Sleeper dates games by US Eastern calendar day.
        val today = LocalDate.now(ZoneId.of("America/New_York")).toString()
        return withContext(Dispatchers.Default) { SleeperParser.parseGameProjections(body, scoring, today) }
    }

    private val projectionCache = mutableMapOf<String, Pair<Long, Map<String, Double>>>()

    /** The whole week's projections (~3,000 players, one request), kept 15 minutes per scoring type. */
    private suspend fun projections(season: String, week: Int, rec: Double): Map<String, Double> {
        val key = "$season-$week-$rec"
        val now = System.currentTimeMillis()
        projectionCache[key]?.takeIf { now - it.first < 15 * 60_000L }?.let { return it.second }
        val root = bases.sleeperBase.removeSuffix("/").removeSuffix("/v1")
        val positions = listOf("QB", "RB", "WR", "TE", "K", "DEF").joinToString("&") { "position%5B%5D=$it" }
        val res = http.get("$root/projections/nfl/$season/$week?season_type=regular&$positions")
        if (!res.status.isSuccess()) return emptyMap()
        return SleeperParser.parseProjections(res.bodyAsText(), rec).also { projectionCache[key] = now to it }
    }
}

/** Player id → name/position/team, per sport. */
interface PlayerDirectory {
    suspend fun get(sport: League = League.NFL): Map<String, FantasyPlayer>
}

/**
 * Sleeper asks clients to fetch the 14 MB /players/nfl dump (2.5 MB for NBA) at most once a day.
 * We stream it to disk, trim it to the four fields we use, and keep that small copy, one per sport.
 */
@Singleton
class SleeperPlayerCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpClient,
    private val bases: ApiBases,
) : PlayerDirectory {
    private val mutex = Mutex()
    private val memory = mutableMapOf<League, Map<String, FantasyPlayer>>()
    private val lastAttemptMs = mutableMapOf<League, Long>()
    // v2 adds injury status; the new name makes old caches refresh. NFL keeps its old file name.
    private fun file(sport: League) = File(context.filesDir, if (sport == League.NFL) "sleeper_players_trimmed_v2.json" else "sleeper_players_${sport.id}_v2.json")

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun get(sport: League): Map<String, FantasyPlayer> = mutex.withLock {
        val file = file(sport)
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < ONE_DAY_MS
        val now = System.currentTimeMillis()
        val mayDownload = !fresh && now - (lastAttemptMs[sport] ?: 0L) > RETRY_MS
        memory[sport]?.takeIf { fresh || !mayDownload }?.let { return it }
        withContext(Dispatchers.IO) {
            if (mayDownload) {
                lastAttemptMs[sport] = now
                runCatching { download(sport) }
            }
            // Even a stale cache beats "Player 4046".
            if (file.exists()) {
                runCatching { SleeperParser.parsePlayers(file.readText(), sport) }.getOrNull()
            } else null
        }.orEmpty().also { memory[sport] = it }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun download(sport: League) {
        val file = file(sport)
        val raw = File(context.cacheDir, "sleeper_players_raw.json")
        http.prepareGet("${bases.sleeperBase}/players/${sport.id}").execute { res ->
            if (!res.status.isSuccess()) return@execute
            res.bodyAsChannel().toInputStream().use { input -> raw.outputStream().use { input.copyTo(it) } }
        }
        if (!raw.exists()) return
        val serializer = MapSerializer(String.serializer(), SleeperPlayerDto.serializer())
        val all = raw.inputStream().use { SofaJson.decodeFromStream(serializer, it) }
        val trimmed = all.mapValues { (_, p) ->
            SleeperPlayerDto(
                fullName = p.fullName ?: listOfNotNull(p.firstName, p.lastName).joinToString(" "),
                position = p.position, team = p.team, injuryStatus = p.injuryStatus,
            )
        }
        val tmp = File(context.filesDir, "sleeper_players_trimmed.tmp")
        tmp.writeText(SofaJson.encodeToString(serializer, trimmed))
        tmp.renameTo(file)
        raw.delete()
    }

    private companion object {
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        const val RETRY_MS = 60 * 60 * 1000L
    }
}
