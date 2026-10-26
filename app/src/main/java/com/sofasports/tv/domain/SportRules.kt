package com.sofasports.tv.domain

import com.sofasports.tv.data.config.CloseGameRule

/** Period names per sport, for play-by-play rows. */
object Periods {
    /** "Q4", "OT", "2OT", "H2", "P3", "9th", "2H". Empty for sports without periods. */
    fun label(league: League, period: Int): String {
        if (period <= 0) return ""
        val reg = league.regulationPeriods
        if (reg == 0) return "R$period"
        if (period > reg) {
            val ot = period - reg
            return when (league.sport) {
                Sport.BASEBALL -> ordinal(period)
                Sport.SOCCER -> if (ot <= 2) "ET" else "PEN"
                else -> if (ot == 1) "OT" else "${ot}OT"
            }
        }
        return when (league.sport) {
            Sport.FOOTBALL -> "Q$period"
            Sport.BASKETBALL -> if (reg == 2) "H$period" else "Q$period"
            Sport.HOCKEY -> "P$period"
            Sport.BASEBALL -> ordinal(period)
            Sport.SOCCER -> if (period == 1) "1H" else "2H"
            else -> "R$period"
        }
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}

/** Per-sport "close game" thresholds and scoring words. */
object SportRules {
    /**
     * When a game counts as close. Football uses the remote-config rule (one score late); the rest
     * use a one-possession-late rule of their own, counted from the end of regulation so halves,
     * quarters and innings all work.
     */
    fun closeRule(league: League, football: CloseGameRule): CloseGameRule = when (league.sport) {
        Sport.FOOTBALL -> football
        // Last quarter (last half in college) within two possessions.
        Sport.BASKETBALL -> CloseGameRule(maxPointDiff = 6, minPeriod = league.regulationPeriods)
        // Third period, one goal.
        Sport.HOCKEY -> CloseGameRule(maxPointDiff = 1, minPeriod = 3)
        // Eighth inning on, within two runs.
        Sport.BASEBALL -> CloseGameRule(maxPointDiff = 2, minPeriod = 8)
        // Second half, one goal.
        Sport.SOCCER -> CloseGameRule(maxPointDiff = 1, minPeriod = 2)
        // Fights and races have no score to be close in.
        else -> CloseGameRule(maxPointDiff = -1, minPeriod = Int.MAX_VALUE)
    }

    /** Points scored between two polls → "TD", "FG", "GOAL", "2 RUNS", "3PT"… for the ticker and overlay banner. */
    fun scoreLabel(league: League, delta: Int): String = when (league.sport) {
        Sport.FOOTBALL -> when (delta) {
            6, 7, 8 -> "TD"
            3 -> "FG"
            2 -> "2 PTS"
            1 -> "XP"
            else -> "+$delta"
        }
        Sport.SOCCER, Sport.HOCKEY -> if (delta == 1) "GOAL" else "$delta GOALS"
        Sport.BASEBALL -> if (delta == 1) "RUN" else "$delta RUNS"
        Sport.BASKETBALL -> if (delta == 3) "3PT" else "+$delta"
        else -> "+$delta"
    }

    /** Basketball scores change every few seconds; only football, soccer, hockey and baseball scores are news. */
    fun scoresAreNews(league: League): Boolean = league.sport in setOf(Sport.FOOTBALL, Sport.SOCCER, Sport.HOCKEY, Sport.BASEBALL)
}

/** A fight card: every bout of one event, main event first. */
data class FightCard(val eventId: String, val league: League, val name: String, val bouts: List<Game>) {
    val mainEvent: Game get() = bouts.first()
    val live: Game? get() = bouts.firstOrNull { it.state == GameState.LIVE }
    val startEpochMs: Long get() = bouts.minOf { it.startEpochMs }
    val state: GameState
        get() = when {
            bouts.any { it.state == GameState.LIVE } -> GameState.LIVE
            bouts.all { it.state == GameState.FINAL } -> GameState.FINAL
            // Prelims done, main card not started yet: still on.
            bouts.any { it.state == GameState.FINAL } && bouts.any { it.state == GameState.PRE } -> GameState.LIVE
            bouts.all { it.state == GameState.PRE } -> GameState.PRE
            else -> GameState.OTHER
        }
    val ref: GameRef get() = GameRef(league, eventId)
}

object FightCards {
    fun group(games: List<Game>): List<FightCard> =
        games.filter { it.league.format == EventFormat.FIGHT }
            .groupBy { it.league to (it.eventId ?: it.id) }
            .map { (k, bouts) ->
                val sorted = bouts.sortedByDescending { it.order }
                FightCard(k.second, k.first, sorted.first().eventName ?: "${k.first.label} card", sorted)
            }
            .sortedWith(compareBy<FightCard> { stateRank(it.state) }.thenBy { it.startEpochMs })

    private fun stateRank(s: GameState) = when (s) {
        GameState.LIVE -> 0
        GameState.PRE -> 1
        else -> 2
    }
}
