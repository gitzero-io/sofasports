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
    /** Set once a result is in. Fights have no score, so this is the only way to tell who won. */
    val winner: Boolean? = null,
)

/** Live game state beyond the score. Only football fills possession and red zone. */
data class Situation(
    /** Football: "3rd & 4 at KC 32". Baseball: "2-1, 1 out · 1st & 3rd". Null when ESPN gives nothing. */
    val detail: String?,
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
    /** 1-based quarter / half / period / inning (fights: round); past [League.regulationPeriods] is overtime; 0 before the start. */
    val period: Int,
    val clock: String,
    /** e.g. "Final", "Final/OT", "4th 2:13", "Halftime", "10/25 - 1:00 PM EDT". */
    val shortDetail: String,
    val broadcast: String?,
    val situation: Situation? = null,
    val week: Int? = null,
    val odds: GameOdds? = null,
    /** Every network/service carrying it ("ESPN", "ABC", "Disney+"); [broadcast] is just the first. */
    val networks: List<String> = emptyList(),
    /** Fights: the card this bout is on ("UFC 333: Volkanovski vs. Evloev"); several bouts share it. */
    val eventId: String? = null,
    val eventName: String? = null,
    /** Fights: weight class ("Featherweight"); boxing: the result line once it's in. */
    val note: String? = null,
    /** Fights: position on the card; the highest is the main event. */
    val order: Int = 0,
    val league: League = League.NFL,
) {
    val teams get() = listOf(away, home)
    val ref: GameRef get() = GameRef(league, id)

    /** The favorite key for [side]: "<league>:<abbr>" for teams, "<league>:<name>" for fighters (see [TeamKey]). */
    fun keyOf(side: TeamSide): String = TeamKey.of(league, if (league.format == EventFormat.FIGHT) side.name else side.abbr)

    /** True when [team] is this game's side: a favorite key (see [TeamKey]), or a bare abbreviation within the same league. */
    fun involves(team: String) = side(team) != null

    fun side(team: String): TeamSide? {
        if (':' in team) {
            if (TeamKey.league(team) != league) return null
            val key = TeamKey.normalize(team)
            return teams.firstOrNull { keyOf(it).equals(key, ignoreCase = true) }
        }
        return teams.firstOrNull { it.abbr == team }
    }

    /** The side that won: higher score, or the fight winner. Null while undecided or tied. */
    val winnerSide: TeamSide?
        get() {
            teams.firstOrNull { it.winner == true }?.let { return it }
            if (state != GameState.FINAL) return null
            val h = home.score ?: return null
            val a = away.score ?: return null
            return when {
                h > a -> home
                a > h -> away
                else -> null
            }
        }

    /** Past regulation: overtime, extra innings, extra time. */
    val inOvertime: Boolean get() = league.regulationPeriods > 0 && period > league.regulationPeriods
}

/** Pre-game betting line (ESPN's odds provider is DraftKings). */
data class GameOdds(
    val provider: String,
    /** e.g. "PIT -3" */
    val details: String?,
    val overUnder: Double?,
    /** Home spread from the home team's view, e.g. -2.5 when home is favored. */
    val homeSpread: Double?,
    val homeMoneyline: String?,
    val awayMoneyline: String?,
)

data class Scoreboard(
    val season: Int?,
    val week: Int?,
    val games: List<Game>,
    val league: League = League.NFL,
    /** Racing only: one entry per race weekend. */
    val races: List<Race> = emptyList(),
)

/** One race weekend, showing the session that matters now (the live one, else the next, else the last). */
data class Race(
    val id: String,
    val league: League,
    /** "United States Grand Prix" */
    val name: String,
    /** "Race", "Qualifying", "Practice 2" */
    val session: String,
    val startEpochMs: Long,
    val state: GameState,
    /** ESPN's status text: "Final", "Lap 34 of 56", "10/25 - 4:00 PM EDT" */
    val shortDetail: String,
    val venue: String?,
    val broadcast: String?,
    val networks: List<String> = emptyList(),
    /** Running or finishing order; empty before the session starts. */
    val results: List<RaceEntry> = emptyList(),
    /** Every session of the weekend, in order, for the detail screen. */
    val sessions: List<RaceSession> = emptyList(),
) {
    val ref: GameRef get() = GameRef(league, id)
    val winner: RaceEntry? get() = results.firstOrNull { it.winner } ?: results.firstOrNull().takeIf { state == GameState.FINAL }

    /** Everyone entered this weekend: the current running order, else any session's entry list. */
    val entrants: List<RaceEntry> get() = results.ifEmpty { sessions.firstOrNull { it.results.isNotEmpty() }?.results.orEmpty() }

    /** Favorite drivers ([TeamKey]s) racing this weekend, in favorites order. */
    fun favorites(keys: List<String>): List<RaceEntry> = keys.mapNotNull { k ->
        if (TeamKey.league(k) != league) return@mapNotNull null
        entrants.firstOrNull { it.key(league).equals(TeamKey.normalize(k), ignoreCase = true) }
    }

    fun involvesAny(keys: List<String>) = favorites(keys).isNotEmpty()

    /** Where a favorite driver is now, when they're in [results]; null before the session runs. */
    fun positionOf(entry: RaceEntry): Int? = results.firstOrNull { it.driverId == entry.driverId }?.position
}

