package com.sofasports.tv.domain

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.espn.EspnParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BettingTest {
    private val w7 = EspnParser.parseScoreboard(Fixtures.read("espn/scoreboard_2026_w7_upcoming.json"))
    private val houJax = EspnParser.parseSummary(Fixtures.read("espn/summary_401872996_hou_jax.json")).game

    @Test fun `scoreboard DraftKings line parses with spread from home view`() {
        val g = w7.games.single { it.id == "401873011" } // PIT @ NO, "PIT -3"
        val o = g.odds!!
        assertEquals("DraftKings", o.provider)
        assertEquals("PIT -3", o.details)
        assertEquals(44.5, o.overUnder!!, 0.0)
        assertEquals(3.0, o.homeSpread!!, 0.0) // NO is home, getting 3
        assertEquals("-166", o.awayMoneyline)
        assertEquals("+140", o.homeMoneyline)
        assertEquals("DK", Betting.providerTag(o.provider))
        assertEquals("PIT -3 · O/U 44.5", Betting.summary(o))
    }

    @Test fun `summary pickcenter gives odds for a finished game`() {
        val o = houJax.odds!!
        assertEquals("JAX -2.5", o.details)
        assertEquals(-2.5, o.homeSpread!!, 0.0)
        assertEquals("+120", o.awayMoneyline)
    }

    @Test fun `HOU +2,5 covered a 34-31 win, JAX -2,5 did not`() {
        assertEquals(5.5, Betting.coverMargin(houJax, "HOU", 2.5)!!, 0.0)
        assertEquals(BetStatus.WON, Betting.status(Betting.coverMargin(houJax, "HOU", 2.5), houJax.state))
        assertEquals(BetStatus.LOST, Betting.status(Betting.coverMargin(houJax, "JAX", -2.5), houJax.state))
        assertEquals(2.5, Betting.spreadFor(houJax.odds!!, houJax, "HOU")!!, 0.0)
    }

    @Test fun `total and over-under`() {
        assertEquals(65, Betting.total(houJax))
        assertEquals(BetStatus.WON, Betting.status(65 - 42.5, GameState.FINAL))
    }

    @Test fun `push and pending`() {
        assertEquals(BetStatus.PUSH, Betting.status(0.0, GameState.FINAL))
        assertEquals(BetStatus.PUSHING, Betting.status(0.0, GameState.LIVE))
        assertEquals(BetStatus.PENDING, Betting.status(3.0, GameState.PRE))
        assertNull(Betting.coverMargin(w7.games.single { it.id == "401873011" }, "PIT", -3.0))
    }

    @Test fun `home spread text parsing`() {
        val g = w7.games.single { it.id == "401873011" }
        assertEquals(0.0, EspnParser.homeSpread("EVEN", g)!!, 0.0)
        assertNull(EspnParser.homeSpread("XYZ -3", g))
        assertEquals("+3.5", Betting.formatLine(3.5))
        assertEquals("-3", Betting.formatLine(-3.0))
    }
}
