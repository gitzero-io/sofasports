package com.sofasports.tv.overlay

import com.sofasports.tv.data.config.CloseGameRule
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.picks.PicksRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.data.repo.FantasyRepository
import com.sofasports.tv.data.repo.ScoresRepository
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameSorting
import com.sofasports.tv.domain.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class OverlayState(
    val matchup: FantasyMatchup? = null,
    val game: Game? = null,
    /** True when [game] is the user's pinned game (vs. a favorite we picked automatically). */
    val gamePinned: Boolean = false,
    /** Favorite teams, so the game is coloured from "my" side. */
    val favorites: List<String> = emptyList(),
    val closeRule: CloseGameRule = CloseGameRule(),
    val showOdds: Boolean = false,
    val picks: List<PickProgress> = emptyList(),
)

/** What the corner overlay shows: the selected Sleeper matchup and one game. */
@Singleton
class OverlayFeed @Inject constructor(
    private val fantasy: FantasyRepository,
    private val scores: ScoresRepository,
    private val settings: SettingsStore,
    private val config: ConfigRepository,
    private val picks: PicksRepository,
) {
    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()
    private var job: Job? = null

    /** Collecting here also keeps the shared pollers running while the overlay is up. */
    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            combine(fantasy.state, scores.state, settings.settings, config.config, picks.progress) { f, s, prefs, cfg, pk ->
                val games = s.scoreboard?.games.orEmpty()
                val pinned = prefs.pinnedGameId?.let { id -> games.firstOrNull { it.id == id } }
                OverlayState(
                    matchup = f.matchup.takeIf { cfg.features.fantasy },
                    game = pinned ?: pickGame(games, prefs.favoriteTeams),
                    gamePinned = pinned != null,
                    favorites = prefs.favoriteTeams,
                    closeRule = cfg.closeGame,
                    showOdds = cfg.features.odds && prefs.showOdds,
                    picks = if (cfg.features.picks) pk else emptyList(),
                )
            }.collect { _state.value = it }
        }
    }

    companion object {
        /**
         * No pinned game: show my first favorite's game if it's live or today (within 12 h);
         * otherwise nothing, so the overlay stays small.
         */
        fun pickGame(games: List<Game>, favorites: List<String>, nowMs: Long = System.currentTimeMillis()): Game? =
            GameSorting.sections(games, favorites).firstOrNull()?.second?.firstOrNull { g ->
                favorites.any { g.involves(it) } && (
                    g.state == GameState.LIVE ||
                        (g.state == GameState.PRE && g.startEpochMs - nowMs < 12 * 3_600_000L) ||
                        (g.state == GameState.FINAL && nowMs - g.startEpochMs < 6 * 3_600_000L)
                    )
            }
    }
}
