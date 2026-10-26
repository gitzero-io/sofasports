package com.sofasports.tv.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.fixture.FixtureUnavailableException
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.provider.GameStatsProvider
import com.sofasports.tv.data.watch.AppLauncher
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameRef
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.InstalledApp
import com.sofasports.tv.domain.LiveTvApps
import com.sofasports.tv.domain.WatchOption
import com.sofasports.tv.domain.WatchPlanner
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GameCenterUi(
    val summary: GameSummary? = null,
    val error: String? = null,
    val favorites: List<String> = emptyList(),
    /** Pinned [GameRef] key. */
    val pinnedGameId: String? = null,
    val showWinProbability: Boolean = true,
    val showOdds: Boolean = false,
    val watchApp: String? = null,
) {
    val loading get() = summary == null && error == null
}

@HiltViewModel
class GameCenterViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val stats: GameStatsProvider,
    private val settings: SettingsStore,
    private val config: ConfigRepository,
    private val usage: UsageEvents,
    private val launcher: AppLauncher,
) : ViewModel() {
    private val tvApps = launcher.installedTvApps()

    /** Where this game can be opened, best first. */
    fun watchOptions(game: Game, watchApp: String?): List<WatchOption> =
        WatchPlanner.options(game.networks.ifEmpty { listOfNotNull(game.broadcast) }, tvApps.firstOrNull { it.pkg == watchApp }?.let {
            InstalledApp(it.pkg, LiveTvApps.known[it.pkg] ?: it.label)
        }, tvApps.map { it.pkg }.toSet())

    fun watch(option: WatchOption) {
        usage.track(UsageEvent.WatchOpened(option.label))
        launcher.launch(option.pkg)
    }

    fun findOnGoogleTv(game: Game) {
        usage.track(UsageEvent.WatchOpened("google_tv_search"))
        launcher.searchGoogleTv(WatchPlanner.searchQuery(game))
    }

    /** The route carries a [GameRef] key; a bare id (an old deep link) is an NFL game. */
    val game: GameRef = GameRef.parse(savedState["gameId"]) ?: GameRef(League.NFL, checkNotNull(savedState["gameId"]))
    private val _ui = MutableStateFlow(GameCenterUi())
    val ui: StateFlow<GameCenterUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings.settings, config.config) { s, c -> s to c }.collect { (s, c) ->
                _ui.update { it.copy(favorites = s.favoriteTeams, pinnedGameId = s.pinnedGameId, showOdds = c.features.odds && s.showOdds, watchApp = s.watchApp) }
            }
        }
        viewModelScope.launch {
            config.config.collect { c -> _ui.update { it.copy(showWinProbability = c.features.winProbability) } }
        }
        viewModelScope.launch {
            // Poll while the game is live; a finished or not-started game is fetched once (plus retries on error).
            while (true) {
                val result = runCatching { stats.summary(game.league, game.id) }
                result.onSuccess { s -> _ui.update { it.copy(summary = s, error = null) } }
                result.onFailure { e ->
                    val msg = if (e is FixtureUnavailableException) "Game detail isn't in the fixture set. Try HOU @ JAX, LAC @ KC or LAL @ UTAH."
                    else "Couldn't load game detail. Retrying…"
                    _ui.update { it.copy(error = if (it.summary == null) msg else null) }
                    if (e is FixtureUnavailableException) return@launch
                }
                val live = result.getOrNull()?.game?.state == GameState.LIVE
                if (result.isSuccess && !live) return@launch
                delay(config.config.value.pollIntervalLiveSec * 1000L)
            }
        }
    }

    fun togglePin() = viewModelScope.launch {
        val pin = _ui.value.pinnedGameId != game.key
        settings.setPinnedGame(if (pin) game else null)
        if (pin) usage.track(UsageEvent.GamePinned)
    }
}