data class RaceSession(val id: String, val name: String, val startEpochMs: Long, val state: GameState, val results: List<RaceEntry>)

data class RaceEntry(
    val position: Int,
    val driverId: String,
    val name: String,
    val shortName: String,
    /** Country flag; ESPN has no driver photos on the scoreboard. */
    val flagUrl: String?,
    val winner: Boolean = false,
) {
    fun key(league: League) = TeamKey.of(league, name)
}

data class WinProbPoint(val playId: String, val homeWinPct: Double, val tiePct: Double)

data class TeamStatRow(val label: String, val away: String, val home: String)

data class PlayerStatTable(
    val teamAbbr: String,
    /** "passing" | "rushing" | "receiving" */
    val key: String = "",
    val category: String,
    val labels: List<String>,
    val rows: List<PlayerStatRow>,
)

data class PlayerStatRow(val name: String, val stats: List<String>, val athleteId: String? = null)

data class Play(
    val id: String,
    val period: Int,
    val clock: String,
    val text: String,
    val type: String?,
    val awayScore: Int,
    val homeScore: Int,
    val isScoring: Boolean,
    /** "Top 9th", "47'": set when the sport doesn't number its periods like quarters. */
    val periodLabel: String? = null,
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
    /** Plays for sports without drives (basketball, baseball, hockey, soccer key events), oldest first. */
    val plays: List<Play> = emptyList(),
) {
    /** All plays, most recent first. */
    val playsNewestFirst: List<Play> get() = (drives.flatMap { it.plays } + plays).asReversed()
}

// ---- Fantasy ----

data class FantasyUser(val userId: String, val displayName: String, val avatar: String?)

enum class FantasySource(val label: String) { SLEEPER("Sleeper"), ESPN("ESPN") }

data class FantasyLeague(
    val id: String,
    val name: String,
    val season: String,
    val totalRosters: Int,
    val status: String?,
    val source: FantasySource = FantasySource.SLEEPER,
    /** NFL, or NBA for Sleeper basketball leagues (experimental). */
    val sport: League = League.NFL,
) {
    /** "Sleeper · Friends League", "Sleeper NBA · Hoops" */
    val switcherLabel: String get() = "${source.label}${if (sport != League.NFL) " ${sport.label}" else ""} · $name"
}

data class FantasyPlayer(
    val id: String,
    val name: String,
    val position: String?,
    val team: String?,
    /** Player photo; null for team defenses (the UI shows the team logo) or when the source has none. */
    val headshotUrl: String? = null,
    /** Short injury tag from the fantasy site: "Q", "D", "O", "IR"; null when healthy. */
    val injury: String? = null,
)

data class FantasyStarter(
    val player: FantasyPlayer,
    val points: Double,
    /** The site's pre-game projection for this week; null when it has none. */
    val projected: Double? = null,
    /** Lineup slot ("QB", "FLEX", "D/ST"…), when the site says. */
    val slot: String? = null,
    /**
     * Basketball only: [projected] split into the player's game today and the games after it this
     * week (a player plays several). Null for football, where the week is one game.
     */
    val projectedToday: Double? = null,
    val projectedLater: Double? = null,
)

data class FantasyTeam(
    val rosterId: Int,
    val ownerId: String?,
    val teamName: String,
    val points: Double,
    val starters: List<FantasyStarter>,
    /** "5-2" or "4-2-1". */
    val record: String? = null,
)

data class FantasyMatchup(
    val leagueId: String,
    val leagueName: String,
    val week: Int,
    val me: FantasyTeam,
    /** Null on a bye week / odd number of teams. */
    val opponent: FantasyTeam?,
    val sport: League = League.NFL,
)
