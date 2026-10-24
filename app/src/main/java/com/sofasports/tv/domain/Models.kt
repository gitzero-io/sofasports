package com.sofasports.tv.domain

enum class GameState { PRE, LIVE, FINAL, OTHER }

data class TeamSide(
    val id: String,
    val abbr: String,
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    val score: Int?,
    val record: String?,
    val isHome: Boolean,
    val timeouts: Int? = null,
)

data class Situation(
    val downDistance: String?,
    val possessionTeamId: String?,
    val isRedZone: Boolean,
    val lastPlay: String?,
)

data class Game(
    val id: String,
    val startEpochMs: Long,
    val state: GameState,
    val home: TeamSide,
    val away: TeamSide,
    /** 1-4 regulation, 5+ overtime, 0 before kickoff. */
    val period: Int,
    val clock: String,
    /** e.g. "Final", "Final/OT", "4th 2:13", "Halftime", "10/25 - 1:00 PM EDT". */
    val shortDetail: String,
    val broadcast: String?,
    val situation: Situation? = null,
    val week: Int? = null,
) {
    val teams get() = listOf(away, home)
    fun involves(teamAbbr: String) = home.abbr == teamAbbr || away.abbr == teamAbbr
    fun side(teamAbbr: String): TeamSide? = teams.firstOrNull { it.abbr == teamAbbr }
}

data class Scoreboard(val season: Int?, val week: Int?, val games: List<Game>)

data class WinProbPoint(val playId: String, val homeWinPct: Double, val tiePct: Double)

data class TeamStatRow(val label: String, val away: String, val home: String)

data class PlayerStatTable(
    val teamAbbr: String,
    val category: String,
    val labels: List<String>,
    val rows: List<PlayerStatRow>,
)

data class PlayerStatRow(val name: String, val stats: List<String>)

data class Play(
    val id: String,
    val period: Int,
    val clock: String,
    val text: String,
    val type: String?,
    val awayScore: Int,
    val homeScore: Int,
    val isScoring: Boolean,
)

data class Drive(
    val id: String,
    val teamAbbr: String?,
    val description: String?,
    val result: String?,
    val isScore: Boolean,
    val plays: List<Play>,
)

data class GameSummary(
    val game: Game,
    val winProbability: List<WinProbPoint>,
    val teamStats: List<TeamStatRow>,
    val playerStats: List<PlayerStatTable>,
    val drives: List<Drive>,
    val scoringPlays: List<Play>,
    val venue: String?,
) {
    /** All plays, most recent first. */
    val playsNewestFirst: List<Play> get() = drives.flatMap { it.plays }.asReversed()
}

// ---- Fantasy ----

data class FantasyUser(val userId: String, val displayName: String, val avatar: String?)

data class FantasyLeague(
    val id: String,
    val name: String,
    val season: String,
    val totalRosters: Int,
    val status: String?,
)

data class FantasyPlayer(val id: String, val name: String, val position: String?, val team: String?)

data class FantasyStarter(val player: FantasyPlayer, val points: Double)

data class FantasyTeam(
    val rosterId: Int,
    val ownerId: String?,
    val teamName: String,
    val points: Double,
    val starters: List<FantasyStarter>,
)

data class FantasyMatchup(
    val leagueId: String,
    val leagueName: String,
    val week: Int,
    val me: FantasyTeam,
    /** Null on a bye week / odd number of teams. */
    val opponent: FantasyTeam?,
)
