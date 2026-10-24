package com.sofasports.tv.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageEventsTest {
    private val sent = mutableListOf<UsageEvent>()
    private val sink = object : UsageSink { override fun send(event: UsageEvent) { sent += event } }

    @Test fun `events flow when allowed`() {
        GatedUsageEvents(sink) { true }.track(UsageEvent.AppOpen)
        assertEquals(listOf<UsageEvent>(UsageEvent.AppOpen), sent)
    }

    @Test fun `opt-out drops everything`() {
        val e = GatedUsageEvents(sink) { false }
        e.track(UsageEvent.AppOpen); e.track(UsageEvent.OverlayToggled(true))
        assertTrue(sent.isEmpty())
    }

    @Test fun `opt-out is read on every event`() {
        var allowed = true
        val e = GatedUsageEvents(sink) { allowed }
        e.track(UsageEvent.AppOpen)
        allowed = false
        e.track(UsageEvent.GameOpened)
        assertEquals(1, sent.size)
    }

    @Test fun `a failing sink never crashes the app`() {
        val boom = object : UsageSink { override fun send(event: UsageEvent) = error("down") }
        GatedUsageEvents(boom) { true }.track(UsageEvent.AppOpen)
    }

    @Test fun `events before the opt-out is known are held, then sent or dropped`() {
        var allowed: Boolean? = null
        val e = GatedUsageEvents(sink) { allowed }
        e.track(UsageEvent.AppOpen)
        assertTrue(sent.isEmpty())
        allowed = true
        e.onDecisionKnown()
        assertEquals(listOf<UsageEvent>(UsageEvent.AppOpen), sent)

        sent.clear()
        allowed = null
        val e2 = GatedUsageEvents(sink) { allowed }
        e2.track(UsageEvent.AppOpen)
        allowed = false
        e2.onDecisionKnown()
        allowed = true
        e2.onDecisionKnown()
        assertTrue(sent.isEmpty())
    }

    @Test fun `league count is capped so it can't fingerprint`() =
        assertEquals("20", UsageEvent.LeagueCount(57).props["count"])
}
