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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.ui.components.MessageBox
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.theme.SofaColors

object Routes {
    const val HOME = "home"
    const val SCORES = "scores"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab(Routes.HOME, "Home"),
    Tab(Routes.SCORES, "Scores"),
    Tab(Routes.SETTINGS, "Settings"),
)

@Composable
fun SofaNavHost(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Column(
        Modifier
            .fillMaxSize()
            .background(SofaColors.Background)
            .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        TopBar(current = current, onSelect = { route ->
            nav.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        })
        Box(Modifier.fillMaxSize().padding(top = 16.dp)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { MessageBox("Home") }
                composable(Routes.SCORES) { MessageBox("Scores") }
                composable(Routes.SETTINGS) { MessageBox("Settings") }
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
