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
 * - Other leagues show a real scoreboard saved on 2026-10-25/26 (`espn/<league id>/scoreboard.json`).
 *   One game each in the NBA, NHL, MLB and Premier League replays its closing stretch the same way,
 *   so every sport's live path can be tested.
 * - UFC's main event and the F1 race are live for the first [LIVE_EVENT_MS], then final.
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
        val replayed = board.copy(games = board.games.map { g -> if (g.id in summaries) liveGame(g.id, nowMs) ?: g else g })
        if (nowMs - startMs >= LIVE_EVENT_MS) return replayed
        return when (league) {
            League.UFC -> replayed.copy(games = liveMainEvent(replayed.games, nowMs))
            League.F1 -> replayed.copy(races = replayed.races.map { liveRace(it, nowMs) })
            else -> replayed
        }
    }

    /** The last bout on each card, in round 1–3 with no winner yet. */
    private fun liveMainEvent(games: List<Game>, nowMs: Long): List<Game> {
        val mains = games.groupBy { it.eventId }.values.map { b -> b.maxBy { it.order }.id }.toSet()
        val sec = ((nowMs - startMs) / 1000).toInt()
        val round = (sec / 200 + 1).coerceAtMost(3)
        val left = 300 - sec % 200 * 300 / 200
        return games.map { g ->
            if (g.id !in mains) g
            else g.copy(
                state = GameState.LIVE, period = round, clock = "${left / 60}:${(left % 60).toString().padStart(2, '0')}",
                shortDetail = "R$round ${left / 60}:${(left % 60).toString().padStart(2, '0')}",
                home = g.home.copy(winner = null), away = g.away.copy(winner = null),
            )
        }
    }

    /** The race session running, a lap every few seconds. */
    private fun liveRace(r: com.sofasports.tv.domain.Race, nowMs: Long): com.sofasports.tv.domain.Race {
        val lap = ((nowMs - startMs) / 1000 / SECONDS_PER_PLAY).toInt() + 30
        return r.copy(state = GameState.LIVE, shortDetail = "Lap ${lap.coerceAtMost(55)} of 56", results = r.results.map { it.copy(winner = false) })
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
            // Soccer plays carry the minute ("67'") and baseball the half inning ("Bot 8th") as their label.
            shortDetail = play.periodLabel ?: "${periodName(play.period, g.league)} ${play.clock}",
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
        const val LIVE_EVENT_MS = 10 * 60 * 1000L
        val REPLAYS = linkedMapOf(
            "401872996" to 120, "401873006" to 0, "401909861" to 380,
            "401892578" to 250, "401908043" to 470, "401879254" to 3,
        )
        val SUMMARY_FILES = mapOf(
            "401872996" to "espn/summary_401872996_hou_jax.json",
            "401873006" to "espn/summary_401873006_lac_kc.json",
            "401909861" to "espn/nba/summary_401909861_lal_utah.json",
            "401892578" to "espn/nhl/summary_401892578.json",
            "401908043" to "espn/mlb/summary_401908043.json",
            "401879254" to "espn/epl/summary_401879254.json",
        )
        /** Replays outside the NFL: LAL @ UTAH, FLA @ CHI, TB @ MIL, NEW @ CRY. */
        val REPLAY_LEAGUES = mapOf(
            "401909861" to League.NBA, "401892578" to League.NHL,
            "401908043" to League.MLB, "401879254" to League.EPL,
        )
    }
}
