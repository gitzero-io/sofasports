package com.sofasports.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.BuildConfig
import com.sofasports.tv.data.prefs.DataMode
import com.sofasports.tv.data.prefs.OverlayCorner
import com.sofasports.tv.data.prefs.OverlayTicker
import com.sofasports.tv.domain.NflTeams
import com.sofasports.tv.remote.RemoteKeys
import com.sofasports.tv.ui.components.QrCode
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.TeamLogo
import com.sofasports.tv.ui.fantasy.SleeperLinkForm
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.focusableRow

@Composable
fun SettingsScreen(
    onEditFavorites: () -> Unit,
    onAddEspnLeague: () -> Unit,
    onCustomizeOverlay: () -> Unit = {},
    onRemoteButtons: () -> Unit = {},
    updateSection: @Composable () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val canDraw by vm.canDrawOverlay.collectAsStateWithLifecycle()
    val espnLinks by vm.espnLinks.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.recheckPermission() }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(32.dp), contentPadding = PaddingValues(bottom = 64.dp)) {
        item(key = "sleeper") {
            SettingsSection("Sleeper") {
                val name = settings.sleeperUsername
                if (name != null && settings.sleeperUserId != null) {
                    SpacedRow {
                        Text("Linked as ", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary)
                        Text(name, style = MaterialTheme.typography.titleLarge, color = SofaColors.BlueBright)
                        SofaButton("Unlink", onClick = vm::unlinkSleeper)
                    }
                    Text("To switch accounts, unlink and enter a different username.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextMuted)
                } else {
                    SleeperLinkForm(onLink = vm::linkSleeper)
                }
            }
        }
        item(key = "espn") {
            SettingsSection("ESPN Fantasy") {
                espnLinks.forEach { l ->
                    SpacedRow {
                        Text(l.leagueName, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextPrimary)
                        Text("· ${l.teamName}" + if (l.isPrivate) " · private" else "", style = MaterialTheme.typography.titleMedium, color = SofaColors.BlueBright)
                        SofaButton("Remove", onClick = { vm.removeEspnLeague(l.key) })
                    }
                }
                if (espnLinks.isEmpty()) {
                    Text("Public leagues need only the league ID. Private leagues also need two ESPN cookies.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
                }
                SofaButton("Add ESPN league", onClick = onAddEspnLeague)
            }
        }
        item(key = "favorites") {
            SettingsSection("Favorite teams") {
                SpacedRow {
                    if (settings.favoriteTeams.isEmpty()) {
                        Text("None yet", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary)
                    }
                    settings.favoriteTeams.mapNotNull(NflTeams::find).forEach { t ->
                        SpacedRow(gap = 6) {
                            TeamLogo(t.logoUrl, t.name, size = 36.dp)
                            Text(t.abbr, style = MaterialTheme.typography.headlineSmall, color = SofaColors.BlueBright)
                        }
                    }
                    SofaButton("Edit", onClick = onEditFavorites)
                }
            }
        }
        item(key = "overlay") {
            SettingsSection("Score overlay") {
                OverlaySettings(
                    enabled = settings.overlayEnabled,
                    canDraw = canDraw,
                    corner = settings.overlayCorner,
                    adbCommand = vm.adbGrantCommand,
                    onEnabled = vm::setOverlayEnabled,
                    onCorner = vm::setOverlayCorner,
                )
                val remoteKey = settings.overlay.toggleKey
                Text(
                    if (remoteKey != null) "Remote: ${RemoteKeys.keyName(remoteKey)} shows/hides the overlay while you watch."
                    else "Set up a remote button to show, hide and flip the overlay without leaving your video.",
                    style = MaterialTheme.typography.bodyMedium, color = if (remoteKey != null) SofaColors.BlueBright else SofaColors.TextSecondary,
                )
                SpacedRow {
                    SofaButton("Customize overlay", onClick = onCustomizeOverlay)
                    SofaButton("Remote buttons", onClick = onRemoteButtons)
                }
                if (settings.pinnedGameId != null) {
                    SpacedRow {
                        Text("A game is pinned to the overlay.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
                        SofaButton("Unpin", onClick = vm::clearPinnedGame)
                    }
                } else {
                    Text("Tip: open a game and choose \"Pin to overlay\". Otherwise it shows your team's game.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextMuted)
                }
            }
        }
        item(key = "ticker") {
            SettingsSection("News ticker") {
                Text(
                    "ESPN headlines, scoring plays and live scores. Anything about your teams, fantasy players or picks comes first (★).",
                    style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
                )
                Text("In the app", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
                SpacedRow {
                    SofaButton("On", onClick = { vm.setTickerInApp(true) }, selected = settings.tickerInApp)
                    SofaButton("Off", onClick = { vm.setTickerInApp(false) }, selected = !settings.tickerInApp)
                }
                Text("Over video (needs the score overlay on)", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
                SpacedRow {
                    OverlayTicker.entries.forEach { m ->
                        SofaButton(m.label, onClick = { vm.setOverlayTicker(m) }, selected = settings.overlayTicker == m)
                    }
                }
            }
        }
        item(key = "odds") {
            SettingsSection("DraftKings lines") {
                Text(
                    "Spread, total and moneyline from DraftKings (via ESPN) on game cards, Game Center and the overlay.",
                    style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
                )
                SpacedRow {
                    SofaButton("Show", onClick = { vm.setShowOdds(true) }, selected = settings.showOdds)
                    SofaButton("Hide", onClick = { vm.setShowOdds(false) }, selected = !settings.showOdds)
                }
            }
        }
        item(key = "privacy") {
            SettingsSection("Anonymous usage events") {
                Text(
                    "Counts like \"opened Scores\" or \"turned the overlay on\", with no account, name or IP address attached. They help decide what to build next.",
                    style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary, modifier = Modifier.widthIn(max = 900.dp),
                )
                SpacedRow {
                    SofaButton("Share", onClick = { vm.setUsageOptOut(false) }, selected = !settings.usageEventsOptOut)
                    SofaButton("Don't share", onClick = { vm.setUsageOptOut(true) }, selected = settings.usageEventsOptOut)
                }
            }
        }
        item(key = "feedback") {
            SettingsSection("Feedback") {
                Row(Modifier.focusableRow().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    QrCode(BuildConfig.FEEDBACK_URL)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Scan with your phone to send feedback or report a bug.", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary)
                        Text(BuildConfig.FEEDBACK_URL, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextMuted)
                    }
                }
            }
        }
        item(key = "about") {
            SettingsSection("About") {
                Column(Modifier.fillMaxWidth().focusableRow().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sofa Sports ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary)
                    Text(NOT_AFFILIATED, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary, modifier = Modifier.widthIn(max = 1000.dp))
                }
                updateSection()
            }
        }
        if (BuildConfig.DEBUG) {
            item(key = "debug") {
                SettingsSection("Data source (debug builds only)") {
                    Text(
                        "Fixtures replay last week's games as if live, with fake Sleeper and ESPN leagues. No network needed. Switching clears linked fantasy accounts, tracked picks and the pinned game.",
                        style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
                    )
                    SpacedRow {
                        SofaButton("Live ESPN + Sleeper", onClick = { vm.setDataMode(DataMode.LIVE) }, selected = settings.dataMode == DataMode.LIVE)
                        SofaButton("Fixtures", onClick = { vm.setDataMode(DataMode.FIXTURES) }, selected = settings.dataMode == DataMode.FIXTURES)
                    }
                }
            }
        }
    }
}

const val NOT_AFFILIATED =
    "Sofa Sports is an independent app. It is not affiliated with, endorsed by, or sponsored by the NFL, its teams, ESPN, or Sleeper. " +
        "Team names and logos are trademarks of their owners and are shown for identification only. Scores may be delayed."

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextPrimary)
        content()
    }
}

