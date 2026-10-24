package com.sofasports.tv.domain

/** How long to wait before the next scoreboard fetch. */
object PollPolicy {
    /**
     * - any game live, or last fetch failed → live interval (30 s by default)
     * - next kickoff sooner than the idle interval → wake at kickoff (but never faster than live)
     * - otherwise → idle interval (5 min by default)
     */
    fun nextDelayMs(games: List<Game>?, nowMs: Long, liveSec: Int, idleSec: Int, lastFetchFailed: Boolean = false): Long {
        val live = liveSec * 1000L
        val idle = idleSec * 1000L
        if (lastFetchFailed || games == null) return live
        if (games.any { it.state == GameState.LIVE }) return live
        val nextKick = games.filter { it.state == GameState.PRE && it.startEpochMs > 0 }.minOfOrNull { it.startEpochMs }
            ?: return idle
        val untilKick = nextKick - nowMs
        // Kickoff time passed but ESPN still says "pre": poll fast so we catch the switch to live.
        if (untilKick <= 0) return live
        return untilKick.coerceIn(live, idle)
    }
}
