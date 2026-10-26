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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.sofasports.tv.data.espnff.EspnLeagueLink
import com.sofasports.tv.data.prefs.UserSettings
import com.sofasports.tv.domain.InstalledApp
import com.sofasports.tv.domain.LiveTvApps
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
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.TeamKey
import com.sofasports.tv.overlay.OverlayLayout
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
    onEditLeagues: () -> Unit = {},
    onAddEspnLeague: () -> Unit,
    onCustomizeOverlay: () -> Unit = {},
    onRemoteButtons: () -> Unit = {},
    updateSection: @Composable () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val canDraw by vm.canDrawOverlay.collectAsStateWithLifecycle()
    val espnLinks by vm.espnLinks.collectAsStateWithLifecycle()
    val tvApps by vm.tvApps.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.recheckPermission() }

    var category by rememberSaveable { mutableStateOf(SettingsCategory.FANTASY) }
    val categories = SettingsCategory.entries.filter { it != SettingsCategory.DEVELOPER || BuildConfig.DEBUG }
    val railFocus = remember(categories) { categories.associateWith { FocusRequester() } }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        SettingsRail(categories, category, railFocus, onSelect = { category = it })
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxSize()
                // Coming down from the tabs lands on the menu, not halfway into a section.
                .focusProperties {
                    onEnter = {
                        if (requestedFocusDirection == FocusDirection.Down) railFocus[category]?.let { if (!it.requestFocus()) cancelFocusChange() }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(32.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp),
        ) {
            settingsPane(category, settings, canDraw, espnLinks, tvApps, vm, onEditFavorites, onEditLeagues, onAddEspnLeague, onCustomizeOverlay, onRemoteButtons, updateSection)
        }
    }
}

/** Left-hand menu; like the top tabs, moving onto a category shows it. */
enum class SettingsCategory(val label: String) {
    FANTASY("Fantasy"),
    TEAMS("Sports & teams"),
    OVERLAY("Score overlay"),
    WATCH("Watch games"),
    TICKER("Ticker & lines"),
    PRIVACY("Privacy & feedback"),
    ABOUT("About & updates"),
    DEVELOPER("Developer"),
}

@Composable
private fun SettingsRail(
    categories: List<SettingsCategory>,
    current: SettingsCategory,
    requesters: Map<SettingsCategory, FocusRequester>,
    onSelect: (SettingsCategory) -> Unit,
) {
    Column(
        Modifier
            .width(300.dp)
            .focusProperties { onEnter = { requesters[current]?.let { if (!it.requestFocus()) cancelFocusChange() } } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { c ->
            SofaButton(
                c.label, onClick = { onSelect(c) }, selected = c == current,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requesters.getValue(c))
                    .onFocusChanged { if (it.isFocused) onSelect(c) },
            )
        }
    }
}

private fun LazyListScope.settingsPane(
    category: SettingsCategory,
    settings: UserSettings,
    canDraw: Boolean,
    espnLinks: List<EspnLeagueLink>,
    tvApps: List<InstalledApp>,
    vm: SettingsViewModel,
    onEditFavorites: () -> Unit,
    onEditLeagues: () -> Unit = {},
    onAddEspnLeague: () -> Unit,
    onCustomizeOverlay: () -> Unit,
    onRemoteButtons: () -> Unit,
    updateSection: @Composable () -> Unit,
) {
    when (category) {
        SettingsCategory.FANTASY -> fantasyItems(settings, espnLinks, vm, onAddEspnLeague)
        SettingsCategory.TEAMS -> teamItems(settings, onEditFavorites, onEditLeagues)
        SettingsCategory.OVERLAY -> overlayItems(settings, canDraw, vm, onCustomizeOverlay, onRemoteButtons)
        SettingsCategory.WATCH -> watchItems(settings, tvApps, vm)
        SettingsCategory.TICKER -> tickerItems(settings, vm)
        SettingsCategory.PRIVACY -> privacyItems(settings, vm)
        SettingsCategory.ABOUT -> aboutItems(updateSection)
        SettingsCategory.DEVELOPER -> developerItems(settings, vm)
    }
}