@Composable
private fun OverlaySettings(
    enabled: Boolean,
    canDraw: Boolean,
    corner: OverlayCorner,
    adbCommand: String,
    onEnabled: (Boolean) -> Unit,
    onCorner: (OverlayCorner) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SpacedRow {
            SofaButton("On", onClick = { onEnabled(true) }, selected = enabled && canDraw)
            SofaButton("Off", onClick = { onEnabled(false) }, selected = !enabled || !canDraw)
        }
        if (!canDraw) {
            AdbInstructions(adbCommand)
        }
        Text("Position", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
        SpacedRow {
            OverlayCorner.entries.forEach { c ->
                SofaButton(cornerLabel(c), onClick = { onCorner(c) }, selected = c == corner)
            }
        }
    }
}

@Composable
private fun AdbInstructions(command: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .focusableRow()
            .background(SofaColors.SurfaceRaised, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("One-time setup: allow the overlay", style = MaterialTheme.typography.titleMedium, color = SofaColors.Live)
        Text(
            "Android TV hides the \"display over other apps\" setting, so grant it from a computer on the same Wi-Fi:",
            style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
        )
        Text("1. On the TV: Settings → System → About → click Build 7 times, then Developer options → USB debugging on.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
        Text("2. On the computer: adb connect <TV IP address>", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = SofaColors.TextPrimary)
        Text("3. Then run:", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
        Text(command, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), color = SofaColors.TextPrimary)
        Text("4. Come back here and turn the overlay on.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
    }
}

internal fun cornerLabel(c: OverlayCorner) = when (c) {
    OverlayCorner.TOP_LEFT -> "Top left"
    OverlayCorner.TOP_CENTER -> "Top center"
    OverlayCorner.TOP_RIGHT -> "Top right"
    OverlayCorner.BOTTOM_LEFT -> "Bottom left"
    OverlayCorner.BOTTOM_CENTER -> "Bottom center"
    OverlayCorner.BOTTOM_RIGHT -> "Bottom right"
}
