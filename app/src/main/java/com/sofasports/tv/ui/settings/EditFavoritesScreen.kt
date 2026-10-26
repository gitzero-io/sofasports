package com.sofasports.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sofasports.tv.domain.Favorites
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.onboarding.FavoritesPicker
import com.sofasports.tv.ui.onboarding.LeaguePicker
import com.sofasports.tv.ui.onboarding.OnboardingViewModel
import com.sofasports.tv.ui.onboarding.PickerHeader
import com.sofasports.tv.ui.onboarding.ids
import com.sofasports.tv.ui.onboarding.leagues

/** Same picker as first run, pre-filled with the current favorites, one tab per followed league. */
@Composable
fun EditFavoritesScreen(onDone: () -> Unit, settingsVm: SettingsViewModel = hiltViewModel(), vm: OnboardingViewModel = hiltViewModel()) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(settings.favoriteTeams) { if (selected == null && settings.onboardingDone) selected = settings.favoriteTeams }
    val current = selected ?: settings.favoriteTeams
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PickerHeader("Favorites", "Teams, fighters and drivers.") {
            SofaButton("Save", onClick = { vm.finish(current, onDone) }, selected = true)
            SofaButton("Cancel", onClick = onDone)
        }
        FavoritesPicker(
            leagues = settings.followedLeagues,
            selected = current,
            onToggle = { selected = Favorites.toggle(current, it) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Which leagues show on Scores, Home and the overlay. */
@Composable
fun EditLeaguesScreen(onDone: () -> Unit, settingsVm: SettingsViewModel = hiltViewModel(), vm: OnboardingViewModel = hiltViewModel()) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var ids by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(settings.followedLeagues) { if (ids == null && settings.onboardingDone) ids = settings.followedLeagues.ids() }
    val current = ids?.leagues() ?: settings.followedLeagues
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PickerHeader("Sports you follow", "The first one you pick opens first on Scores. Keep at least one.") {
            SofaButton(if (current.isEmpty()) "Save" else "Save (${current.size})", onClick = { vm.saveLeagues(current, onDone) }, selected = current.isNotEmpty())
            SofaButton("Cancel", onClick = onDone)
        }
        LeaguePicker(
            selected = current,
            onToggle = { l -> ids = current.ids().let { c -> if (l.id in c) c - l.id else c + l.id } },
            modifier = Modifier.weight(1f),
        )
    }
}
