package com.sofasports.tv.data.provider

import com.sofasports.tv.domain.FantasyLeague
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyUser
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.Scoreboard

/** Scores for the current NFL week. v0.1: ESPN. */
interface ScoresProvider {
    suspend fun currentScoreboard(): Scoreboard
}

/** Detail for one game: win probability, box score, play-by-play. v0.1: ESPN. */
interface GameStatsProvider {
    suspend fun summary(gameId: String): GameSummary
}

/** Fantasy leagues and matchups. v0.1: Sleeper. */
interface FantasyProvider {
    /** Null when the username doesn't exist. */
    suspend fun findUser(username: String): FantasyUser?
    suspend fun leagues(userId: String): List<FantasyLeague>
    /** The user's matchup in [leagueId] for the current week, or null if they have none. */
    suspend fun currentMatchup(leagueId: String, userId: String): FantasyMatchup?
}
