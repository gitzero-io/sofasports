package com.sofasports.tv.overlay

import com.sofasports.tv.data.prefs.MomentKind
import com.sofasports.tv.data.prefs.OverlayPage
import com.sofasports.tv.domain.BetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BigMomentsTest {
    private val all = MomentKind.entries.toSet()

    @Test fun `touchdown in my game is blue for my side, red for theirs`() {
        val before = OverlayState(game = T.game("g", "HOU", "JAX", awayScore = 14, homeScore = 10), favorites = listOf("JAX"))
        val jaxTd = before.copy(game = T.game("g", "HOU", "JAX", awayScore = 14, homeScore = 17))
        val m = BigMoments.detect(before, jaxTd, all).single()
        assertEquals("TD JAX · HOU 14–17 JAX", m.text)
        assertEquals(MomentTone.MINE, m.tone)
        assertEquals(OverlayPage.MY_GAME, m.page)
        val houFg = before.copy(game = T.game("g", "HOU", "JAX", awayScore = 17, homeScore = 10))
        assertEquals(MomentTone.OPPONENT, BigMoments.detect(before, houFg, all).single().tone)
    }

    @Test fun `a different game or first load never pops`() {
        val cur = OverlayState(game = T.game("g", "HOU", "JAX", awayScore = 7))
        assertTrue(BigMoments.detect(OverlayState(), cur, all).isEmpty())
        assertTrue(BigMoments.detect(OverlayState(game = T.game("x", "KC", "LAC")), cur, all).isEmpty())
    }

    @Test fun `my starter's big gain and a lead change`() {
        val before = OverlayState(matchup = T.matchup(50.0, 52.0, listOf(T.starter("1", "Nico Collins", "HOU", 6.0))))
        val after = OverlayState(matchup = T.matchup(57.0, 52.0, listOf(T.starter("1", "Nico Collins", "HOU", 13.0))))
        val kinds = BigMoments.detect(before, after, all).map { it.kind }
        assertEquals(listOf(MomentKind.PLAYER_SCORED, MomentKind.LEAD_CHANGE), kinds)
        assertEquals("You took the lead · 57.00–52.00", BigMoments.detect(before, after, setOf(MomentKind.LEAD_CHANGE)).single().text)
        // Small gains don't count; a switched league doesn't count.
        val small = OverlayState(matchup = T.matchup(51.0, 52.0, listOf(T.starter("1", "Nico Collins", "HOU", 7.0))))
        assertTrue(BigMoments.detect(before, small, all).isEmpty())
        val otherLeague = OverlayState(matchup = T.matchup(57.0, 52.0, listOf(T.starter("1", "Nico Collins", "HOU", 13.0)), league = "L2"))
        assertTrue(BigMoments.detect(before, otherLeague, all).isEmpty())
    }

    @Test fun `pick settling and a game turning close`() {
        val before = OverlayState(
            picks = listOf(T.pick("p", BetStatus.WINNING)),
            games = listOf(T.game("g", "HOU", "JAX", awayScore = 10, homeScore = 24, period = 4)),
        )
        val after = before.copy(
            picks = listOf(T.pick("p", BetStatus.WON)),
            games = listOf(T.game("g", "HOU", "JAX", awayScore = 17, homeScore = 24, period = 4)),
        )
        val moments = BigMoments.detect(before, after, all)
        assertEquals(listOf(MomentKind.PICK_RESULT, MomentKind.CLOSE_GAME), moments.map { it.kind })
        assertTrue(moments[0].text.startsWith("HIT"))
        assertEquals(OverlayPage.LEAGUE, moments[1].page)
    }

    @Test fun `only the chosen kinds are reported`() {
        val before = OverlayState(game = T.game("g", "HOU", "JAX"))
        val after = before.copy(game = T.game("g", "HOU", "JAX", awayScore = 7))
        assertTrue(BigMoments.detect(before, after, setOf(MomentKind.PICK_RESULT)).isEmpty())
    }
}
