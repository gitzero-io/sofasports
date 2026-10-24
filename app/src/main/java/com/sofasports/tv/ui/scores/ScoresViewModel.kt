package com.sofasports.tv.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sofasports.tv.data.config.CloseGameRule
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.repo.ScoresRepository
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameSorting
import com.sofasports.tv.domain.ScoreSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ScoresUi(
    val sections: List<Pair<ScoreSection, List<Game>>> = emptyList(),
    val favorites: List<String> = emptyList(),
    val closeRule: CloseGameRule = CloseGameRule(),
    val week: Int? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val updatedAtMs: Long = 0L,
    val gameCenterEnabled: Boolean = true,
)

@HiltViewModel
class ScoresViewModel @Inject constructor(
    private val scores: ScoresRepository,
    settings: SettingsStore,
    config: ConfigRepository,
) : ViewModel() {
    val ui: StateFlow<ScoresUi> = combine(scores.state, settings.settings, config.config) { s, prefs, cfg ->
        ScoresUi(
            sections = GameSorting.sections(s.scoreboard?.games.orEmpty(), prefs.favoriteTeams),
            favorites = prefs.favoriteTeams,
            closeRule = cfg.closeGame,
            week = s.scoreboard?.week,
            loading = s.loading,
            error = s.error,
            updatedAtMs = s.updatedAtMs,
            gameCenterEnabled = cfg.features.gameCenter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoresUi())

    fun refresh() = scores.refreshNow()
}
