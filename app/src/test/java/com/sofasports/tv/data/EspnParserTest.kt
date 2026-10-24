package com.sofasports.tv.data

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.espn.EspnParser
import com.sofasports.tv.domain.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnParserTest {

    @Test fun `week 6 scoreboard parses every completed game`() {
        val sb = EspnParser.parseScoreboard(Fixtures.read("espn/scoreboard_2026_w6_final.json"))
        assertEquals(6, sb.week)
        assertEquals(2026, sb.season)
        assertEquals(14, sb.games.size)
        assertTrue(sb.games.all { it.state == GameState.FINAL })
        val houJax = sb.games.single { it.id == "401872996" }
        assertEquals("HOU", houJax.away.abbr)
        assertEquals("JAX", houJax.home.abbr)
        assertEquals(34, houJax.away.score)
        assertEquals(31, houJax.home.score)
        assertTrue(houJax.home.isHome)
        assertTrue(houJax.startEpochMs > 0)
        assertTrue(houJax.home.logoUrl!!.startsWith("https://"))
    }

    @Test fun `week 7 scoreboard has scheduled games with no score and a kickoff time`() {
        val sb = EspnParser.parseScoreboard(Fixtures.read("espn/scoreboard_2026_w7_upcoming.json"))
        assertEquals(7, sb.week)
        val final = sb.games.single { it.id == "401873010" }
        assertEquals(GameState.FINAL, final.state)
        val pre = sb.games.filter { it.state == GameState.PRE }
        assertEquals(13, pre.size)
        assertTrue(pre.all { it.home.score == null && it.away.score == null })
        val pitNo = sb.games.single { it.id == "401873011" }
        // "2026-10-25T13:30Z"
        assertEquals(1792935000000L, pitNo.startEpochMs)
        assertEquals("NFL Net", pitNo.broadcast)
        assertEquals("2-4", pitNo.home.record)
    }

    @Test fun `summary parses header, win probability, box score and plays`() {
        val s = EspnParser.parseSummary(Fixtures.read("espn/summary_401872996_hou_jax.json"))
        assertEquals("401872996", s.game.id)
        assertEquals(GameState.FINAL, s.game.state)
        assertEquals(34, s.game.away.score)
        assertEquals(31, s.game.home.score)
        assertEquals(187, s.winProbability.size)
        assertEquals(0.0, s.winProbability.last().homeWinPct, 0.0001)
        assertEquals("1st Downs", s.teamStats.first().label)
        assertEquals("23", s.teamStats.first().away) // HOU is the away side
        assertTrue(s.playerStats.any { it.teamAbbr == "HOU" && it.category.contains("Passing", ignoreCase = true) })
        val passing = s.playerStats.first { it.teamAbbr == "HOU" }
        assertEquals("C.J. Stroud", passing.rows.first().name)
        assertEquals(20, s.drives.size)
        assertEquals(186, s.drives.sumOf { it.plays.size })
        assertEquals("END GAME", s.playsNewestFirst.first().text)
        assertEquals(11, s.scoringPlays.size)
        assertEquals("Wembley Stadium", s.venue)
    }

    @Test fun `second summary fixture parses too`() {
        val s = EspnParser.parseSummary(Fixtures.read("espn/summary_401873006_lac_kc.json"))
        assertEquals("KC", s.game.home.abbr)
        assertEquals(22, s.game.home.score)
        assertEquals(6, s.game.away.score)
        assertTrue(s.winProbability.isNotEmpty())
    }

    @Test fun `unknown status maps to OTHER and bad dates to zero`() {
        assertEquals(GameState.OTHER, EspnParser.stateOf(null))
        assertEquals(0L, EspnParser.parseDate("not a date"))
        assertEquals(0L, EspnParser.parseDate(null))
    }

    @Test fun `empty scoreboard is fine`() {
        val sb = EspnParser.parseScoreboard("""{"events":[]}""")
        assertTrue(sb.games.isEmpty())
        assertNull(sb.week)
    }
}
