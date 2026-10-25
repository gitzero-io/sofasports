package com.sofasports.tv.overlay

import com.sofasports.tv.domain.BetStatus
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyPlayer
import com.sofasports.tv.domain.FantasyStarter
import com.sofasports.tv.domain.FantasyTeam
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.PickBook
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.domain.PickType
import com.sofasports.tv.domain.TeamSide
import com.sofasports.tv.domain.TrackedPick

internal object T {
    const val NOW = 1_000_000_000_000L

    fun game(
        id: String, away: String, home: String, state: GameState = GameState.LIVE,
        awayScore: Int = 0, homeScore: Int = 0, period: Int = 2, start: Long = NOW - 3_600_000,
    ) = Game(
        id, start, state,
        TeamSide(home, home, home, home, null, homeScore, null, true),
        TeamSide(away, away, away, away, null, awayScore, null, false),
        period, "", "Q$period", null,
    )

    fun starter(id: String, name: String, team: String, pts: Double) = FantasyStarter(FantasyPlayer(id, name, "WR", team), pts)

    fun matchup(me: Double, opp: Double, starters: List<FantasyStarter> = emptyList(), league: String = "L1") = FantasyMatchup(
        league, "League", 7,
        FantasyTeam(1, "me", "My Team", me, starters),
        FantasyTeam(2, "opp", "Their Team", opp, emptyList()),
    )

    fun pick(id: String, status: BetStatus) = PickProgress(
        TrackedPick(id, PickBook.DRAFTKINGS, PickType.MONEYLINE, "HOU"), null, null, status,
    )
}
