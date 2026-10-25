package com.sofasports.tv.data.espnff

import com.sofasports.tv.data.fixture.FixtureFantasy
import com.sofasports.tv.data.fixture.FixtureReplay
import com.sofasports.tv.data.fixture.FixtureSource
import com.sofasports.tv.data.provider.ApiBases
import com.sofasports.tv.data.provider.DataModeSource
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyStarter
import com.sofasports.tv.domain.FantasyTeam
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class EspnCookies(val espnS2: String, val swid: String)

sealed interface EspnLookup {
    data class Found(val league: EspnFfLeague, val suggestedTeamId: Int?) : EspnLookup
    /** League exists but isn't public: needs espn_s2 + SWID. */
    data object Private : EspnLookup
    /** Cookies were given but ESPN still refused. */
    data object CookiesRejected : EspnLookup
    data object NotFound : EspnLookup
    data class Failed(val message: String) : EspnLookup
}

interface EspnFantasyProvider {
    suspend fun lookup(leagueId: String, cookies: EspnCookies?): EspnLookup
    suspend fun matchup(link: EspnLeagueLink): FantasyMatchup?
}

@Singleton
class EspnFantasyApi @Inject constructor(
    private val http: HttpClient,
    private val bases: ApiBases,
) : EspnFantasyProvider {

    private val leagueCache = mutableMapOf<String, Pair<Long, EspnFfLeague>>()

    private fun url(season: Int, leagueId: String) = "${bases.espnFantasyBase}/seasons/$season/segments/0/leagues/$leagueId"

    private fun HttpRequestBuilder.auth(cookies: EspnCookies?) {
        if (cookies != null) {
            val swid = cookies.swid.trim().let { if (it.startsWith("{")) it else "{$it}" }
            header("Cookie", "espn_s2=${cookies.espnS2.trim()}; SWID=$swid")
        }
    }

    override suspend fun lookup(leagueId: String, cookies: EspnCookies?): EspnLookup = runCatching {
        val id = leagueId.trim()
        if (id.isEmpty() || !id.all { it.isDigit() }) return EspnLookup.NotFound
        val season = EspnFantasyParser.seasonFor(LocalDate.now())
        val res = http.get("${url(season, id)}?view=mTeam&view=mSettings&view=mStatus") { auth(cookies) }
        when {
            res.status == HttpStatusCode.Unauthorized || res.status == HttpStatusCode.Forbidden ->
                if (cookies == null) EspnLookup.Private else EspnLookup.CookiesRejected
            res.status == HttpStatusCode.NotFound -> EspnLookup.NotFound
            !res.status.isSuccess() -> EspnLookup.Failed("ESPN said HTTP ${res.status.value}")
            else -> {
                val league = EspnFantasyParser.parseLeague(res.bodyAsText())
                EspnLookup.Found(league, EspnFantasyParser.teamForSwid(league, cookies?.swid)?.id)
            }
        }
    }.getOrElse { EspnLookup.Failed(it.message ?: "network error") }

    override suspend fun matchup(link: EspnLeagueLink): FantasyMatchup? {
        val cookies = link.espnS2?.let { EspnCookies(it, link.swid.orEmpty()) }
        val league = cachedLeague(link, cookies) ?: error("Couldn't load ESPN league ${link.leagueId}")
        val period = league.currentMatchupPeriod
        val res = http.get("${url(link.season, link.leagueId)}?view=mMatchupScore&view=mBoxscore&scoringPeriodId=$period") {
            auth(cookies)
            // Only this week's matchups, or ESPN returns the whole season's rosters.
            header("x-fantasy-filter", """{"schedule":{"filterMatchupPeriodIds":{"value":[$period]}}}""")
        }
        if (!res.status.isSuccess()) error("ESPN said HTTP ${res.status.value}")
        val m = EspnFantasyParser.buildMatchup(league, res.bodyAsText(), link.teamId, link.key) ?: return null
        return withDefaultProjections(m, link.season, period)
    }

    /**
     * Fills in projections the league's box score left out, from ESPN's public default (PPR)
     * projections. A league with its own projections is left alone.
     */
    private suspend fun withDefaultProjections(m: FantasyMatchup, season: Int, period: Int): FantasyMatchup {
        val all = m.me.starters + m.opponent?.starters.orEmpty()
        val missing = all.filter { it.projected == null }.mapNotNull { it.player.id.toLongOrNull()?.takeIf { id -> id > 0 } }
        if (missing.isEmpty()) return m
        val defaults = runCatching {
            val res = http.get("${bases.espnFantasyBase}/seasons/$season/segments/0/leaguedefaults/3?scoringPeriodId=$period&view=kona_player_info") {
                header("x-fantasy-filter", """{"players":{"filterIds":{"value":[${missing.joinToString(",")}]}}}""")
            }
            if (res.status.isSuccess()) EspnFantasyParser.parseDefaultProjections(res.bodyAsText(), season, period) else emptyMap()
        }.getOrDefault(emptyMap())
        if (defaults.isEmpty()) return m
        fun FantasyTeam.fill() = copy(starters = starters.map { s -> if (s.projected != null) s else s.copy(projected = defaults[s.player.id]) })
        return m.copy(me = m.me.fill(), opponent = m.opponent?.fill())
    }

    /** Team names and the current week change rarely; refetch every 30 min. */
    private suspend fun cachedLeague(link: EspnLeagueLink, cookies: EspnCookies?): EspnFfLeague? {
        val now = System.currentTimeMillis()
        leagueCache[link.leagueId]?.takeIf { now - it.first < 30 * 60_000L }?.let { return it.second }
        val res = http.get("${url(link.season, link.leagueId)}?view=mTeam&view=mSettings&view=mStatus") { auth(cookies) }
        if (!res.status.isSuccess()) return null
        return EspnFantasyParser.parseLeague(res.bodyAsText()).also { leagueCache[link.leagueId] = now to it }
    }
}

/** Fixture mode: any league id finds the scrubbed fixture league; week 6 replays with growing points. */
@Singleton
class FixtureEspnFantasyProvider @Inject constructor(
    private val source: FixtureSource,
    private val replay: FixtureReplay,
) : EspnFantasyProvider {
    private fun league() = source.read("espn_fantasy/league_teams.json")?.let(EspnFantasyParser::parseLeague)?.copy(currentMatchupPeriod = 6)

    override suspend fun lookup(leagueId: String, cookies: EspnCookies?): EspnLookup {
        val l = league() ?: return EspnLookup.Failed("fixture missing")
        return EspnLookup.Found(l.copy(id = leagueId.trim().ifEmpty { l.id }), null)
    }

    override suspend fun matchup(link: EspnLeagueLink): FantasyMatchup? {
        val l = league() ?: return null
        val box = source.read("espn_fantasy/league_box_w6.json") ?: return null
        val m = EspnFantasyParser.buildMatchup(l, box, link.teamId, link.key) ?: return null
        return FixtureFantasy.replayed(m, replay.progress()).copy(leagueName = link.leagueName)
    }
}

@Singleton
class SwitchingEspnFantasyProvider @Inject constructor(
    private val mode: DataModeSource,
    private val live: EspnFantasyApi,
    private val fixture: FixtureEspnFantasyProvider,
) : EspnFantasyProvider {
    private suspend fun pick(): EspnFantasyProvider = if (mode.useFixtures()) fixture else live
    override suspend fun lookup(leagueId: String, cookies: EspnCookies?) = pick().lookup(leagueId, cookies)
    override suspend fun matchup(link: EspnLeagueLink) = pick().matchup(link)
}
