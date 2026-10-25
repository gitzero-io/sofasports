package com.sofasports.tv.domain

import kotlinx.serialization.Serializable

/** Where the pick was placed. Entered by hand: no book has a public API for your entries. */
@Serializable
enum class PickBook(val label: String, val tag: String) {
    PRIZEPICKS("PrizePicks", "PP"),
    DRAFTKINGS("DraftKings", "DK"),
    FANDUEL("FanDuel", "FD"),
    BETMGM("BetMGM", "MGM"),
    OTHER("Other", "BET"),
    ;

    /** PrizePicks is player props only; sportsbooks take every bet type. */
    val propsOnly get() = this == PRIZEPICKS
}

@Serializable
enum class PickType(val label: String, val hint: String) {
    PROP("Player prop", "Yards, catches, TDs, anytime TD, longest play, kicking"),
    SPREAD("Spread", "A team with the points"),
    TOTAL("Game total", "Over/under on both teams' points"),
    MONEYLINE("Moneyline", "Who wins the game"),
    TEAM_TOTAL("Team total", "Over/under on one team's points"),
    ;

    /** Has an over/under number the live value climbs toward. */
    val overUnder get() = this == PROP || this == TOTAL || this == TEAM_TOTAL
}

/**
 * Player prop stats that can be read from ESPN's box score.
 * [monotonic] stats only go up during a game, so an over can be settled early.
 */
@Serializable
enum class PropStat(val label: String, val short: String, val defaultLine: Double, val monotonic: Boolean, val positions: Set<String>) {
    PASS_YDS("Passing yards", "Pass Yds", 224.5, false, setOf("QB")),
    PASS_TDS("Passing TDs", "Pass TD", 1.5, true, setOf("QB")),
    COMPLETIONS("Completions", "Comp", 20.5, true, setOf("QB")),
    PASS_ATTEMPTS("Pass attempts", "Att", 31.5, true, setOf("QB")),
    INTERCEPTIONS("Interceptions thrown", "INT", 0.5, true, setOf("QB")),
    RUSH_YDS("Rushing yards", "Rush Yds", 49.5, false, setOf("QB", "RB", "WR")),
    RUSH_ATTEMPTS("Rush attempts", "Rush Att", 12.5, true, setOf("QB", "RB")),
    RECEPTIONS("Receptions", "Rec", 3.5, true, setOf("RB", "WR", "TE")),
    REC_YDS("Receiving yards", "Rec Yds", 49.5, false, setOf("RB", "WR", "TE")),
    RUSH_REC_YDS("Rush + rec yards", "R+R Yds", 69.5, false, setOf("RB", "WR", "TE")),
    PASS_RUSH_YDS("Pass + rush yards", "P+R Yds", 249.5, false, setOf("QB")),
    ANYTIME_TD("Anytime TD", "Anytime TD", 0.5, true, setOf("QB", "RB", "WR", "TE")),
    RUSH_TDS("Rushing TDs", "Rush TD", 0.5, true, setOf("QB", "RB", "WR")),
    REC_TDS("Receiving TDs", "Rec TD", 0.5, true, setOf("RB", "WR", "TE")),
    LONGEST_RUSH("Longest rush", "Long Rush", 12.5, true, setOf("QB", "RB")),
    LONGEST_REC("Longest reception", "Long Rec", 19.5, true, setOf("RB", "WR", "TE")),
    TARGETS("Targets", "Tgts", 5.5, true, setOf("RB", "WR", "TE")),
    FG_MADE("Field goals made", "FGM", 1.5, true, setOf("K")),
    KICKING_PTS("Kicking points", "Kick Pts", 7.5, true, setOf("K")),
    ;

    /** Yes/no props: always "over 0.5", so there's no line or over/under to pick. */
    val yesNo get() = this == ANYTIME_TD

    companion object {
        fun forPosition(pos: String?): List<PropStat> = entries.filter { pos == null || pos in it.positions }
    }
}

