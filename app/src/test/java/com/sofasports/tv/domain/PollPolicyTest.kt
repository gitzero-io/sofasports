package com.sofasports.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PollPolicyTest {
    private val now = 10_000_000L
    private fun g(state: GameState, start: Long = 0L) = Game(
        "x", start, state,
        TeamSide("1", "A", "A", "A", null, null, null, true),
        TeamSide("2", "B", "B", "B", null, null, null, false),
        0, "", "", null,
    )

    @Test fun `live game polls every 30s`() =
        assertEquals(30_000L, PollPolicy.nextDelayMs(listOf(g(GameState.FINAL), g(GameState.LIVE)), now, 30, 300))

    @Test fun `nothing live and nothing soon polls idle`() =
        assertEquals(300_000L, PollPolicy.nextDelayMs(listOf(g(GameState.FINAL)), now, 30, 300))

    @Test fun `wakes at kickoff`() =
        assertEquals(120_000L, PollPolicy.nextDelayMs(listOf(g(GameState.PRE, now + 120_000)), now, 30, 300))

    @Test fun `never faster than live interval before kickoff`() =
        assertEquals(30_000L, PollPolicy.nextDelayMs(listOf(g(GameState.PRE, now + 5_000)), now, 30, 300))

    @Test fun `past kickoff still pre polls fast`() =
        assertEquals(30_000L, PollPolicy.nextDelayMs(listOf(g(GameState.PRE, now - 60_000)), now, 30, 300))

    @Test fun `errors retry at live cadence`() {
        assertEquals(30_000L, PollPolicy.nextDelayMs(null, now, 30, 300))
        assertEquals(30_000L, PollPolicy.nextDelayMs(listOf(g(GameState.FINAL)), now, 30, 300, lastFetchFailed = true))
    }
}
