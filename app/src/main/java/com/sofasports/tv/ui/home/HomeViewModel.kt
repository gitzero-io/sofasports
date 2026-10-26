package com.sofasports.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sofasports.tv.data.config.CloseGameRule
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.picks.PicksRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.data.repo.FantasyRepository
import com.sofasports.tv.data.repo.FantasyState
import com.sofasports.tv.data.repo.LinkResult
import com.sofasports.tv.data.repo.ScoresRepository
import com.sofasports.tv.domain.EventFormat
import com.sofasports.tv.domain.FightCard
import com.sofasports.tv.domain.FightCards
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameSorting
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.Race
import com.sofasports.tv.domain.Perspective
import com.sofasports.tv.data.watch.AppLauncher
import com.sofasports.tv.domain.InstalledApp
import com.sofasports.tv.domain.LiveTvApps
import com.sofasports.tv.domain.WatchOption
import com.sofasports.tv.domain.WatchPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUi(
    val fantasyEnabled: Boolean = true,
    val fantasy: FantasyState = FantasyState(),
    val fantasyPromptSkipped: Boolean = false,
    val myGames: List<Game> = emptyList(),
    /** Live games beyond my teams (close first), or the next kickoffs when nothing is live. */
    val aroundLeague: GameSorting.AroundLeague = GameSorting.AroundLeague(false, emptyList()),
    val favorites: List<String> = emptyList(),
    val closeRule: CloseGameRule = CloseGameRule(),
    val announcement: String? = null,
    val scoresLoading: Boolean = true,
    val gameCenterEnabled: Boolean = true,
    val showOdds: Boolean = false,
    val picks: List<PickProgress> = emptyList(),
    val watchApp: String? = null,
    /** Every polled league's games, for fantasy live projections (the matchup picks its sport's). */
    val games: List<Game> = emptyList(),
    /** Fight cards and races that are live or start within a day. */
    val fightCards: List<FightCard> = emptyList(),
    val races: List<Race> = emptyList(),
    /** More than one league followed: cards get a league tag. */
    val multiLeague: Boolean = false,
    /** Every favorite is an NFL team, so "this week" is the right word. */
    val weeklyOnly: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fantasy: FantasyRepository,
    scores: ScoresRepository,
    private val settings: SettingsStore,
    picks: PicksRepository,
    config: ConfigRepository,
    private val launcher: AppLauncher,
) : ViewModel() {
    private val tvApps = launcher.installedTvApps()

    fun watchOptions(game: Game, watchApp: String?): List<WatchOption> =
        WatchPlanner.options(game.networks.ifEmpty { listOfNotNull(game.broadcast) }, tvApps.firstOrNull { it.pkg == watchApp }?.let {
            InstalledApp(it.pkg, LiveTvApps.known[it.pkg] ?: it.label)
        }, tvApps.map { it.pkg }.toSet())

    fun watch(option: WatchOption) { launcher.launch(option.pkg) }
    fun findOnGoogleTv(game: Game) { launcher.searchGoogleTv(WatchPlanner.searchQuery(game)) }

    val ui: StateFlow<HomeUi> = combine(fantasy.state, scores.state, settings.settings, config.config, picks.progress) { f, s, prefs, cfg, pk ->
        val games = s.games
        val teamGames = games.filter { it.league.format == EventFormat.TEAMS }
        // Favorite fighters' bouts count as "your games" too.
        val mine = GameSorting.sorted(games.filter { Perspective.involvesFavorite(it, prefs.favoriteTeams) }, prefs.favoriteTeams)
        val now = System.currentTimeMillis()
        val soon = { state: GameState, start: Long -> state == GameState.LIVE || (state == GameState.PRE && start - now < DAY_MS) }
        HomeUi(
            fantasyEnabled = cfg.features.fantasy,
            fantasy = f,
            fantasyPromptSkipped = prefs.fantasyPromptSkipped,
            myGames = mine,
            aroundLeague = GameSorting.aroundLeague(teamGames, prefs.favoriteTeams, cfg.closeGame),
            favorites = prefs.favoriteTeams,
            closeRule = cfg.closeGame,
            announcement = cfg.announcement,
            scoresLoading = s.loading,
            gameCenterEnabled = cfg.features.gameCenter,
            showOdds = cfg.features.odds && prefs.showOdds,
            picks = if (cfg.features.picks) pk else emptyList(),
            watchApp = prefs.watchApp,
            games = s.games,
            fightCards = FightCards.group(games, prefs.favoriteTeams).filter { soon(it.state, it.startEpochMs) },
            races = s.races.filter { soon(it.state, it.startEpochMs) }.sortedBy { if (it.involvesAny(prefs.favoriteTeams)) 0 else 1 },
            multiLeague = s.leagues.size > 1,
            weeklyOnly = s.leagues.all { it.weekly },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    suspend fun link(username: String): LinkResult = fantasy.link(username)

    fun selectLeague(id: String) = viewModelScope.launch { fantasy.selectLeague(id) }

    fun skipFantasyPrompt() = viewModelScope.launch { settings.setFantasyPromptSkipped(true) }

    private companion object { const val DAY_MS = 24 * 3_600_000L }
}
