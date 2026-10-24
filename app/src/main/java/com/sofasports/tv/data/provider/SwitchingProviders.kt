package com.sofasports.tv.data.provider

import com.sofasports.tv.BuildConfig
import com.sofasports.tv.data.fixture.FixtureFantasyProvider
import com.sofasports.tv.data.fixture.FixtureGameStatsProvider
import com.sofasports.tv.data.fixture.FixtureScoresProvider
import com.sofasports.tv.data.prefs.DataMode
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.domain.FantasyLeague
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyUser
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.Scoreboard
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the debug "data source" toggle. Release builds are always live. */
@Singleton
class DataModeSource @Inject constructor(private val settings: SettingsStore) {
    suspend fun useFixtures(): Boolean = BuildConfig.DEBUG && settings.current().dataMode == DataMode.FIXTURES
}

@Singleton
class SwitchingScoresProvider @Inject constructor(
    private val mode: DataModeSource,
    private val live: EspnScoresProvider,
    private val fixture: FixtureScoresProvider,
) : ScoresProvider {
    override suspend fun currentScoreboard(): Scoreboard =
        (if (mode.useFixtures()) fixture else live).currentScoreboard()
}

@Singleton
class SwitchingGameStatsProvider @Inject constructor(
    private val mode: DataModeSource,
    private val live: EspnGameStatsProvider,
    private val fixture: FixtureGameStatsProvider,
) : GameStatsProvider {
    override suspend fun summary(gameId: String): GameSummary =
        (if (mode.useFixtures()) fixture else live).summary(gameId)
}

@Singleton
class SwitchingFantasyProvider @Inject constructor(
    private val mode: DataModeSource,
    private val live: SleeperFantasyProvider,
    private val fixture: FixtureFantasyProvider,
) : FantasyProvider {
    private suspend fun pick(): FantasyProvider = if (mode.useFixtures()) fixture else live
    override suspend fun findUser(username: String): FantasyUser? = pick().findUser(username)
    override suspend fun leagues(userId: String): List<FantasyLeague> = pick().leagues(userId)
    override suspend fun currentMatchup(leagueId: String, userId: String): FantasyMatchup? =
        pick().currentMatchup(leagueId, userId)
}
