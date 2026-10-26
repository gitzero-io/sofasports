package com.sofasports.tv.overlay

import com.sofasports.tv.data.config.CloseGameRule
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.picks.PicksRepository
import com.sofasports.tv.data.prefs.OverlayPrefs
import com.sofasports.tv.data.prefs.OverlayTicker
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.repo.TickerRepository
import com.sofasports.tv.domain.TickerItem
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.data.repo.FantasyRepository
import com.sofasports.tv.data.repo.ScoresRepository
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameSorting
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.Race
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class OverlayState(
    val matchup: FantasyMatchup? = null,
    val game: Game? = null,
    /** Shown on the "my game" page when there's no [game]: a pinned race, or a favorite driver's. */
    val race: Race? = null,
    /** True when [game] (or [race]) is the user's pin (vs. a favorite we picked automatically). */
    val gamePinned: Boolean = false,
    /** Favorite teams, so the game is coloured from "my" side. */
    val favorites: List<String> = emptyList(),
    val closeRule: CloseGameRule = CloseGameRule(),
    val showOdds: Boolean = false,
    val picks: List<PickProgress> = emptyList(),
    val tickerMode: OverlayTicker = OverlayTicker.OFF,
    val ticker: List<TickerItem> = emptyList(),
    /** This week's scoreboard, for "Around the league", "playing now" and close-game moments. */
    val games: List<Game> = emptyList(),
    val prefs: OverlayPrefs = OverlayPrefs(),
) {
    val cornerTicker get() = (tickerMode == OverlayTicker.CORNER || tickerMode == OverlayTicker.BOTH) && ticker.isNotEmpty()
    val barTicker get() = (tickerMode == OverlayTicker.BAR || tickerMode == OverlayTicker.BOTH) && ticker.isNotEmpty()
}

/** What the overlay can show: the selected fantasy matchup, one game, picks, the scoreboard and the ticker. */
@Singleton
class OverlayFeed @Inject constructor(
    private val fantasy: FantasyRepository,
    private val scores: ScoresRepository,
    private val settings: SettingsStore,
    private val config: ConfigRepository,
    private val picks: PicksRepository,
    private val ticker: TickerRepository,
) {
    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()
    private var job: Job? = null

    /** Collecting here also keeps the shared pollers running while the overlay is up. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            val tickerMode = combine(settings.settings, config.config) { s, c -> if (c.features.ticker) s.overlayTicker else OverlayTicker.OFF }
                .distinctUntilChanged()
            // Only subscribe to headlines when the overlay actually shows them.
            val tickerFeed = tickerMode.flatMapLatest { m ->
                if (m == OverlayTicker.OFF) flowOf(m to emptyList()) else ticker.items.map { m to it }
            }
            combine(fantasy.state, scores.state, settings.settings, config.config, combine(picks.progress, tickerFeed) { a, b -> a to b }) { f, s, prefs, cfg, (pk, tk) ->
                val games = s.games
                val pinned = prefs.pinnedGame?.let(s::game)
                val pinnedRace = prefs.pinnedGame?.let(s::race)
                val game = pinned ?: if (pinnedRace == null) pickGame(games, prefs.favoriteTeams) else null
                OverlayState(
                    matchup = f.matchup.takeIf { cfg.features.fantasy },
                    game = game,
                    race = pinnedRace ?: if (game == null) pickRace(s.races, prefs.favoriteTeams) else null,
                    gamePinned = pinned != null || pinnedRace != null,
                    favorites = prefs.favoriteTeams,
                    closeRule = cfg.closeGame,
                    showOdds = cfg.features.odds && prefs.showOdds,
                    picks = if (cfg.features.picks) pk else emptyList(),
                    tickerMode = tk.first,
                    ticker = tk.second,
                    games = games,
                    prefs = prefs.overlay,
                )
            }.collect { _state.value = it }
        }
    }

    companion object {
        /**
         * No pinned game: show my highest-priority favorite's game (any league) if it's live or
         * today (within 12 h); otherwise nothing, so the overlay stays small.
         */
        fun pickGame(games: List<Game>, favorites: List<String>, nowMs: Long = System.currentTimeMillis()): Game? =
            GameSorting.sections(games, favorites).firstOrNull()?.second?.firstOrNull { g ->
                favorites.any { g.involves(it) } && (
                    g.state == GameState.LIVE ||
                        (g.state == GameState.PRE && g.startEpochMs - nowMs < 12 * 3_600_000L) ||
                        (g.state == GameState.FINAL && nowMs - g.startEpochMs < 6 * 3_600_000L)
                    )
            }

        /** No game to show: a favorite driver's race weekend while a session is live or one starts within 12 h. */
        fun pickRace(races: List<Race>, favorites: List<String>, nowMs: Long = System.currentTimeMillis()): Race? =
            races.filter { it.involvesAny(favorites) }.firstOrNull { r ->
                r.state == GameState.LIVE || (r.state == GameState.PRE && r.startEpochMs - nowMs in 0..12 * 3_600_000L)
            }
    }
}
