package com.sofasports.tv.data.repo

import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.Scoreboard
import com.sofasports.tv.overlay.T
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoresStateTest {
    private val nfl = T.game("n", "HOU", "JAX")
    private val nba = T.game("b", "LAL", "UTAH").copy(league = League.NBA)
    private val boards = mapOf(
        League.NFL to LeagueBoard(Scoreboard(2026, 7, listOf(nfl))),
        League.NBA to LeagueBoard(Scoreboard(2026, null, listOf(nba), League.NBA)),
    )

    @Test fun `a league polled only for fantasy stays out of the followed games`() {
        // NFL unfollowed, but still polled for a linked Sleeper account.
        val s = ScoresState(listOf(League.NBA, League.NFL), boards, followed = listOf(League.NBA))
        assertEquals(listOf("b"), s.followedGames.map { it.id })
        assertEquals(listOf("b", "n"), s.games.map { it.id })
    }

    @Test fun `followed defaults to every polled league`() {
        val s = ScoresState(listOf(League.NFL, League.NBA), boards)
        assertEquals(listOf("n", "b"), s.followedGames.map { it.id })
    }
}
