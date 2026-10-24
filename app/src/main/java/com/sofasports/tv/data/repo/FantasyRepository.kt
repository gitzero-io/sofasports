package com.sofasports.tv.data.repo

import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.espnff.EspnFantasyProvider
import com.sofasports.tv.data.espnff.EspnLeagueLink
import com.sofasports.tv.data.espnff.EspnLeagueStore
import com.sofasports.tv.data.provider.FantasyProvider
import com.sofasports.tv.di.AppScope
import com.sofasports.tv.domain.FantasyLeague
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasySource
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val usage: UsageEvents,
    private val espn: EspnFantasyProvider,
    private val espnLeagues: EspnLeagueStore,
    @AppScope scope: CoroutineScope,
) {
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Sleeper leagues survive a league switch so the switcher (and its focus) doesn't vanish while reloading. */
    @Volatile private var cachedLeagues: Pair<String, List<FantasyLeague>>? = null

    private data class Key(
        val userId: String?,
        val username: String?,
        val leagueId: String?,
        val mode: Any,
        val espn: List<EspnLeagueLink>,
    )

    val state: StateFlow<FantasyState> = combine(settings.settings, espnLeagues.links) { s, espn ->
        Key(s.sleeperUserId, s.sleeperUsername, s.selectedLeagueId, s.dataMode, espn)
    }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            if (key.userId == null && key.espn.isEmpty()) flowOf(FantasyState()) else poll(key)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), FantasyState())

    suspend fun link(username: String): LinkResult {
        val name = username.trim()
        if (name.isEmpty()) return LinkResult.NotFound
        return runCatching { provider.findUser(name) }.fold(
            onSuccess = { user ->
                if (user == null) LinkResult.NotFound
                else { settings.setSleeper(name, user.userId); usage.track(UsageEvent.SleeperLinked); LinkResult.Ok }
            },
            onFailure = { LinkResult.Failed(it.message ?: "Network error") },
        )
    }

    suspend fun unlink() = settings.setSleeper(null, null)

    suspend fun selectLeague(id: String) = settings.setSelectedLeague(id)

    fun refreshNow() { refreshSignal.tryEmit(Unit) }

    private fun espnLeague(link: EspnLeagueLink) = FantasyLeague(
        id = link.key,
        name = link.leagueName,
        season = link.season.toString(),
        totalRosters = 0,
        status = null,
        source = FantasySource.ESPN,
    )

    private fun poll(key: Key): Flow<FantasyState> = flow {
        val userId = key.userId
        val espnById = key.espn.associateBy { it.key }
        val cached = cachedLeagues?.takeIf { it.first == userId }?.second.orEmpty()
        var sleeperLeagues = if (userId == null) emptyList() else cached
        fun allLeagues() = sleeperLeagues + key.espn.map(::espnLeague)
        var state = FantasyState(linked = true, username = key.username, leagues = allLeagues(), selectedLeagueId = key.leagueId, loading = true)
        emit(state)
        var leaguesFetchedAt = if (cached.isEmpty()) 0L else System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            if (userId != null && (sleeperLeagues.isEmpty() || now - leaguesFetchedAt > LEAGUES_TTL_MS)) {
                runCatching { provider.leagues(userId) }
                    .onSuccess {
                        if (cachedLeagues?.first != userId) usage.track(UsageEvent.LeagueCount(it.size))
                        leaguesFetchedAt = now; cachedLeagues = userId to it; sleeperLeagues = it
                        state = state.copy(error = null)
                    }
                    .onFailure { state = state.copy(error = it.message ?: "Couldn't load Sleeper leagues") }
            }
            state = state.copy(leagues = allLeagues())
            val leagueId = state.selectedLeagueId?.takeIf { id -> state.leagues.any { it.id == id } }
                ?: state.leagues.firstOrNull()?.id
            if (leagueId != null && leagueId != key.leagueId) {
                // Persisting re-keys the flow; the next collector picks up from here.
                settings.setSelectedLeague(leagueId)
            }
            state = state.copy(selectedLeagueId = leagueId)
            if (leagueId != null) {
                val espnLink = espnById[leagueId]
                runCatching {
                    when {
                        espnLink != null -> espn.matchup(espnLink)
                        userId != null -> provider.currentMatchup(leagueId, userId)
                        else -> null
                    }
                }
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
