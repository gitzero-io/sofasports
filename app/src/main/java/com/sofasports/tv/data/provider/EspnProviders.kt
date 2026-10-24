package com.sofasports.tv.data.provider

import com.sofasports.tv.data.espn.EspnParser
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.Scoreboard
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EspnScoresProvider @Inject constructor(
    private val http: HttpClient,
    private val bases: ApiBases,
) : ScoresProvider {
    override suspend fun currentScoreboard(): Scoreboard {
        val body = http.get("${bases.espnNflBase}/scoreboard").bodyAsText()
        return withContext(Dispatchers.Default) { EspnParser.parseScoreboard(body) }
    }
}

@Singleton
class EspnGameStatsProvider @Inject constructor(
    private val http: HttpClient,
    private val bases: ApiBases,
) : GameStatsProvider {
    override suspend fun summary(gameId: String): GameSummary {
        val body = http.get("${bases.espnNflBase}/summary") { parameter("event", gameId) }.bodyAsText()
        return withContext(Dispatchers.Default) { EspnParser.parseSummary(body) }
    }
}
