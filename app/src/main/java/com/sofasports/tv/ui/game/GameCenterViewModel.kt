package com.sofasports.tv.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.fixture.FixtureUnavailableException
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.provider.GameStatsProvider
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.GameSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GameCenterUi(
    val summary: GameSummary? = null,
    val error: String? = null,
    val favorites: List<String> = emptyList(),
    val pinnedGameId: String? = null,
    val showWinProbability: Boolean = true,
) {
    val loading get() = summary == null && error == null
}

@HiltViewModel
class GameCenterViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val stats: GameStatsProvider,
    private val settings: SettingsStore,
    private val config: ConfigRepository,
) : ViewModel() {

    val gameId: String = checkNotNull(savedState["gameId"])
    private val _ui = MutableStateFlow(GameCenterUi())
    val ui: StateFlow<GameCenterUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { s -> _ui.update { it.copy(favorites = s.favoriteTeams, pinnedGameId = s.pinnedGameId) } }
        }
        viewModelScope.launch {
            config.config.collect { c -> _ui.update { it.copy(showWinProbability = c.features.winProbability) } }
        }
        viewModelScope.launch {
            // Poll while the game is live; a finished or not-started game is fetched once (plus retries on error).
            while (true) {
                val result = runCatching { stats.summary(gameId) }
                result.onSuccess { s -> _ui.update { it.copy(summary = s, error = null) } }
                result.onFailure { e ->
                    val msg = if (e is FixtureUnavailableException) "Game detail isn't in the fixture set. Try HOU @ JAX or LAC @ KC."
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
        settings.setPinnedGame(if (_ui.value.pinnedGameId == gameId) null else gameId)
    }
}
