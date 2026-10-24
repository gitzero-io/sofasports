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
import androidx.compose.runtime.LaunchedEffect
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
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.fantasy.AddEspnLeagueScreen
import com.sofasports.tv.ui.game.GameCenterScreen
import com.sofasports.tv.ui.home.HomeScreen
import com.sofasports.tv.ui.onboarding.OnboardingScreen
import com.sofasports.tv.ui.scores.ScoresScreen
import com.sofasports.tv.ui.settings.EditFavoritesScreen
import com.sofasports.tv.ui.settings.SettingsScreen
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.update.UpdateSection
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
    const val EDIT_FAVORITES = "favorites"
    const val ADD_ESPN = "espn-add"
    fun game(id: String) = "game/$id"
}

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab(Routes.HOME, "Home"),
    Tab(Routes.SCORES, "Scores"),
    Tab(Routes.SETTINGS, "Settings"),
)

@HiltViewModel
class RootViewModel @Inject constructor(store: SettingsStore, private val usage: UsageEvents) : ViewModel() {
    fun screenView(route: String) {
        // Only the route pattern ("game/{gameId}"), never the id.
        usage.track(UsageEvent.ScreenView(route.substringBefore('/')))
        if (route == Routes.GAME) usage.track(UsageEvent.GameOpened)
    }

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
    LaunchedEffect(current) { current?.let(vm::screenView) }
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
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenGame = { nav.navigate(Routes.game(it)) },
                        onOpenScores = { nav.navigate(Routes.SCORES) { launchSingleTop = true } },
                        onAddEspnLeague = { nav.navigate(Routes.ADD_ESPN) },
                    )
                }
                composable(Routes.SCORES) { ScoresScreen(onOpenGame = { nav.navigate(Routes.game(it)) }) }
                composable(Routes.GAME) { GameCenterScreen() }
                composable(Routes.SETTINGS) { SettingsScreen(
                        onEditFavorites = { nav.navigate(Routes.EDIT_FAVORITES) },
                        onAddEspnLeague = { nav.navigate(Routes.ADD_ESPN) },
                        updateSection = { UpdateSection() },
                    ) }
                composable(Routes.ADD_ESPN) { AddEspnLeagueScreen(onDone = { nav.popBackStack() }) }
                composable(Routes.EDIT_FAVORITES) { EditFavoritesScreen(onDone = { nav.popBackStack() }) }
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
