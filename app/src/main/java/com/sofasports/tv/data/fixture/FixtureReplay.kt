package com.sofasports.tv.data.fixture

import com.sofasports.tv.data.espn.EspnParser
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.Periods
import com.sofasports.tv.domain.Play
import com.sofasports.tv.domain.Scoreboard
import com.sofasports.tv.domain.Situation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fixture mode: turns last week's finished games into a fake live Sunday so every screen can be
 * exercised offline, including live-only behaviour (close-game badge, score pulse, polling).
 *
 * - The two games with summary fixtures replay play-by-play, one play every [SECONDS_PER_PLAY]s.
 *   HOU @ JAX starts late in the 3rd quarter so the close-game badge shows within minutes.
 * - The rest of week 6 shows as final.
 * - Week 7's schedule is shifted so the first kickoff is a few minutes away (countdown).
 * - Other leagues show a real scoreboard saved on 2026-10-25/26 (`espn/<league id>/scoreboard.json`);
 *   in the NBA, LAL @ UTAH replays its fourth quarter the same way, so non-football live paths can be tested.
 */
@Singleton
class FixtureReplay @Inject constructor(private val source: FixtureSource) {

    private val startMs = System.currentTimeMillis()
    private val summaries: Map<String, GameSummary> by lazy {
        REPLAYS.keys.mapNotNull { id ->
            val league = REPLAY_LEAGUES[id] ?: League.NFL
            source.read(SUMMARY_FILES.getValue(id))?.let { id to EspnParser.parseSummary(it, league) }
        }.toMap()
    }

    /** Plays in order: drives for football, the flat list elsewhere. */
    private fun GameSummary.orderedPlays(): List<Play> = drives.flatMap { it.plays } + plays

    /** Fraction of the replayed slate that has elapsed, 0..1. Used to grow fake fantasy points. */
    fun progress(nowMs: Long = System.currentTimeMillis()): Double {
        // Measured on the game that replays from kickoff.
        val id = REPLAYS.entries.first { it.value == 0 }.key
        val total = summaries[id]?.orderedPlays()?.size?.coerceAtLeast(1) ?: return 1.0
        return (playIndex(id, nowMs).toDouble() / total).coerceIn(0.0, 1.0)
    }

    fun scoreboard(league: League, nowMs: Long = System.currentTimeMillis()): Scoreboard? {
        if (league == League.NFL) return scoreboard(nowMs)
        val board = source.read("espn/${league.id}/scoreboard.json")?.let { json ->
            if (league == League.BOXING) com.sofasports.tv.data.sportsdb.SportsDbParser.parseBoxing(json) else EspnParser.parseScoreboard(json, league)
        } ?: return null
        return board.copy(games = board.games.map { g -> if (g.id in summaries) liveGame(g.id, nowMs) ?: g else g })
    }

    fun scoreboard(nowMs: Long = System.currentTimeMillis()): Scoreboard {
        val finals = source.read("espn/scoreboard_2026_w6_final.json")?.let(EspnParser::parseScoreboard)
        val upcoming = source.read("espn/scoreboard_2026_w7_upcoming.json")?.let(EspnParser::parseScoreboard)
        val replayed = finals?.games.orEmpty().map { g -> if (g.id in summaries) liveGame(g.id, nowMs) ?: g else g }
        val pre = upcoming?.games.orEmpty().filter { it.state == GameState.PRE }
        val firstKick = pre.minOfOrNull { it.startEpochMs } ?: 0L
        val shifted = pre.map { it.copy(startEpochMs = startMs + FIRST_KICKOFF_DELAY_MS + (it.startEpochMs - firstKick)) }
        return Scoreboard(season = finals?.season, week = finals?.week, games = replayed + shifted)
    }

    fun summary(gameId: String, nowMs: Long = System.currentTimeMillis()): GameSummary? {
        val full = summaries[gameId] ?: return null
        val idx = playIndex(gameId, nowMs)
        val plays = full.orderedPlays()
        if (idx >= plays.size) return full
        val shown = plays.take(idx + 1).map { it.id }.toSet()
        val last = plays[idx]
        val fraction = (idx + 1).toDouble() / plays.size
        return full.copy(
            game = liveGame(gameId, nowMs) ?: full.game,
            // The fixture only has end-of-game player stats; scale them so props move during a replay.
            playerStats = full.playerStats.map { t -> t.copy(rows = t.rows.map { r -> r.copy(stats = r.stats.map { scaleStat(it, fraction) }) }) },
            winProbability = full.winProbability.filter { it.playId in shown },
            drives = full.drives.mapNotNull { d -> d.copy(plays = d.plays.filter { it.id in shown }).takeIf { it.plays.isNotEmpty() } },
            plays = full.plays.filter { it.id in shown },
            scoringPlays = full.scoringPlays.filter { it.awayScore + it.homeScore <= last.awayScore + last.homeScore },
        )
    }

    /** "312" → "143", "24/34" → "11/16"; non-numbers unchanged. Fixture-only approximation. */
    private fun scaleStat(v: String, f: Double): String =
        if (v.contains('/')) v.split('/').joinToString("/") { scaleStat(it, f) }
        else v.toIntOrNull()?.let { Math.round(it * f).toInt().toString() } ?: v

    private fun playIndex(gameId: String, nowMs: Long): Int {
        val elapsedPlays = ((nowMs - startMs) / 1000 / SECONDS_PER_PLAY).toInt()
        return (REPLAYS.getValue(gameId) + elapsedPlays).coerceAtLeast(0)
    }

    private fun liveGame(gameId: String, nowMs: Long): Game? {
        val full = summaries[gameId] ?: return null
        val all = full.orderedPlays()
        val idx = playIndex(gameId, nowMs)
        if (idx >= all.size - 1) return full.game
        val play = all[idx]
        val drive = full.drives.firstOrNull { d -> d.plays.any { it.id == play.id } }
        val g = full.game
        val possessionId = listOf(g.home, g.away).firstOrNull { it.abbr == drive?.teamAbbr }?.id
        return g.copy(
            state = GameState.LIVE,
            period = play.period,
            clock = play.clock,
            shortDetail = "${periodName(play.period, g.league)} ${play.clock}",
            home = g.home.copy(score = play.homeScore, winner = null),
            away = g.away.copy(score = play.awayScore, winner = null),
            situation = Situation(detail = null, possessionTeamId = possessionId, isRedZone = false, lastPlay = play.text),
        )
    }

    private fun periodName(p: Int, league: League) =
        if (p > league.regulationPeriods) Periods.label(league, p) else Periods.ordinal(p)

    companion object {
        const val SECONDS_PER_PLAY = 4L
        const val FIRST_KICKOFF_DELAY_MS = 3 * 60 * 1000L
        /** Game id → play index the replay starts at. */
        val REPLAYS = linkedMapOf("401872996" to 120, "401873006" to 0, "401909861" to 380)
        val SUMMARY_FILES = mapOf(
            "401872996" to "espn/summary_401872996_hou_jax.json",
            "401873006" to "espn/summary_401873006_lac_kc.json",
            "401909861" to "espn/nba/summary_401909861_lal_utah.json",
        )
        /** Replays outside the NFL. */
        val REPLAY_LEAGUES = mapOf("401909861" to League.NBA)
    }
}