@Serializable
data class TrackedPick(
    val id: String,
    val book: PickBook,
    val type: PickType,
    /** Team the pick is on (prop: player's team; spread/moneyline: the side; total: either team in the game). */
    val teamAbbr: String,
    val line: Double = 0.0,
    /** Over / "More" for props and totals. */
    val over: Boolean = true,
    val playerId: String? = null,
    val playerName: String? = null,
    val stat: PropStat? = null,
    val createdAtMs: Long = 0L,
    /** Set when this pick is a leg of a parlay / entry ([BetTicket]); null for a plain single. */
    val ticketId: String? = null,
) {
    /** "C.J. Stroud Pass Yds O 224.5", "HOU +2.5", "HOU @ JAX O 42.5", "HOU ML" */
    fun describe(game: Game? = null): String = when (type) {
        PickType.PROP -> if (stat?.yesNo == true) "${playerName ?: "Player"} ${stat.short}"
        else "${playerName ?: "Player"} ${stat?.short ?: ""} ${if (over) "O" else "U"} ${Betting.formatNumber(line)}"
        PickType.SPREAD -> "$teamAbbr ${Betting.formatLine(line)}"
        PickType.TOTAL -> "${game?.let { "${it.away.abbr} @ ${it.home.abbr}" } ?: teamAbbr} ${if (over) "O" else "U"} ${Betting.formatNumber(line)}"
        PickType.MONEYLINE -> "$teamAbbr ML"
        PickType.TEAM_TOTAL -> "$teamAbbr team total ${if (over) "O" else "U"} ${Betting.formatNumber(line)}"
    }
}

/** How a pick stands right now. [current] is the live number (stat, cover margin, total). */
data class PickProgress(val pick: TrackedPick, val game: Game?, val current: Double?, val status: BetStatus) {
    val settled get() = status == BetStatus.WON || status == BetStatus.LOST || status == BetStatus.PUSH
}

object PickEvaluator {

    fun evaluate(pick: TrackedPick, game: Game?, summary: GameSummary?): PickProgress {
        if (game == null) return PickProgress(pick, null, null, BetStatus.PENDING)
        return when (pick.type) {
            PickType.PROP -> prop(pick, game, summary)
            PickType.SPREAD -> {
                val m = Betting.coverMargin(game, pick.teamAbbr, pick.line)
                PickProgress(pick, game, m, Betting.status(m, game.state))
            }
            PickType.TOTAL -> overUnder(pick, game, Betting.total(game)?.toDouble())
            PickType.MONEYLINE -> {
                val m = Betting.coverMargin(game, pick.teamAbbr, 0.0)
                PickProgress(pick, game, m, Betting.status(m, game.state))
            }
            PickType.TEAM_TOTAL -> {
                val pts = if (game.state == GameState.PRE) null else game.side(pick.teamAbbr)?.score?.toDouble()
                overUnder(pick, game, pts)
            }
        }
    }

    /** Points never come off the board: an over that's already over has won. */
    private fun overUnder(pick: TrackedPick, game: Game, points: Double?): PickProgress {
        val margin = points?.let { if (pick.over) it - pick.line else pick.line - it }
        val status = if (pick.over && margin != null && margin > 0 && game.state == GameState.LIVE) BetStatus.WON
        else Betting.status(margin, game.state)
        return PickProgress(pick, game, points, status)
    }

    private fun prop(pick: TrackedPick, game: Game, summary: GameSummary?): PickProgress {
        if (game.state == GameState.PRE) return PickProgress(pick, game, null, BetStatus.PENDING)
        val stat = pick.stat ?: return PickProgress(pick, game, null, BetStatus.PENDING)
        // Not in the box score yet = hasn't recorded that stat: 0 so far.
        val value = summary?.let { statValue(it, pick.playerId, stat) } ?: if (summary != null) 0.0 else null
        if (value == null) return PickProgress(pick, game, null, BetStatus.PENDING)
        val margin = if (pick.over) value - pick.line else pick.line - value
        val status = when {
            game.state == GameState.LIVE && stat.monotonic && pick.over && margin > 0 -> BetStatus.WON
            game.state == GameState.LIVE && stat.monotonic && !pick.over && margin < 0 -> BetStatus.LOST
            else -> Betting.status(margin, game.state)
        }
        return PickProgress(pick, game, value, status)
    }

