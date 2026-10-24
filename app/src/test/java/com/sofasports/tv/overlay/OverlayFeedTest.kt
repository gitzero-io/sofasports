package com.sofasports.tv.overlay

import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.TeamSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayFeedTest {
    private val now = 1_000_000_000_000L
    private fun g(id: String, away: String, home: String, state: GameState, start: Long) = Game(
        id, start, state,
        TeamSide(home, home, home, home, null, 0, null, true),
        TeamSide(away, away, away, away, null, 0, null, false),
        if (state == GameState.LIVE) 2 else 0, "", "", null,
    )

    @Test fun `picks my live favorite game`() {
        val games = listOf(g("1", "KC", "SEA", GameState.PRE, now + 3_600_000), g("2", "DAL", "PHI", GameState.LIVE, now - 1))
        assertEquals("2", OverlayFeed.pickGame(games, listOf("PHI", "KC"), now)?.id)
    }

    @Test fun `upcoming favorite today is shown, next week is not`() {
        assertEquals("1", OverlayFeed.pickGame(listOf(g("1", "KC", "SEA", GameState.PRE, now + 3_600_000)), listOf("KC"), now)?.id)
        assertNull(OverlayFeed.pickGame(listOf(g("1", "KC", "SEA", GameState.PRE, now + 48 * 3_600_000L)), listOf("KC"), now))
    }

    @Test fun `no favorites playing shows nothing`() =
        assertNull(OverlayFeed.pickGame(listOf(g("1", "DAL", "PHI", GameState.LIVE, now)), listOf("KC"), now))
}
