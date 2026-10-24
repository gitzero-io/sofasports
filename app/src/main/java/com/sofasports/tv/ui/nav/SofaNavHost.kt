package com.sofasports.tv.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.ui.components.MessageBox
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.game.GameCenterScreen
import com.sofasports.tv.ui.onboarding.OnboardingScreen
import com.sofasports.tv.ui.scores.ScoresScreen
import com.sofasports.tv.ui.settings.SettingsScreen
import com.sofasports.tv.ui.theme.SofaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SCORES = "scores"
    const val SETTINGS = "settings"
    const val GAME = "game/{gameId}"
    fun game(id: String) = "game/$id"
}

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab(Routes.HOME, "Home"),
    Tab(Routes.SCORES, "Scores"),
    Tab(Routes.SETTINGS, "Settings"),
)

@HiltViewModel
class RootViewModel @Inject constructor(store: SettingsStore) : ViewModel() {
    /** Null until DataStore has been read, so we never flash the wrong start screen. */
    val onboardingDone: StateFlow<Boolean?> =
        store.settings.map { it.onboardingDone }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun SofaNavHost(nav: NavHostController = rememberNavController(), vm: RootViewModel = hiltViewModel()) {
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    val done = onboardingDone ?: run {
        Box(Modifier.fillMaxSize().background(SofaColors.Background))
        return
    }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showChrome = current != Routes.ONBOARDING

    Column(
        Modifier
            .fillMaxSize()
            .background(SofaColors.Background)
            .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        if (showChrome) {
            TopBar(current = current, onSelect = { route ->
                nav.navigate(route) {
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            })
        }
        Box(Modifier.fillMaxSize().padding(top = if (showChrome) 16.dp else 0.dp)) {
            NavHost(nav, startDestination = if (done) Routes.HOME else Routes.ONBOARDING) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onDone = {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    })
                }
                composable(Routes.HOME) { MessageBox("Home") }
                composable(Routes.SCORES) { ScoresScreen(onOpenGame = { nav.navigate(Routes.game(it)) }) }
                composable(Routes.GAME) { GameCenterScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun TopBar(current: String?, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("SOFA", style = MaterialTheme.typography.headlineLarge, color = SofaColors.Blue)
        Text("SPORTS", style = MaterialTheme.typography.headlineLarge, color = SofaColors.Red)
        Spacer(Modifier.width(32.dp))
        SpacedRow {
            tabs.forEach { tab ->
                SofaButton(text = tab.label, onClick = { onSelect(tab.route) }, selected = current == tab.route)
            }
        }
    }
}
