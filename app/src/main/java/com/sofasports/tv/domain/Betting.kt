package com.sofasports.tv.domain

import java.util.Locale

/** Status of a bet or pick against the current numbers. */
enum class BetStatus { PENDING, WINNING, LOSING, PUSHING, WON, LOST, PUSH }

object Betting {
    /** Short provider tag shown next to lines. */
    fun providerTag(provider: String) = if (provider.contains("draft", ignoreCase = true)) "DK" else provider.take(4).uppercase()

    fun formatLine(v: Double): String {
        val s = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
        return if (v > 0) "+$s" else s
    }

    fun formatNumber(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

    /** "PIT -3 · O/U 44.5" */
    fun summary(o: GameOdds): String = listOfNotNull(o.details, o.overUnder?.let { "O/U ${formatNumber(it)}" }).joinToString(" · ")

    /** Points [team] is ahead by after applying [line] (e.g. +3.5). Positive = covering. Null before kickoff. */
    fun coverMargin(game: Game, teamAbbr: String, line: Double): Double? {
        val me = game.side(teamAbbr) ?: return null
        val other = if (me.id == game.home.id) game.away else game.home
        val a = me.score ?: return null
        val b = other.score ?: return null
        return a + line - b
    }

    fun total(game: Game): Int? {
        val h = game.home.score ?: return null
        val a = game.away.score ?: return null
        return h + a
    }

    /**
     * Turns a margin (positive = this side of the bet is ahead) into a status.
     * Live games are "winning/losing", finals "won/lost", nothing before kickoff.
     */
    fun status(margin: Double?, state: GameState): BetStatus {
        if (margin == null || state == GameState.PRE) return BetStatus.PENDING
        val final = state == GameState.FINAL
        return when {
            margin > 0 -> if (final) BetStatus.WON else BetStatus.WINNING
            margin < 0 -> if (final) BetStatus.LOST else BetStatus.LOSING
            else -> if (final) BetStatus.PUSH else BetStatus.PUSHING
        }
    }

    /** The home or away line from the game's odds, from that team's point of view. */
    fun spreadFor(odds: GameOdds, game: Game, teamAbbr: String): Double? {
        val home = odds.homeSpread ?: return null
        return when (teamAbbr) {
            game.home.abbr -> home
            game.away.abbr -> -home
            else -> null
        }
    }
}
