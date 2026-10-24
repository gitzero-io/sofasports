package com.sofasports.tv.data.repo

import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.provider.FantasyProvider
import com.sofasports.tv.di.AppScope
import com.sofasports.tv.domain.FantasyLeague
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.GameState
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class FantasyState(
    val linked: Boolean = false,
    val username: String? = null,
    val leagues: List<FantasyLeague> = emptyList(),
    val selectedLeagueId: String? = null,
    val matchup: FantasyMatchup? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val selectedLeague: FantasyLeague? get() = leagues.firstOrNull { it.id == selectedLeagueId }
}

sealed interface LinkResult {
    data object Ok : LinkResult
    data object NotFound : LinkResult
    data class Failed(val message: String) : LinkResult
}

/** Sleeper account → leagues → my matchup in the selected league, refreshed while games are live. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FantasyRepository @Inject constructor(
    private val provider: FantasyProvider,
    private val settings: SettingsStore,
    private val config: ConfigRepository,
    private val scores: ScoresRepository,
    @AppScope scope: CoroutineScope,
) {
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Leagues survive a league switch so the switcher (and its focus) doesn't vanish while reloading. */
    @Volatile private var cachedLeagues: Pair<String, List<FantasyLeague>>? = null

    private data class Key(val userId: String?, val username: String?, val leagueId: String?, val mode: Any)

    val state: StateFlow<FantasyState> = settings.settings
        .map { Key(it.sleeperUserId, it.sleeperUsername, it.selectedLeagueId, it.dataMode) }
        .distinctUntilChanged()
        .flatMapLatest { key -> if (key.userId == null) flowOf(FantasyState()) else poll(key.userId, key.username, key.leagueId) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), FantasyState())

    suspend fun link(username: String): LinkResult {
        val name = username.trim()
        if (name.isEmpty()) return LinkResult.NotFound
        return runCatching { provider.findUser(name) }.fold(
            onSuccess = { user ->
                if (user == null) LinkResult.NotFound
                else { settings.setSleeper(name, user.userId); LinkResult.Ok }
            },
            onFailure = { LinkResult.Failed(it.message ?: "Network error") },
        )
    }

    suspend fun unlink() = settings.setSleeper(null, null)

    suspend fun selectLeague(id: String) = settings.setSelectedLeague(id)

    fun refreshNow() { refreshSignal.tryEmit(Unit) }

    private fun poll(userId: String, username: String?, selected: String?): Flow<FantasyState> = flow {
        val cached = cachedLeagues?.takeIf { it.first == userId }?.second.orEmpty()
        var state = FantasyState(linked = true, username = username, leagues = cached, selectedLeagueId = selected, loading = true)
        emit(state)
        var leaguesFetchedAt = if (cached.isEmpty()) 0L else System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            if (state.leagues.isEmpty() || now - leaguesFetchedAt > LEAGUES_TTL_MS) {
                runCatching { provider.leagues(userId) }
                    .onSuccess { leaguesFetchedAt = now; cachedLeagues = userId to it; state = state.copy(leagues = it, error = null) }
                    .onFailure { state = state.copy(error = it.message ?: "Couldn't load leagues") }
            }
            val leagueId = state.selectedLeagueId?.takeIf { id -> state.leagues.any { it.id == id } }
                ?: state.leagues.firstOrNull()?.id
            if (leagueId != null && leagueId != selected) {
                // Persisting re-keys the flow; the next collector picks up from here.
                settings.setSelectedLeague(leagueId)
            }
            state = state.copy(selectedLeagueId = leagueId)
            if (leagueId != null) {
                runCatching { provider.currentMatchup(leagueId, userId) }
                    .onSuccess { state = state.copy(matchup = it, error = null) }
                    .onFailure { state = state.copy(error = it.message ?: "Couldn't load matchup") }
            }
            state = state.copy(loading = false)
            emit(state)
            val anyLive = scores.state.value.scoreboard?.games?.any { it.state == GameState.LIVE } == true
            val cfg = config.config.value
            val wait = if (anyLive || state.error != null) cfg.pollIntervalLiveSec * 1000L else cfg.pollIntervalIdleSec * 1000L
            withTimeoutOrNull(wait) { refreshSignal.first() }
        }
    }

    private companion object {
        const val LEAGUES_TTL_MS = 60 * 60 * 1000L
    }
}
