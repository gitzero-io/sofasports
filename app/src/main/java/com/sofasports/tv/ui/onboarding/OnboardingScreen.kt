package com.sofasports.tv.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.domain.Favorites
import com.sofasports.tv.domain.League
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.theme.SofaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val store: SettingsStore, private val usage: UsageEvents) : ViewModel() {
    fun finish(favorites: List<String>, then: () -> Unit) = viewModelScope.launch {
        store.setFavorites(favorites)
        usage.track(UsageEvent.FavoritesSaved(favorites.size))
        then()
    }

    fun finish(leagues: List<League>, favorites: List<String>, then: () -> Unit) = viewModelScope.launch {
        // Favorites only make sense in leagues the user follows.
        val kept = favorites.filter { key -> leagues.any { it.id == key.substringBefore(':') } }
        store.finishOnboarding(leagues, kept)
        usage.track(UsageEvent.LeaguesSaved(leagues.size))
        usage.track(UsageEvent.FavoritesSaved(kept.size))
        then()
    }

    fun saveLeagues(leagues: List<League>, then: () -> Unit) = viewModelScope.launch {
        store.setFollowedLeagues(leagues)
        usage.track(UsageEvent.LeaguesSaved(leagues.size))
        then()
    }
}

/** Saveable form of a league list (enum names). */
internal fun List<League>.ids() = map { it.id }
internal fun List<String>.leagues() = mapNotNull(League::fromId)

/**
 * First run: 1) which sports and leagues to follow, 2) favorite teams in those leagues.
 * Both are skippable and editable later in Settings.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var leagueIds by rememberSaveable { mutableStateOf(League.DEFAULT_FOLLOWED.ids()) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val leagues = leagueIds.leagues()
    val hasTeams = leagues.any { it.hasTeams }
    Column(
        Modifier.fillMaxSize().background(SofaColors.Background).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PickerHeader(
            if (step == 0) "What do you watch?" else "Who do you root for?",
            if (step == 0) "Pick the leagues you want scores for. The first one you pick opens first on Scores."
            else "Pick up to ${Favorites.MAX_PER_LEAGUE} per league. Your teams show first and are always blue.",
        ) {
            if (step == 0) {
                SofaButton(
                    if (leagues.isEmpty()) "Next" else "Next (${leagues.size})",
                    onClick = { if (hasTeams) step = 1 else vm.finish(leagues, emptyList(), onDone) },
                    selected = leagues.isNotEmpty(),
                )
            } else {
                SofaButton(
                    if (selected.isEmpty()) "Done" else "Done (${selected.size})",
                    onClick = { vm.finish(leagues, selected, onDone) },
                    selected = selected.isNotEmpty(),
                )
                SofaButton("Skip", onClick = { vm.finish(leagues, emptyList(), onDone) })
                SofaButton("Back", onClick = { step = 0 })
            }
        }
        if (step == 0) {
            LeaguePicker(
                selected = leagues,
                onToggle = { l -> leagueIds = if (l.id in leagueIds) leagueIds - l.id else leagueIds + l.id },
                modifier = Modifier.weight(1f),
            )
        } else {
            FavoritesPicker(leagues = leagues, selected = selected, onToggle = { selected = Favorites.toggle(selected, it) }, modifier = Modifier.weight(1f))
        }
    }
}
