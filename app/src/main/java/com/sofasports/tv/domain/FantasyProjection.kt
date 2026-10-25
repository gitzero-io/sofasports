package com.sofasports.tv.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Where a starter's NFL game is: not started, playing, done, or no game this week. */
enum class PlayerGamePhase { PRE, LIVE, DONE, NO_GAME }

data class StarterLive(
    val starter: FantasyStarter,
    val game: Game?,
    val phase: PlayerGamePhase,
    /** Points so far plus the projection for the game time left (ESPN's "live projection"). */
    val liveProjected: Double?,
    /** Share of the player's game still to play, 0..1. */
    val remaining: Double,
)

data class TeamLive(val team: FantasyTeam, val starters: List<StarterLive>, val projected: Double?) {
    fun count(phase: PlayerGamePhase) = starters.count { it.phase == phase }
}

data class MatchupLive(
    val matchup: FantasyMatchup,
    val me: TeamLive,
    val opponent: TeamLive?,
    /** My chance to win, 0..1; null without projections or an opponent. */
    val winProbability: Double?,
)

/**
 * Live projections and win probability, the way ESPN's matchup screen shows them: a player who
 * hasn't played counts his projection, a finished player his points, and a player mid-game his
 * points plus the projection for the time left. Win probability treats each remaining projection
 * as uncertain (spread proportional to its size) and asks how likely my total ends higher.
 */
object FantasyProjection {
    /** How much a player's remaining projection can swing, as a share of it (one standard deviation). */
    private const val SPREAD = 0.5

    fun live(m: FantasyMatchup, games: List<Game>): MatchupLive {
        val byTeam = games.flatMap { g -> g.teams.map { it.abbr to g } }.toMap()
        fun team(t: FantasyTeam) = t.starters.map { starter(it, it.player.team?.let { abbr -> byTeam[NflTeams.scoreboardAbbr(abbr)] }) }
            .let { s -> TeamLive(t, s, total(s)) }
        val me = team(m.me)
        val opp = m.opponent?.let(::team)
        return MatchupLive(m, me, opp, opp?.let { winProbability(me, it) })
    }

    fun starter(s: FantasyStarter, game: Game?): StarterLive {
        val phase = when (game?.state) {
            null -> PlayerGamePhase.NO_GAME
            GameState.PRE -> PlayerGamePhase.PRE
            GameState.LIVE -> PlayerGamePhase.LIVE
            else -> PlayerGamePhase.DONE
        }
        val remaining = remaining(game)
        val live = s.projected?.let { p ->
            when (phase) {
                PlayerGamePhase.PRE -> p
                PlayerGamePhase.LIVE -> s.points + p * remaining
                PlayerGamePhase.DONE, PlayerGamePhase.NO_GAME -> s.points
            }
        }
        return StarterLive(s, game, phase, live, remaining)
    }

    /** Team live projection, or null when the site gave no projections at all. */
    private fun total(starters: List<StarterLive>): Double? =
        if (starters.none { it.starter.projected != null }) null
        else starters.sumOf { it.liveProjected ?: it.starter.points }

    /** Share of a game still to play from its period and clock; overtime counts as a sliver. */
    fun remaining(game: Game?): Double = when (game?.state) {
        null -> 0.0
        GameState.PRE -> 1.0
        GameState.LIVE -> {
            if (game.period > 4) 0.02
            else {
                val clock = game.clock.split(":").let { (it.getOrNull(0)?.toDoubleOrNull() ?: 0.0) + (it.getOrNull(1)?.toDoubleOrNull() ?: 0.0) / 60 }
                (((4 - game.period.coerceIn(1, 4)) * 15 + clock) / 60).coerceIn(0.0, 1.0)
            }
        }
        else -> 0.0
    }

    fun winProbability(me: TeamLive, opp: TeamLive): Double? {
        val mine = me.projected ?: return null
        val theirs = opp.projected ?: return null
        val variance = (me.starters + opp.starters).sumOf { s ->
            val p = s.starter.projected ?: 0.0
            val sd = SPREAD * p * s.remaining
            sd * sd
        }
        val margin = mine - theirs
        if (variance < 0.25) return if (margin > 0) 1.0 else if (margin < 0) 0.0 else 0.5
        return normalCdf(margin / sqrt(variance))
    }

    /** Standard normal CDF (Abramowitz & Stegun 7.1.26), plenty for a percentage on screen. */
    internal fun normalCdf(z: Double): Double {
        val x = abs(z) / sqrt(2.0)
        val t = 1 / (1 + 0.3275911 * x)
        val erf = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
        return if (z >= 0) (1 + erf) / 2 else (1 - erf) / 2
    }
}
