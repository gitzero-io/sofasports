package com.sofasports.tv.overlay

import com.sofasports.tv.data.prefs.MomentKind
import com.sofasports.tv.data.prefs.OverlayPage
import com.sofasports.tv.domain.BetStatus
import com.sofasports.tv.domain.CloseGame
import com.sofasports.tv.domain.Perspective
import com.sofasports.tv.domain.TickerBuilder
import com.sofasports.tv.ui.fantasy.formatPoints

/** Colour of the moment banner: blue = good for me, red = good for the opponent. */
enum class MomentTone { MINE, OPPONENT, NEUTRAL }

data class Moment(val kind: MomentKind, val text: String, val page: OverlayPage, val tone: MomentTone, val key: String)

/**
 * Big moments found by comparing two consecutive overlay states. Only things that changed between
 * the two count, so the first load (or a league switch) never pops.
 */
object BigMoments {
    /** A starter gaining this many fantasy points between polls is "my player scored". */
    const val PLAYER_POINTS = 4.0

    fun detect(prev: OverlayState, cur: OverlayState, kinds: Set<MomentKind>): List<Moment> = buildList {
        if (MomentKind.MY_GAME_SCORE in kinds) myGameScore(prev, cur)?.let(::add)
        if (MomentKind.PLAYER_SCORED in kinds) addAll(playerScored(prev, cur))
        if (MomentKind.LEAD_CHANGE in kinds) leadChange(prev, cur)?.let(::add)
        if (MomentKind.PICK_RESULT in kinds) addAll(pickResults(prev, cur))
        if (MomentKind.CLOSE_GAME in kinds) addAll(closeGames(prev, cur))
    }

    private fun myGameScore(prev: OverlayState, cur: OverlayState): Moment? {
        val g = cur.game ?: return null
        val old = prev.game?.takeIf { it.id == g.id } ?: return null
        val scorer = g.teams.firstOrNull { t -> (t.score ?: 0) > (old.side(t.abbr)?.score ?: Int.MAX_VALUE) } ?: return null
        val delta = (scorer.score ?: 0) - (old.side(scorer.abbr)?.score ?: 0)
        val mine = Perspective.mySide(g, cur.favorites).id == scorer.id
        val score = "${g.away.abbr} ${g.away.score ?: 0}–${g.home.score ?: 0} ${g.home.abbr}"
        return Moment(
            MomentKind.MY_GAME_SCORE,
            "${TickerBuilder.scoreLabel(delta)} ${scorer.abbr} · $score",
            OverlayPage.MY_GAME,
            if (mine) MomentTone.MINE else MomentTone.OPPONENT,
            "score-${g.id}-${g.away.score}-${g.home.score}",
        )
    }

    private fun playerScored(prev: OverlayState, cur: OverlayState): List<Moment> {
        val m = cur.matchup ?: return emptyList()
        val old = prev.matchup?.takeIf { it.leagueId == m.leagueId && it.week == m.week } ?: return emptyList()
        val before = old.me.starters.associate { it.player.id to it.points }
        return m.me.starters.mapNotNull { st ->
            val was = before[st.player.id] ?: return@mapNotNull null
            val gain = st.points - was
            if (gain < PLAYER_POINTS) return@mapNotNull null
            Moment(
                MomentKind.PLAYER_SCORED,
                "${st.player.name} +${formatPoints(gain)} pts",
                OverlayPage.MATCHUP,
                MomentTone.MINE,
                "player-${m.leagueId}-${st.player.id}-${formatPoints(st.points)}",
            )
        }
    }

    private fun leadChange(prev: OverlayState, cur: OverlayState): Moment? {
        val m = cur.matchup ?: return null
        val opp = m.opponent ?: return null
        val old = prev.matchup?.takeIf { it.leagueId == m.leagueId && it.week == m.week } ?: return null
        val oldOpp = old.opponent ?: return null
        val was = old.me.points.compareTo(oldOpp.points)
        val now = m.me.points.compareTo(opp.points)
        // Only a real flip of the leader; ties in between don't count.
        if (was == 0 || now == 0 || was == now) return null
        val score = "${formatPoints(m.me.points)}–${formatPoints(opp.points)}"
        return if (now > 0) {
            Moment(MomentKind.LEAD_CHANGE, "You took the lead · $score", OverlayPage.MATCHUP, MomentTone.MINE, "lead-${m.leagueId}-me-$score")
        } else {
            Moment(MomentKind.LEAD_CHANGE, "${opp.teamName} took the lead · $score", OverlayPage.MATCHUP, MomentTone.OPPONENT, "lead-${m.leagueId}-opp-$score")
        }
    }

    private fun pickResults(prev: OverlayState, cur: OverlayState): List<Moment> {
        val before = prev.picks.associateBy { it.pick.id }
        return cur.picks.mapNotNull { p ->
            val old = before[p.pick.id] ?: return@mapNotNull null
            if (old.settled || !p.settled) return@mapNotNull null
            val (word, tone) = when (p.status) {
                BetStatus.WON -> "HIT" to MomentTone.MINE
                BetStatus.LOST -> "MISSED" to MomentTone.OPPONENT
                else -> "PUSH" to MomentTone.NEUTRAL
            }
            Moment(MomentKind.PICK_RESULT, "$word · ${p.pick.describe(p.game)}", OverlayPage.PICKS, tone, "pick-${p.pick.id}-${p.status}")
        }
    }

    private fun closeGames(prev: OverlayState, cur: OverlayState): List<Moment> {
        val before = prev.games.associateBy { it.id }
        return cur.games.mapNotNull { g ->
            val old = before[g.id] ?: return@mapNotNull null
            if (CloseGame.isClose(old, prev.closeRule) || !CloseGame.isClose(g, cur.closeRule)) return@mapNotNull null
            val page = if (cur.game?.id == g.id) OverlayPage.MY_GAME else OverlayPage.LEAGUE
            Moment(
                MomentKind.CLOSE_GAME,
                "Close game · ${g.away.abbr} ${g.away.score ?: 0}–${g.home.score ?: 0} ${g.home.abbr} · ${g.shortDetail}",
                page,
                MomentTone.NEUTRAL,
                "close-${g.id}",
            )
        }
    }
}
