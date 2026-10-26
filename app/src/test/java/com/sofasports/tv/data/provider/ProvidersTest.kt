package com.sofasports.tv.data.provider

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.sleeper.SleeperParser
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.League
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProvidersTest {

    private val requested = mutableListOf<String>()

    /** Serves fixtures by URL path, the same files the debug fixture mode uses. */
    private val client = HttpClient(MockEngine { req ->
        val path = req.url.encodedPath
        requested += req.url.toString()
        val fixture = when {
            path.endsWith("/basketball/nba/scoreboard") -> "espn/nba/scoreboard.json"
            path.endsWith("/football/college-football/scoreboard") -> "espn/cfb/scoreboard.json"
            path.endsWith("/racing/f1/scoreboard") -> "espn/f1/scoreboard.json"
            path.endsWith("/basketball/nba/summary") -> "espn/nba/summary_401909861_lal_utah.json"
            path.endsWith("/basketball/nba/teams") -> null
            path.endsWith("eventsnextleague.php") || path.endsWith("eventspastleague.php") -> "espn/boxing/scoreboard.json"
            path.endsWith("/scoreboard") -> "espn/scoreboard_2026_w6_final.json"
            path.endsWith("/summary") -> "espn/summary_401872996_hou_jax.json"
            path.endsWith("/state/nfl") -> "sleeper/state_nfl.json"
            path.contains("/user/nosuchuser") -> null
            path.contains("/leagues/nfl/") -> null
            path.endsWith("/users") -> "sleeper/league_users.json"
            path.endsWith("/rosters") -> "sleeper/league_rosters.json"
            path.contains("/matchups/") -> "sleeper/league_matchups_w1.json"
            path.contains("/league/") -> "sleeper/league.json"
            else -> null
        }
        when {
            path.contains("/leagues/nfl/") -> respond("[" + Fixtures.read("sleeper/league.json") + "]")
            fixture != null -> respond(Fixtures.read(fixture))
            path.contains("/user/") -> respond("null", HttpStatusCode.OK)
            else -> respond("", HttpStatusCode.NotFound)
        }
    })

    private val players = object : PlayerDirectory {
        override suspend fun get() = SleeperParser.parsePlayers(Fixtures.read("sleeper/players_nfl_subset.json"))
    }

    @Test fun `espn scores provider hits scoreboard and parses`() = runTest {
        val sb = EspnScoresProvider(client, ApiBases.Default).scoreboard(League.NFL)
        assertEquals(14, sb.games.size)
        assertEquals("${Endpoints.ESPN_NFL_BASE}/scoreboard", requested.single())
    }

    @Test fun `espn stats provider passes event id`() = runTest {
        val s = EspnGameStatsProvider(client, ApiBases.Default).summary(League.NFL, "401872996")
        assertEquals("401872996", s.game.id)
        assertEquals("event=401872996", requested.single().substringAfter('?'))
    }

    @Test fun `other leagues use their own espn path`() = runTest {
        val sb = EspnScoresProvider(client, ApiBases.Default).scoreboard(League.NBA)
        assertEquals("${Endpoints.ESPN_SPORTS_BASE}/basketball/nba/scoreboard", requested.single())
        assertEquals(League.NBA, sb.league)
        assertEquals(true, sb.games.isNotEmpty() && sb.games.all { it.league == League.NBA })
        val s = EspnGameStatsProvider(client, ApiBases.Default).summary(League.NBA, "401909861")
        assertEquals(League.NBA, s.game.league)
    }

    @Test fun `college asks for the whole division`() = runTest {
        EspnScoresProvider(client, ApiBases.Default).scoreboard(League.CFB)
        assertEquals("groups=80&limit=300", requested.single().substringAfter('?'))
    }

    @Test fun `racing comes back as races`() = runTest {
        val sb = EspnScoresProvider(client, ApiBases.Default).scoreboard(League.F1)
        assertEquals(true, sb.games.isEmpty())
        assertEquals(1, sb.races.size)
    }

    @Test fun `boxing reads the next and last fights from TheSportsDB`() = runTest {
        val sb = EspnScoresProvider(client, ApiBases.Default).scoreboard(League.BOXING)
        assertEquals(2, requested.size)
        assertEquals(true, requested.all { it.startsWith(Endpoints.SPORTS_DB_BASE) && it.endsWith("id=4445") })
        // Both lists come from the same fixture here; duplicates collapse.
        assertEquals(2, sb.games.size)
        assertEquals(setOf(GameState.PRE, GameState.FINAL), sb.games.map { it.state }.toSet())
    }

    @Test fun `sleeper unknown user is null`() = runTest {
        assertNull(SleeperFantasyProvider(client, ApiBases.Default, players).findUser("nosuchuser"))
    }

    @Test fun `sleeper leagues use the current season from state`() = runTest {
        val leagues = SleeperFantasyProvider(client, ApiBases.Default, players).leagues("123")
        assertEquals(1, leagues.size)
        assertEquals(true, requested.any { it.endsWith("/user/123/leagues/nfl/2026") })
    }

    @Test fun `sleeper current matchup uses the state week`() = runTest {
        val m = SleeperFantasyProvider(client, ApiBases.Default, players)
            .currentMatchup("289646328504385536", "189140835533586432")
        assertEquals(7, m?.week)
        assertEquals(true, requested.any { it.endsWith("/matchups/7") })
        assertEquals("Kamara is the new DJ", m?.me?.teamName)
    }
}