private fun LazyListScope.fantasyItems(settings: UserSettings, espnLinks: List<EspnLeagueLink>, vm: SettingsViewModel, onAddEspnLeague: () -> Unit) {
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
}

private fun LazyListScope.teamItems(settings: UserSettings, onEditFavorites: () -> Unit, onEditLeagues: () -> Unit) {
    item(key = "leagues") {
        SettingsSection("Sports you follow") {
            SpacedRow {
                Text(settings.followedLeagues.joinToString(" · ") { it.label }, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary)
                SofaButton("Edit", onClick = onEditLeagues)
            }
            Text(
                "Fantasy and the picks tracker are NFL-only for now.",
                style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextMuted,
            )
        }
    }
    item(key = "favorites") {
        SettingsSection("Favorites") {
            SpacedRow {
                if (settings.favoriteTeams.isEmpty()) {
                    Text("None yet", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary)
                }
                settings.favoriteTeams.forEach { key ->
                    val league = TeamKey.league(key) ?: League.NFL
                    val abbr = TeamKey.abbr(key)
                    SpacedRow(gap = 6) {
                        league.logoUrl(abbr)?.let { TeamLogo(it, abbr, size = 36.dp) }
                        Text(
                            if (settings.followedLeagues.size > 1 || league != League.NFL) "${league.label} ${if (league.hasTeams) abbr else abbr.substringAfterLast(' ')}" else abbr,
                            style = MaterialTheme.typography.headlineSmall, color = SofaColors.BlueBright,
                        )
                    }
                }
                SofaButton("Edit", onClick = onEditFavorites)
            }
        }
    }
}

private fun LazyListScope.overlayItems(
    settings: UserSettings,
    canDraw: Boolean,
    vm: SettingsViewModel,
    onCustomizeOverlay: () -> Unit,
    onRemoteButtons: () -> Unit,
) {
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
}

private fun LazyListScope.watchItems(settings: UserSettings, apps: List<InstalledApp>, vm: SettingsViewModel) {
    item(key = "watch") {
        SettingsSection("Your live TV app") {
            Text(
                "\"Watch\" on a game opens this app for games on TV (CBS, Fox, NBC, ABC, ESPN, NFL Network). Games streamed only on " +
                    "Prime Video, Peacock, Netflix and the like open that app when it's installed. The score overlay stays on top.",
                style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
            )
            Text(
                "Apps can't be opened straight to a game, so pick the channel once the app opens, or use \"Find on Google TV\".",
                style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextMuted,
            )
            SofaButton("None: I use cable or an antenna", onClick = { vm.setWatchApp(null) }, selected = settings.watchApp == null)
            apps.chunked(3).forEach { row ->
                SpacedRow {
                    row.forEach { app ->
                        val known = LiveTvApps.known[app.pkg]
                        SofaButton(known ?: app.label, onClick = { vm.setWatchApp(app.pkg) }, selected = settings.watchApp == app.pkg)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.tickerItems(settings: UserSettings, vm: SettingsViewModel) {
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
}

private fun LazyListScope.privacyItems(settings: UserSettings, vm: SettingsViewModel) {
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
}

private fun LazyListScope.aboutItems(updateSection: @Composable () -> Unit) {
    item(key = "about") {
        SettingsSection("About") {
            Column(Modifier.fillMaxWidth().focusableRow().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sofa Sports ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary)
                Text(NOT_AFFILIATED, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary, modifier = Modifier.widthIn(max = 1000.dp))
            }
            updateSection()
        }
    }
}

private fun LazyListScope.developerItems(settings: UserSettings, vm: SettingsViewModel) {
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

const val NOT_AFFILIATED =
    "Sofa Sports is an independent app. It is not affiliated with, endorsed by, or sponsored by any league, team, governing body, ESPN, TheSportsDB, or Sleeper. " +
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
        // Two rows laid out like the screen: top three, then bottom three.
        OverlayCorner.entries.partition { !OverlayLayout.isBottom(it) }.toList().forEach { row ->
            SpacedRow {
                row.forEach { c -> SofaButton(cornerLabel(c), onClick = { onCorner(c) }, selected = c == corner) }
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
