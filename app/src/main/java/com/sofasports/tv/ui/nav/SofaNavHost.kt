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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.repo.TickerRepository
import com.sofasports.tv.domain.TickerItem
import com.sofasports.tv.ui.components.TickerBar
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.fantasy.AddEspnLeagueScreen
import com.sofasports.tv.ui.game.GameCenterScreen
import com.sofasports.tv.ui.home.HomeScreen
import com.sofasports.tv.ui.onboarding.OnboardingScreen
import com.sofasports.tv.ui.picks.AddPickScreen
import com.sofasports.tv.ui.picks.PicksScreen
import com.sofasports.tv.ui.scores.ScoresScreen
import com.sofasports.tv.ui.settings.EditFavoritesScreen
import com.sofasports.tv.ui.settings.SettingsScreen
import com.sofasports.tv.ui.settings.SettingsViewModel
import com.sofasports.tv.ui.overlaysettings.OverlaySettingsScreen
import com.sofasports.tv.ui.overlaysettings.RemoteButtonsScreen
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.update.UpdateSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    const val PICKS = "picks"
    const val ADD_PICK = "picks-add"
    const val OVERLAY_SETTINGS = "overlay-settings"
    const val REMOTE_BUTTONS = "remote-buttons"
    fun game(id: String) = "game/$id"
}

private data class Tab(val route: String, val label: String)

private fun tabs(picks: Boolean) = listOfNotNull(
    Tab(Routes.HOME, "Home"),
    Tab(Routes.SCORES, "Scores"),
    Tab(Routes.PICKS, "Picks").takeIf { picks },
    Tab(Routes.SETTINGS, "Settings"),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class RootViewModel @Inject constructor(
    store: SettingsStore,
    private val usage: UsageEvents,
    config: ConfigRepository,
    ticker: TickerRepository,
) : ViewModel() {
    /** Empty when the in-app bar is off (setting or remote flag), so nothing is drawn or polled for it. */
    val tickerItems: StateFlow<List<TickerItem>> =
        combine(store.settings, config.config) { s, c -> s.tickerInApp && c.features.ticker }
            .flatMapLatest { on -> if (on) ticker.items else flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val picksEnabled: StateFlow<Boolean> =
        config.config.map { it.features.picks }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
    val picksEnabled by vm.picksEnabled.collectAsStateWithLifecycle()
    val tickerItems by vm.tickerItems.collectAsStateWithLifecycle()
    val done = onboardingDone ?: run {
        Box(Modifier.fillMaxSize().background(SofaColors.Background))
        return
    }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    LaunchedEffect(current) { current?.let(vm::screenView) }
    val showChrome = current != Routes.ONBOARDING

    Column(Modifier.fillMaxSize().background(SofaColors.Background).brandGlow()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 8.dp),
        ) {
            if (showChrome) {
                TopBar(current = current, tabs = tabs(picksEnabled), onSelect = { route ->
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
                            onOpenPicks = { nav.navigate(Routes.PICKS) { launchSingleTop = true } },
                        )
                    }
                    composable(Routes.PICKS) { PicksScreen(onAddPick = { nav.navigate(Routes.ADD_PICK) }) }
                    composable(Routes.ADD_PICK) { AddPickScreen(onDone = { nav.popBackStack() }) }
                    composable(Routes.SCORES) { ScoresScreen(onOpenGame = { nav.navigate(Routes.game(it)) }) }
                    composable(Routes.GAME) { GameCenterScreen() }
                    composable(Routes.SETTINGS) { SettingsScreen(
                            onEditFavorites = { nav.navigate(Routes.EDIT_FAVORITES) },
                            onAddEspnLeague = { nav.navigate(Routes.ADD_ESPN) },
                            onCustomizeOverlay = { nav.navigate(Routes.OVERLAY_SETTINGS) },
                            onRemoteButtons = { nav.navigate(Routes.REMOTE_BUTTONS) },
                            updateSection = { UpdateSection() },
                        ) }
                    composable(Routes.OVERLAY_SETTINGS) {
                        val settingsVm: SettingsViewModel = hiltViewModel()
                        OverlaySettingsScreen(onDone = { nav.popBackStack() }, onPosition = settingsVm::setOverlayCorner)
                    }
                    composable(Routes.REMOTE_BUTTONS) { RemoteButtonsScreen(onDone = { nav.popBackStack() }) }
                    composable(Routes.ADD_ESPN) { AddEspnLeagueScreen(onDone = { nav.popBackStack() }) }
                    composable(Routes.EDIT_FAVORITES) { EditFavoritesScreen(onDone = { nav.popBackStack() }) }
                }
            }
        }
        // Extra bottom inset keeps the crawl inside the overscan-safe area (~5% on many TVs).
        if (showChrome) TickerBar(tickerItems, bottomInset = 20.dp)
    }
}

/** A faint blue glow from the top left and red from the top right: the Red vs Blue brand, kept well behind content. */
private fun Modifier.brandGlow(): Modifier = drawBehind {
    val radius = size.width * 0.6f
    drawRect(Brush.radialGradient(listOf(SofaColors.Blue.copy(alpha = 0.10f), Color.Transparent), center = Offset(0f, 0f), radius = radius))
    drawRect(Brush.radialGradient(listOf(SofaColors.Red.copy(alpha = 0.07f), Color.Transparent), center = Offset(size.width, 0f), radius = radius))
}

/**
 * Tabs follow the usual TV pattern: moving onto a tab switches to it, and pressing Up from the
 * page always lands on the page's own tab (not whichever tab had focus last).
 */
@Composable
private fun TopBar(current: String?, tabs: List<Tab>, onSelect: (String) -> Unit) {
    val requesters = remember(tabs) { tabs.associate { it.route to FocusRequester() } }
    val onTab = tabs.any { it.route == current }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("SOFA", style = MaterialTheme.typography.headlineLarge, color = SofaColors.Blue)
        Text("SPORTS", style = MaterialTheme.typography.headlineLarge, color = SofaColors.Red)
        Spacer(Modifier.width(32.dp))
        SpacedRow(
            Modifier
                .focusProperties { onEnter = { requesters[current]?.let { if (!it.requestFocus()) cancelFocusChange() } } }
                .focusGroup(),
        ) {
            tabs.forEach { tab ->
                SofaButton(
                    text = tab.label,
                    onClick = { onSelect(tab.route) },
                    selected = current == tab.route,
                    modifier = Modifier
                        .focusRequester(requesters.getValue(tab.route))
                        // Only switch on focus between tabs; from a detail page (a game) it takes a press.
                        .onFocusChanged { if (it.isFocused && onTab && current != tab.route) onSelect(tab.route) },
                )
            }
        }
    }
}
