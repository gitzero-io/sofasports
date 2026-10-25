package com.sofasports.tv.overlay

import com.sofasports.tv.data.prefs.OverlayPage
import com.sofasports.tv.data.prefs.QuietHours
import com.sofasports.tv.domain.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPagesTest {
    private val all = OverlayPage.entries.toSet()

    @Test fun `pages without content are skipped`() {
        val s = OverlayState(matchup = T.matchup(10.0, 5.0))
        assertEquals(listOf(OverlayPage.MATCHUP, OverlayPage.MINI), OverlayPages.available(s, all, T.NOW))
        assertEquals(emptyList<OverlayPage>(), OverlayPages.available(OverlayState(), all, T.NOW))
    }

    @Test fun `disabled pages are skipped but still have content for pops`() {
        val s = OverlayState(matchup = T.matchup(10.0, 5.0), picks = listOf(T.pick("p", com.sofasports.tv.domain.BetStatus.PENDING)))
        assertEquals(listOf(OverlayPage.MATCHUP), OverlayPages.available(s, setOf(OverlayPage.MATCHUP), T.NOW))
        assertTrue(OverlayPage.PICKS in OverlayPages.withContent(s, T.NOW))
    }

    @Test fun `step wraps both ways and rotation skips minimized`() {
        val pages = listOf(OverlayPage.MATCHUP, OverlayPage.MY_GAME, OverlayPage.MINI)
        assertEquals(OverlayPage.MY_GAME, OverlayPages.step(OverlayPage.MATCHUP, pages))
        assertEquals(OverlayPage.MATCHUP, OverlayPages.step(OverlayPage.MINI, pages))
        assertEquals(OverlayPage.MINI, OverlayPages.step(OverlayPage.MATCHUP, pages, forward = false))
        assertEquals(OverlayPage.MATCHUP, OverlayPages.step(OverlayPage.MY_GAME, pages, fullOnly = true))
        assertNull(OverlayPages.step(OverlayPage.MATCHUP, emptyList()))
    }

    @Test fun `resolve falls back to the first full page`() {
        val pages = listOf(OverlayPage.MY_GAME, OverlayPage.MINI)
        assertEquals(OverlayPage.MY_GAME, OverlayPages.resolve(OverlayPage.PICKS, pages))
        assertEquals(OverlayPage.MINI, OverlayPages.resolve(OverlayPage.PICKS, listOf(OverlayPage.MINI)))
    }

    @Test fun `around the league puts close live games first`() {
        val games = listOf(
            T.game("a", "DAL", "PHI", awayScore = 3, homeScore = 30, period = 4),
            T.game("b", "HOU", "JAX", awayScore = 21, homeScore = 24, period = 4),
            T.game("c", "KC", "LAC", GameState.PRE, start = T.NOW + 3_600_000),
            T.game("d", "NYG", "NYJ", GameState.PRE, start = T.NOW + 5 * 24 * 3_600_000L),
        )
        assertEquals(listOf("b", "a", "c"), OverlayPages.leagueGames(OverlayState(games = games), T.NOW).map { it.id })
    }

    @Test fun `playing now lists my starters in live games`() {
        val m = T.matchup(20.0, 10.0, listOf(T.starter("1", "A", "HOU", 4.0), T.starter("2", "B", "WAS", 9.0), T.starter("3", "C", "KC", 12.0)))
        val s = OverlayState(matchup = m, games = listOf(T.game("g", "HOU", "WSH")))
        assertEquals(listOf("B", "A"), OverlayPages.playingNow(s).map { it.player.name })
    }

    @Test fun `quiet hours wrap past midnight`() {
        assertTrue(QuietHours.LATE.contains(23)); assertTrue(QuietHours.LATE.contains(3)); assertFalse(QuietHours.LATE.contains(8))
        assertTrue(QuietHours.OVERNIGHT.contains(1)); assertFalse(QuietHours.OVERNIGHT.contains(0))
        assertFalse(QuietHours.OFF.contains(2))
    }
}
