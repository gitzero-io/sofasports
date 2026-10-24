package com.sofasports.tv.di

import com.sofasports.tv.BuildConfig
import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.fixture.AssetFixtureSource
import com.sofasports.tv.data.fixture.FixtureSource
import com.sofasports.tv.data.provider.ApiBases
import com.sofasports.tv.data.provider.FantasyProvider
import com.sofasports.tv.data.provider.GameStatsProvider
import com.sofasports.tv.data.provider.PlayerDirectory
import com.sofasports.tv.data.provider.ScoresProvider
import com.sofasports.tv.data.provider.SleeperPlayerCache
import com.sofasports.tv.data.provider.SwitchingFantasyProvider
import com.sofasports.tv.data.provider.SwitchingGameStatsProvider
import com.sofasports.tv.data.provider.SwitchingScoresProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {
    @Binds abstract fun scores(impl: SwitchingScoresProvider): ScoresProvider
    @Binds abstract fun gameStats(impl: SwitchingGameStatsProvider): GameStatsProvider
    @Binds abstract fun fantasy(impl: SwitchingFantasyProvider): FantasyProvider
    @Binds abstract fun players(impl: SleeperPlayerCache): PlayerDirectory
    @Binds abstract fun fixtures(impl: AssetFixtureSource): FixtureSource
    @Binds abstract fun apiBases(impl: ConfigRepository): ApiBases
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(SofaJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        defaultRequest { header("User-Agent", "SofaSports/${BuildConfig.VERSION_NAME} (Android TV)") }
        expectSuccess = false
    }
}
