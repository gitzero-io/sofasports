package com.sofasports.tv.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sofasports.tv.data.config.CloseGameRule
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.repo.ScoresRepository
import com.sofasports.tv.domain.EventFormat
import com.sofasports.tv.domain.FightCard
import com.sofasports.tv.domain.FightCards
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameSorting
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.Race
import com.sofasports.tv.domain.ScoreSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ScoresUi(
    /** Followed leagues, for the filter row. */
    val leagues: List<League> = emptyList(),
    /** Null = all leagues. */
    val selected: League? = null,
    val sections: List<Pair<ScoreSection, List<Game>>> = emptyList(),
    val fightCards: List<FightCard> = emptyList(),
    val races: List<Race> = emptyList(),
    val favorites: List<String> = emptyList(),
    val closeRule: CloseGameRule = CloseGameRule(),
    /** "NFL · Week 8", "NBA", "All sports". */
    val title: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val updatedAtMs: Long = 0L,
    val gameCenterEnabled: Boolean = true,
    val showOdds: Boolean = false,
) {
    val isEmpty: Boolean get() = sections.isEmpty() && fightCards.isEmpty() && races.isEmpty()
    val showLeagueTags: Boolean get() = selected == null && leagues.size > 1
}

@HiltViewModel
class ScoresViewModel @Inject constructor(
    private val scores: ScoresRepository,
    settings: SettingsStore,
    config: ConfigRepository,
) : ViewModel() {
    /** The user's pick in the filter row; null until they choose (then the first followed league is shown). */
    private val choice = MutableStateFlow<Choice?>(null)

    private sealed interface Choice {
        data object All : Choice
        data class One(val league: League) : Choice
    }

    val ui: StateFlow<ScoresUi> = combine(scores.state, settings.settings, config.config, choice) { s, prefs, cfg, pick ->
        val leagues = s.leagues.filter { it in prefs.followedLeagues }.ifEmpty { s.leagues }
        val selected: League? = when (pick) {
            Choice.All -> null
            is Choice.One -> pick.league.takeIf { it in leagues } ?: leagues.firstOrNull()
            null -> leagues.firstOrNull()
        }
        val shown = if (selected == null) leagues else listOf(selected)
        val games = shown.flatMap { s.board(it)?.games.orEmpty() }
        val boards = shown.mapNotNull { s.boards[it] }
        ScoresUi(
            leagues = leagues,
            selected = selected,
            sections = GameSorting.sections(games.filter { it.league.format == EventFormat.TEAMS }, prefs.favoriteTeams),
            fightCards = FightCards.group(games),
            races = shown.flatMap { s.board(it)?.races.orEmpty() }.sortedWith(compareBy<Race> { raceRank(it.state) }.thenBy { it.startEpochMs }),
            favorites = prefs.favoriteTeams,
            closeRule = cfg.closeGame,
            title = when {
                selected == null -> "All sports"
                selected.weekly -> s.board(selected)?.week?.let { "${selected.fullName} · Week $it" } ?: selected.fullName
                else -> selected.fullName
            },
            loading = boards.none { it.scoreboard != null || it.error != null },
            error = boards.firstNotNullOfOrNull { it.error },
            updatedAtMs = boards.maxOfOrNull { it.updatedAtMs } ?: 0L,
            gameCenterEnabled = cfg.features.gameCenter,
            showOdds = cfg.features.odds && prefs.showOdds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoresUi())

    fun select(league: League?) {
        choice.value = if (league == null) Choice.All else Choice.One(league)
    }

    fun refresh() = scores.refreshNow()

    private fun raceRank(s: GameState) = when (s) {
        GameState.LIVE -> 0
        GameState.PRE -> 1
        else -> 2
    }
}
