package com.sofasports.tv.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.domain.NflTeams
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
}

/** First run: pick up to [NflTeams.MAX_FAVORITES] teams. Skippable; editable later in Settings. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    Column(
        Modifier.fillMaxSize().background(SofaColors.Background).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Who do you root for?", style = MaterialTheme.typography.displaySmall, color = SofaColors.TextPrimary)
                Text(
                    "Pick up to ${NflTeams.MAX_FAVORITES}. Your teams show first and are always blue.",
                    style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary,
                )
            }
            SofaButton("Skip", onClick = { vm.finish(emptyList(), onDone) })
            Spacer(Modifier.padding(6.dp))
            SofaButton(
                if (selected.isEmpty()) "Done" else "Done (${selected.size})",
                onClick = { vm.finish(selected, onDone) },
                selected = selected.isNotEmpty(),
            )
        }
        FavoritesPicker(selected = selected, onToggle = { selected = NflTeams.toggle(selected, it) }, modifier = Modifier.weight(1f))
    }
}
