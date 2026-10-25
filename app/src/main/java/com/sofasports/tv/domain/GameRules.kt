package com.sofasports.tv.domain

import com.sofasports.tv.data.config.CloseGameRule
import kotlin.math.abs

/** Close-game rule: live, in period >= minPeriod, and within maxPointDiff. Overtime is always close. */
object CloseGame {
    fun isClose(game: Game, rule: CloseGameRule = CloseGameRule()): Boolean {
        if (game.state != GameState.LIVE) return false
        val home = game.home.score ?: return false
        val away = game.away.score ?: return false
        if (game.period >= 5) return true
        return game.period >= rule.minPeriod && abs(home - away) <= rule.maxPointDiff
    }
}

/** Who is "me" (blue) in a game: my highest-priority favorite if playing, otherwise the home team. */
object Perspective {
    fun mySide(game: Game, favorites: List<String>): TeamSide =
        favorites.firstNotNullOfOrNull { game.side(it) } ?: game.home

    fun opponent(game: Game, favorites: List<String>): TeamSide {
        val me = mySide(game, favorites)
        return if (me.id == game.home.id) game.away else game.home
    }

    fun involvesFavorite(game: Game, favorites: List<String>) = favorites.any { game.involves(it) }
}

enum class ScoreSection(val title: String) { FAVORITES("Your teams"), LIVE("Live"), UPCOMING("Upcoming"), FINAL("Final") }

/** Favorites first, then live, upcoming, final. */
object GameSorting {
    fun sections(games: List<Game>, favorites: List<String>): List<Pair<ScoreSection, List<Game>>> {
        val (fav, rest) = games.partition { Perspective.involvesFavorite(it, favorites) }
        val favSorted = fav.sortedWith(
            compareBy<Game> { g -> favorites.indexOfFirst { g.involves(it) } }.thenBy { stateRank(it) }.thenBy { it.startEpochMs }
        )
        val live = rest.filter { it.state == GameState.LIVE }.sortedWith(compareBy<Game>({ -it.period }, { it.startEpochMs }))
        val upcoming = rest.filter { it.state == GameState.PRE }.sortedBy { it.startEpochMs }
        val final = rest.filter { it.state == GameState.FINAL || it.state == GameState.OTHER }.sortedBy { it.startEpochMs }
        return listOf(
            ScoreSection.FAVORITES to favSorted,
            ScoreSection.LIVE to live,
            ScoreSection.UPCOMING to upcoming,
            ScoreSection.FINAL to final,
        ).filter { it.second.isNotEmpty() }
    }

    fun sorted(games: List<Game>, favorites: List<String>): List<Game> = sections(games, favorites).flatMap { it.second }

    /**
     * Home's second row, beyond my teams: live games with close ones first or, when nothing is
     * live, the next kickoffs. [live] says which, for the row title.
     */
    data class AroundLeague(val live: Boolean, val games: List<Game>)

    fun aroundLeague(games: List<Game>, favorites: List<String>, rule: CloseGameRule, max: Int = 8): AroundLeague {
        val rest = games.filterNot { Perspective.involvesFavorite(it, favorites) }
        val live = rest.filter { it.state == GameState.LIVE }
            .sortedWith(compareByDescending<Game> { CloseGame.isClose(it, rule) }.thenBy { -it.period }.thenBy { it.startEpochMs })
        if (live.isNotEmpty()) return AroundLeague(true, live.take(max))
        return AroundLeague(false, rest.filter { it.state == GameState.PRE }.sortedBy { it.startEpochMs }.take(max))
    }

    private fun stateRank(g: Game) = when (g.state) {
        GameState.LIVE -> 0
        GameState.PRE -> 1
        else -> 2
    }
}

/** Kickoff countdown text. Pure so it's unit tested; the UI ticks [nowMs]. */
object Countdown {
    /**
     * - more than 24h away: null (caller shows the date instead)
     * - 1h..24h: "in 5h 07m"
     * - under 1h: "in 42:05"
     * - at/after kickoff but not live yet: "Kickoff soon"
     */
    fun format(nowMs: Long, kickoffMs: Long): String? {
        val diff = kickoffMs - nowMs
        if (diff <= 0) return "Kickoff soon"
        val totalSec = diff / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h >= 24 -> null
            h >= 1 -> "in ${h}h ${m.toString().padStart(2, '0')}m"
            else -> "in $m:${s.toString().padStart(2, '0')}"
        }
    }

    /** How often the UI needs to tick for this countdown. */
    fun tickMs(nowMs: Long, kickoffMs: Long): Long = if (kickoffMs - nowMs < 3_600_000) 1_000 else 30_000
}

/** Win probability shown as "<TEAM> 73%" from my side's perspective. */
data class WinProbDisplay(val teamAbbr: String, val percent: Int, val isMine: Boolean, val label: String)

object WinProbability {
    /**
     * Picks whose number to show and rounds it for TV:
     * - shows the leader's chance: mine if I'm favored (or level), otherwise the opponent's
     * - never shows 100% / 0% while the game is live (caps at 99%)
     * - final games show the winner at 100%, ties at 50%
     * - returns null with no data
     */
    fun display(game: Game, points: List<WinProbPoint>, favorites: List<String>): WinProbDisplay? {
        val me = Perspective.mySide(game, favorites)
        val opp = Perspective.opponent(game, favorites)
        if (game.state == GameState.FINAL) {
            val h = game.home.score ?: return null
            val a = game.away.score ?: return null
            if (h == a) return WinProbDisplay(me.abbr, 50, true, "Tie")
            val winner = if (h > a) game.home else game.away
            return WinProbDisplay(winner.abbr, 100, winner.id == me.id, "${winner.abbr} won")
        }
        val last = points.lastOrNull() ?: return null
        val homePct = last.homeWinPct.coerceIn(0.0, 1.0)
        val awayPct = (1.0 - homePct - last.tiePct).coerceIn(0.0, 1.0)
        val meIsHome = me.id == game.home.id
        val myPct = if (meIsHome) homePct else awayPct
        val oppPct = if (meIsHome) awayPct else homePct
        // Show the leader; an exact tie goes to my side.
        val mineLeads = myPct >= oppPct
        val shownTeam = if (mineLeads) me else opp
        val pct = Math.round((if (mineLeads) myPct else oppPct) * 100).toInt().coerceIn(1, 99)
        return WinProbDisplay(shownTeam.abbr, pct, mineLeads, "${shownTeam.abbr} $pct%")
    }

    /** My side's win chance per play, 0..1, for the chart line. */
    fun mySeries(game: Game, points: List<WinProbPoint>, favorites: List<String>): List<Double> {
        val meIsHome = Perspective.mySide(game, favorites).id == game.home.id
        return points.map { p -> if (meIsHome) p.homeWinPct else (1.0 - p.homeWinPct - p.tiePct).coerceIn(0.0, 1.0) }
    }
}

/** When a number on screen should pulse. */
object ScorePulse {
    /**
     * Pulse when a value changes after the first time we've seen it.
     * Game scores only pulse upward (a downward change is a stat correction, not news);
     * fantasy points pulse either way (negative plays are news too).
     */
    fun shouldPulse(previous: Double?, current: Double?, onlyIncrease: Boolean): Boolean {
        if (previous == null || current == null) return false
        if (Math.abs(current - previous) < 0.005) return false
        return !onlyIncrease || current > previous
    }
}
