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
import com.sofasports.tv.domain.CloseGame
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameSorting
import com.sofasports.tv.domain.Perspective
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
    val closeGames: List<Game> = emptyList(),
    val favorites: List<String> = emptyList(),
    val closeRule: CloseGameRule = CloseGameRule(),
    val announcement: String? = null,
    val scoresLoading: Boolean = true,
    val gameCenterEnabled: Boolean = true,
    val showOdds: Boolean = false,
    val picks: List<PickProgress> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fantasy: FantasyRepository,
    scores: ScoresRepository,
    private val settings: SettingsStore,
    picks: PicksRepository,
    config: ConfigRepository,
) : ViewModel() {

    val ui: StateFlow<HomeUi> = combine(fantasy.state, scores.state, settings.settings, config.config, picks.progress) { f, s, prefs, cfg, pk ->
        val games = s.scoreboard?.games.orEmpty()
        val mine = GameSorting.sorted(games.filter { Perspective.involvesFavorite(it, prefs.favoriteTeams) }, prefs.favoriteTeams)
        HomeUi(
            fantasyEnabled = cfg.features.fantasy,
            fantasy = f,
            fantasyPromptSkipped = prefs.fantasyPromptSkipped,
            myGames = mine,
            closeGames = games.filter { CloseGame.isClose(it, cfg.closeGame) && it !in mine },
            favorites = prefs.favoriteTeams,
            closeRule = cfg.closeGame,
            announcement = cfg.announcement,
            scoresLoading = s.loading,
            gameCenterEnabled = cfg.features.gameCenter,
            showOdds = cfg.features.odds && prefs.showOdds,
            picks = if (cfg.features.picks) pk else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    suspend fun link(username: String): LinkResult = fantasy.link(username)

    fun selectLeague(id: String) = viewModelScope.launch { fantasy.selectLeague(id) }

    fun skipFantasyPrompt() = viewModelScope.launch { settings.setFantasyPromptSkipped(true) }
}
