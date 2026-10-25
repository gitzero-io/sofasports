package com.sofasports.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TicketEvaluatorTest {
    private fun leg(status: BetStatus, ticket: String? = "t") =
        PickProgress(TrackedPick("${status}-${ticket}-${Math.random()}", PickBook.DRAFTKINGS, PickType.MONEYLINE, "HOU", ticketId = ticket), null, null, status)
    private fun status(vararg s: BetStatus) = TicketEvaluator.status(s.map { leg(it) })

    @Test fun `one lost leg loses the parlay even while others are live`() =
        assertEquals(BetStatus.LOST, status(BetStatus.WON, BetStatus.LOST, BetStatus.WINNING))

    @Test fun `all legs won wins, pushes drop out, all pushes push`() {
        assertEquals(BetStatus.WON, status(BetStatus.WON, BetStatus.WON))
        assertEquals(BetStatus.WON, status(BetStatus.WON, BetStatus.PUSH))
        assertEquals(BetStatus.PUSH, status(BetStatus.PUSH, BetStatus.PUSH))
    }

    @Test fun `open parlays are losing, pending or alive`() {
        assertEquals(BetStatus.LOSING, status(BetStatus.WON, BetStatus.LOSING, BetStatus.PENDING))
        assertEquals(BetStatus.PENDING, status(BetStatus.WINNING, BetStatus.PENDING))
        assertEquals(BetStatus.WINNING, status(BetStatus.WON, BetStatus.WINNING, BetStatus.PUSHING))
        assertEquals(BetStatus.PENDING, status())
    }

    @Test fun `legs are grouped under their ticket`() {
        val t = BetTicket("t", PickBook.FANDUEL)
        val grouped = TicketEvaluator.group(listOf(t), listOf(leg(BetStatus.WON), leg(BetStatus.WINNING), leg(BetStatus.LOST, ticket = null)))
        assertEquals(2, grouped.single().legs.size)
        assertEquals(1, grouped.single().legsWon)
        assertEquals(BetStatus.WINNING, grouped.single().status)
    }
}
