package com.sofasports.tv.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyGesturesTest {
    private var now = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val seen = mutableListOf<Gesture>()
    private val g = KeyGestures(
        schedule = { d, a ->
            val t = (now + d) to a
            timers += t
            KeyGestures.Cancel { timers.remove(t) }
        },
        onGesture = { _, gesture -> seen += gesture },
    )

    private fun advance(ms: Long) {
        now += ms
        timers.filter { it.first <= now }.forEach { timers.remove(it); it.second() }
    }

    private fun press(code: Int = 42, holdMs: Long = 80) {
        g.onKey(code, KeyGestures.DOWN, 0); advance(holdMs); g.onKey(code, KeyGestures.UP, 0)
    }

    @Test fun `single press fires after the double-press window`() {
        press(); assertEquals(emptyList<Gesture>(), seen)
        advance(KeyGestures.DOUBLE_MS); assertEquals(listOf(Gesture.SINGLE), seen)
    }

    @Test fun `two quick presses are a double`() {
        press(); advance(100); press(); advance(1_000)
        assertEquals(listOf(Gesture.DOUBLE), seen)
    }

    @Test fun `holding is a long press, with no single afterwards`() {
        g.onKey(42, KeyGestures.DOWN, 0)
        advance(KeyGestures.LONG_MS)
        g.onKey(42, KeyGestures.DOWN, 5) // key repeat
        g.onKey(42, KeyGestures.UP, 0)
        advance(1_000)
        assertEquals(listOf(Gesture.LONG), seen)
    }

    @Test fun `slow presses are two singles`() {
        press(); advance(500); press(); advance(500)
        assertEquals(listOf(Gesture.SINGLE, Gesture.SINGLE), seen)
    }
}
