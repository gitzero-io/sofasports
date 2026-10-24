package com.sofasports.tv.data.repo

import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.provider.ScoresProvider
import com.sofasports.tv.di.AppScope
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.PollPolicy
import com.sofasports.tv.domain.Scoreboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class ScoresState(
    val scoreboard: Scoreboard? = null,
    val updatedAtMs: Long = 0L,
    /** Set when the last fetch failed; [scoreboard] keeps the last good data. */
    val error: String? = null,
) {
    val loading: Boolean get() = scoreboard == null && error == null
    fun game(id: String): Game? = scoreboard?.games?.firstOrNull { it.id == id }
}

/**
 * Single shared scoreboard poller for the whole app (screens + overlay).
 * Polls only while someone is watching, at the cadence [PollPolicy] picks (30 s while live).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ScoresRepository @Inject constructor(
    private val provider: ScoresProvider,
    private val config: ConfigRepository,
    settings: SettingsStore,
    @AppScope scope: CoroutineScope,
) {
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<ScoresState> = settings.settings
        .map { it.dataMode }
        .distinctUntilChanged()
        .flatMapLatest { poll() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ScoresState())

    fun refreshNow() {
        refreshSignal.tryEmit(Unit)
    }

    private fun poll(): Flow<ScoresState> = flow {
        var last = ScoresState()
        emit(last)
        while (true) {
            last = runCatching { provider.currentScoreboard() }.fold(
                onSuccess = { ScoresState(it, System.currentTimeMillis(), null) },
                onFailure = { last.copy(error = it.message ?: "Couldn't load scores") },
            )
            emit(last)
            val cfg = config.config.value
            val wait = PollPolicy.nextDelayMs(
                last.scoreboard?.games, System.currentTimeMillis(),
                cfg.pollIntervalLiveSec, cfg.pollIntervalIdleSec, lastFetchFailed = last.error != null,
            )
            withTimeoutOrNull(wait) { refreshSignal.first() }
        }
    }
}