    /** Reads one stat for one player from ESPN's box score tables. */
    fun statValue(summary: GameSummary, playerId: String?, stat: PropStat): Double? {
        if (playerId == null) return null
        fun cell(key: String, label: String): Double? {
            val table = summary.playerStats.firstOrNull { t -> t.key == key && t.rows.any { it.athleteId == playerId } } ?: return null
            val row = table.rows.first { it.athleteId == playerId }
            val raw = row.stats.getOrNull(table.labels.indexOf(label)) ?: return null
            return raw.replace(",", "").toDoubleOrNull()
        }
        fun cAtt(i: Int): Double? {
            val table = summary.playerStats.firstOrNull { t -> t.key == "passing" && t.rows.any { it.athleteId == playerId } } ?: return null
            val raw = table.rows.first { it.athleteId == playerId }.stats.getOrNull(table.labels.indexOf("C/ATT")) ?: return null
            return raw.split("/").getOrNull(i)?.toDoubleOrNull()
        }
        /** "2/3" → 2 */
        fun made(key: String, label: String): Double? {
            val table = summary.playerStats.firstOrNull { t -> t.key == key && t.rows.any { it.athleteId == playerId } } ?: return null
            val raw = table.rows.first { it.athleteId == playerId }.stats.getOrNull(table.labels.indexOf(label)) ?: return null
            return raw.substringBefore("/").toDoubleOrNull()
        }
        fun sum(a: Double?, b: Double?) = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
        val anyRow = summary.playerStats.any { t -> t.rows.any { it.athleteId == playerId } }
        val v = when (stat) {
            PropStat.PASS_YDS -> cell("passing", "YDS")
            PropStat.PASS_TDS -> cell("passing", "TD")
            PropStat.INTERCEPTIONS -> cell("passing", "INT")
            PropStat.COMPLETIONS -> cAtt(0)
            PropStat.PASS_ATTEMPTS -> cAtt(1)
            PropStat.RUSH_YDS -> cell("rushing", "YDS")
            PropStat.RUSH_ATTEMPTS -> cell("rushing", "CAR")
            PropStat.RECEPTIONS -> cell("receiving", "REC")
            PropStat.REC_YDS -> cell("receiving", "YDS")
            PropStat.RUSH_REC_YDS -> sum(cell("rushing", "YDS"), cell("receiving", "YDS"))
            PropStat.PASS_RUSH_YDS -> sum(cell("passing", "YDS"), cell("rushing", "YDS"))
            PropStat.ANYTIME_TD -> sum(cell("rushing", "TD"), cell("receiving", "TD"))
            PropStat.RUSH_TDS -> cell("rushing", "TD")
            PropStat.REC_TDS -> cell("receiving", "TD")
            PropStat.LONGEST_RUSH -> cell("rushing", "LONG")
            PropStat.LONGEST_REC -> cell("receiving", "LONG")
            PropStat.TARGETS -> cell("receiving", "TGTS")
            PropStat.FG_MADE -> made("kicking", "FG")
            PropStat.KICKING_PTS -> cell("kicking", "PTS")
        }
        // Player appears in some table but not this one (e.g. a WR with no rushes): that's 0.
        return v ?: if (anyRow) 0.0 else null
    }

    /** Overlay/Home summary: "3 of 5 hitting". Hitting = winning or won. */
    fun hitting(progress: List<PickProgress>) = progress.count { it.status == BetStatus.WINNING || it.status == BetStatus.WON }
}

/**
 * A parlay (or PrizePicks entry): two or more legs that all have to hit. Legs are [TrackedPick]s
 * pointing back with [TrackedPick.ticketId]. No money is tracked, only whether it hits.
 */
@Serializable
data class BetTicket(
    val id: String,
    val book: PickBook,
    val createdAtMs: Long = 0L,
)

data class TicketProgress(val ticket: BetTicket, val legs: List<PickProgress>, val status: BetStatus) {
    val settled get() = status == BetStatus.WON || status == BetStatus.LOST || status == BetStatus.PUSH
    val legsWon get() = legs.count { it.status == BetStatus.WON }
}

object TicketEvaluator {
    /**
     * All legs must hit. One lost leg loses the ticket; pushed legs drop out (all pushed = push).
     * Otherwise: losing if any open leg is losing, pending if any hasn't started, else winning.
     */
    fun status(legs: List<PickProgress>): BetStatus {
        if (legs.isEmpty()) return BetStatus.PENDING
        if (legs.any { it.status == BetStatus.LOST }) return BetStatus.LOST
        if (legs.all { it.settled }) return if (legs.all { it.status == BetStatus.PUSH }) BetStatus.PUSH else BetStatus.WON
        val open = legs.filterNot { it.settled }
        return when {
            open.any { it.status == BetStatus.LOSING } -> BetStatus.LOSING
            open.any { it.status == BetStatus.PENDING } -> BetStatus.PENDING
            open.all { it.status == BetStatus.PUSHING } -> BetStatus.PUSHING
            else -> BetStatus.WINNING
        }
    }

    fun group(tickets: List<BetTicket>, progress: List<PickProgress>): List<TicketProgress> {
        val legs = progress.filter { it.pick.ticketId != null }.groupBy { it.pick.ticketId }
        return tickets.map { t ->
            val l = legs[t.id].orEmpty()
            TicketProgress(t, l, status(l))
        }
    }
}
