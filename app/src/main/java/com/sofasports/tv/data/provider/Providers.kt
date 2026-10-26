package com.sofasports.tv.data.provider

import com.sofasports.tv.domain.FantasyLeague
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyUser
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.Scoreboard
import com.sofasports.tv.domain.TeamInfo

/** Current scores for one league: this week (football) or today (everything else). ESPN; boxing from TheSportsDB. */
interface ScoresProvider {
    suspend fun scoreboard(league: League): Scoreboard
}

/** Detail for one game: win probability, box score, play-by-play. ESPN team sports only. */
interface GameStatsProvider {
    suspend fun summary(league: League, gameId: String): GameSummary
}

/** Every team in a league, for the favorites picker. */
interface TeamsProvider {
    suspend fun teams(league: League): List<TeamInfo>
}

/** Fantasy leagues and matchups. v0.1: Sleeper. */
interface FantasyProvider {
    /** Null when the username doesn't exist. */
    suspend fun findUser(username: String): FantasyUser?
    suspend fun leagues(userId: String): List<FantasyLeague>
    /** The user's matchup in [leagueId] for the current week, or null if they have none. */
    suspend fun currentMatchup(leagueId: String, userId: String): FantasyMatchup?
}
