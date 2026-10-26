package com.sofasports.tv.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Where a starter's game is (today's, in basketball): not started, playing, done, or none. */
enum class PlayerGamePhase { PRE, LIVE, DONE, NO_GAME }

data class StarterLive(
    val starter: FantasyStarter,
    val game: Game?,
    val phase: PlayerGamePhase,
    /** Points so far plus the projection for the game time left (ESPN's "live projection"). */
    val liveProjected: Double?,
    /** Share of the player's game (basketball: week) still to play, 0..1. */
    val remaining: Double,
    val sport: League = League.NFL,
)

data class TeamLive(val team: FantasyTeam, val starters: List<StarterLive>, val projected: Double?) {
    fun count(phase: PlayerGamePhase) = starters.count { it.phase == phase }

    /** Not playing now but with a game still to come: today's, or (basketball) later this week. */
    val yetToPlay: Int get() = starters.count { it.phase == PlayerGamePhase.PRE || (it.phase != PlayerGamePhase.LIVE && (it.starter.projectedLater ?: 0.0) > 0.0) }
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
 *
 * Basketball weeks have several games per player, so a starter's remaining projection is his later
 * games plus whatever is left of today's ([FantasyStarter.projectedToday]/[projectedLater]).
 */
object FantasyProjection {
    /** How much a player's remaining projection can swing, as a share of it (one standard deviation). */
    private const val SPREAD = 0.5

    /** [games] may hold every league's games; only the matchup's sport is used. */
    fun live(m: FantasyMatchup, games: List<Game>): MatchupLive {
        val byTeam = games.filter { it.league == m.sport }.flatMap { g -> g.teams.map { it.abbr to g } }.toMap()
        fun team(t: FantasyTeam) = t.starters.map { starter(it, it.player.team?.let { abbr -> byTeam[FantasyTeams.scoreboardAbbr(m.sport, abbr)] }).copy(sport = m.sport) }
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
        val later = s.projectedLater
        val live = if (later != null) {
            // Basketball: points so far, the games after today, and what's left of today's.
            val today = s.projectedToday ?: 0.0
            s.points + later + when (phase) {
                PlayerGamePhase.PRE -> today
                PlayerGamePhase.LIVE -> today * remaining
                PlayerGamePhase.DONE, PlayerGamePhase.NO_GAME -> 0.0
            }
        } else s.projected?.let { p ->
            when (phase) {
                PlayerGamePhase.PRE -> p
                PlayerGamePhase.LIVE -> s.points + p * remaining
                PlayerGamePhase.DONE, PlayerGamePhase.NO_GAME -> s.points
            }
        }
        // Share of the player's week still to play: from the projection when there is one.
        val weekLeft = if (later != null) s.projected?.takeIf { it > 0 }?.let { p -> ((live ?: s.points) - s.points) / p }?.coerceIn(0.0, 1.0) ?: remaining else remaining
        return StarterLive(s, game, phase, live, weekLeft)
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
            val periods = game.league.regulationPeriods.takeIf { it > 0 } ?: 4
            val minutes = periodMinutes(game.league)
            if (game.period > periods) 0.02
            else {
                val clock = game.clock.split(":").let { (it.getOrNull(0)?.toDoubleOrNull() ?: 0.0) + (it.getOrNull(1)?.toDoubleOrNull() ?: 0.0) / 60 }
                (((periods - game.period.coerceIn(1, periods)) * minutes + clock) / (periods * minutes)).coerceIn(0.0, 1.0)
            }
        }
        else -> 0.0
    }

    private fun periodMinutes(league: League) = when (league) {
        League.NBA -> 12.0
        League.CBB -> 20.0
        else -> 15.0
    }

    fun winProbability(me: TeamLive, opp: TeamLive): Double? {
        val mine = me.projected ?: return null
        val theirs = opp.projected ?: return null
        val variance = (me.starters + opp.starters).sumOf { s ->
            // What the player is still expected to add (football: projection × game left).
            val left = s.liveProjected?.let { it - s.starter.points }?.coerceAtLeast(0.0) ?: 0.0
            val sd = SPREAD * left
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

/** Fantasy sites' team abbreviations → ESPN scoreboard ones, per sport. */
object FantasyTeams {
    private val nba = mapOf("GSW" to "GS", "NOP" to "NO", "NYK" to "NY", "SAS" to "SA", "UTA" to "UTAH", "WAS" to "WSH")

    fun scoreboardAbbr(sport: League, fantasyTeam: String): String = when (sport) {
        League.NBA -> fantasyTeam.uppercase().let { nba[it] ?: it }
        else -> NflTeams.scoreboardAbbr(fantasyTeam)
    }

    fun logoUrl(sport: League, fantasyTeam: String): String? =
        if (sport == League.NFL) NflTeams.find(NflTeams.scoreboardAbbr(fantasyTeam))?.logoUrl
        else sport.logoUrl(scoreboardAbbr(sport, fantasyTeam))
}
