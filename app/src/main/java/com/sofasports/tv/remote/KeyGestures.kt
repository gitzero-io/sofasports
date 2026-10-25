package com.sofasports.tv.remote

enum class Gesture { SINGLE, DOUBLE, LONG }

/**
 * Turns raw key down/up events for one bound button into single / double / long presses.
 * Time and timers are injected so the rules can be unit tested.
 */
class KeyGestures(
    private val schedule: (delayMs: Long, action: () -> Unit) -> Cancel,
    private val onGesture: (keyCode: Int, gesture: Gesture) -> Unit,
) {
    fun interface Cancel { fun cancel() }

    private var code = 0
    private var pendingSingle: Cancel? = null
    private var pendingLong: Cancel? = null
    private var longFired = false
    private var secondPress = false

    /** [action] is KeyEvent.ACTION_DOWN (0) or ACTION_UP (1). Held-key repeats are ignored. */
    fun onKey(keyCode: Int, action: Int, repeatCount: Int) {
        if (keyCode != code) reset()
        code = keyCode
        when (action) {
            DOWN -> {
                if (repeatCount > 0) return
                if (pendingSingle != null) {
                    pendingSingle?.cancel(); pendingSingle = null
                    secondPress = true
                    return
                }
                longFired = false
                pendingLong = schedule(LONG_MS) { longFired = true; pendingLong = null; onGesture(keyCode, Gesture.LONG) }
            }
            UP -> {
                pendingLong?.cancel(); pendingLong = null
                when {
                    longFired -> longFired = false
                    secondPress -> { secondPress = false; onGesture(keyCode, Gesture.DOUBLE) }
                    else -> pendingSingle = schedule(DOUBLE_MS) { pendingSingle = null; onGesture(keyCode, Gesture.SINGLE) }
                }
            }
        }
    }

    fun reset() {
        pendingSingle?.cancel(); pendingSingle = null
        pendingLong?.cancel(); pendingLong = null
        longFired = false; secondPress = false
    }

    companion object {
        const val DOWN = 0
        const val UP = 1
        /** Second press within this window = double press. */
        const val DOUBLE_MS = 350L
        /** Held this long = long press. */
        const val LONG_MS = 600L
    }
}
