package com.sofasports.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.NflTeams
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.onboarding.FavoritesPicker
import com.sofasports.tv.ui.onboarding.OnboardingViewModel
import com.sofasports.tv.ui.theme.SofaColors

/** Same picker as first run, pre-filled with the current favorites. */
@Composable
fun EditFavoritesScreen(onDone: () -> Unit, settingsVm: SettingsViewModel = hiltViewModel(), vm: OnboardingViewModel = hiltViewModel()) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(settings.favoriteTeams) { if (selected == null && settings.onboardingDone) selected = settings.favoriteTeams }
    val current = selected ?: settings.favoriteTeams
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Favorite teams (up to ${NflTeams.MAX_FAVORITES})", style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary, modifier = Modifier.weight(1f))
            SofaButton("Cancel", onClick = onDone)
            Spacer(Modifier.width(12.dp))
            SofaButton("Save", onClick = { vm.finish(current, onDone) }, selected = true)
        }
        FavoritesPicker(selected = current, onToggle = { selected = NflTeams.toggle(current, it) }, modifier = Modifier.weight(1f))
    }
}
