package com.sofasports.tv.domain

/** How long to wait before the next scoreboard fetch. */
object PollPolicy {
    private const val STALE_START_MS = 3 * 3_600_000L
    /**
     * - any game live, or last fetch failed → live interval (30 s by default)
     * - next kickoff sooner than the idle interval → wake at kickoff (but never faster than live)
     * - otherwise → idle interval (5 min by default)
     */
    fun nextDelayMs(games: List<Game>?, nowMs: Long, liveSec: Int, idleSec: Int, lastFetchFailed: Boolean = false): Long =
        delayFor(games?.map { it.state to it.startEpochMs }, nowMs, liveSec, idleSec, lastFetchFailed)

    /** Same rule for a whole scoreboard, races included. */
    fun nextDelayMs(board: Scoreboard?, nowMs: Long, liveSec: Int, idleSec: Int, lastFetchFailed: Boolean = false): Long =
        delayFor(
            board?.let { b -> b.games.map { it.state to it.startEpochMs } + b.races.map { it.state to it.startEpochMs } },
            nowMs, liveSec, idleSec, lastFetchFailed,
        )

    /** [events] are (state, start time) pairs. */
    private fun delayFor(events: List<Pair<GameState, Long>>?, nowMs: Long, liveSec: Int, idleSec: Int, lastFetchFailed: Boolean): Long {
        val live = liveSec * 1000L
        val idle = idleSec * 1000L
        if (lastFetchFailed || events == null) return live
        if (events.any { it.first == GameState.LIVE }) return live
        // A start long past that never went live (a postponed game, a fight card with no result yet)
        // shouldn't keep polling at the live rate for the rest of the day.
        val nextKick = events.filter { it.first == GameState.PRE && it.second > 0 && nowMs - it.second < STALE_START_MS }.minOfOrNull { it.second }
            ?: return idle
        val untilKick = nextKick - nowMs
        // Kickoff time passed but ESPN still says "pre": poll fast so we catch the switch to live.
        if (untilKick <= 0) return live
        return untilKick.coerceIn(live, idle)
    }
}
