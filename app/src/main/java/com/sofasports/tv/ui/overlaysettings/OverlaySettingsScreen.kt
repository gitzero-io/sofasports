package com.sofasports.tv.ui.overlaysettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.prefs.MomentKind
import com.sofasports.tv.data.prefs.OverlayCorner
import com.sofasports.tv.data.prefs.OverlayEdge
import com.sofasports.tv.data.prefs.OverlayOpacity
import com.sofasports.tv.data.prefs.OverlayPage
import com.sofasports.tv.data.prefs.OverlayPrefs
import com.sofasports.tv.data.prefs.OverlayRow
import com.sofasports.tv.data.prefs.OverlaySize
import com.sofasports.tv.data.prefs.QuietHours
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.settings.SettingsSection
import com.sofasports.tv.ui.settings.cornerLabel
import com.sofasports.tv.ui.theme.SofaColors

/** Everything about how the overlay looks and what it does on its own. */
@Composable
fun OverlaySettingsScreen(
    onDone: () -> Unit,
    onPosition: (OverlayCorner) -> Unit,
    vm: OverlaySettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val o = settings.overlay
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp), contentPadding = PaddingValues(bottom = 64.dp)) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Customize overlay", style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary)
                    Text("Changes apply right away. Press Home to see the overlay over other apps.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
                }
                SofaButton("Done", onClick = onDone, selected = true, modifier = Modifier.focusRequester(first))
            }
        }
        item(key = "pages") {
            SettingsSection("Pages") {
                Hint("Flip through these with your remote button. Pages with nothing to show are skipped.")
                SpacedRow {
                    OverlayPage.entries.forEach { p ->
                        SofaButton(p.label, onClick = { vm.update { it.copy(pages = it.pages.toggled(p, keepOne = true)) } }, selected = p in o.pages)
                    }
                }
            }
        }
        item(key = "look") {
            SettingsSection("Look") {
                Choice("Size", OverlaySize.entries, o.size, { it.label }) { v -> vm.update { it.copy(size = v) } }
                Choice("Background", OverlayOpacity.entries, o.opacity, { it.label }) { v -> vm.update { it.copy(opacity = v) } }
                Choice("Position", OverlayCorner.entries, settings.overlayCorner, ::cornerLabel, onPosition)
                Choice("Distance from edge", OverlayEdge.entries, o.edge, { it.label }) { v -> vm.update { it.copy(edge = v) } }
                Hint("Use Extra if the card is cut off at the edge of your TV.")
            }
        }
        item(key = "rows") {
            SettingsSection("Details") {
                SpacedRow {
                    OverlayRow.entries.forEach { r ->
                        SofaButton(r.label, onClick = { vm.update { it.copy(rows = it.rows.toggled(r)) } }, selected = r in o.rows)
                    }
                }
            }
        }
        item(key = "rotate") {
            SettingsSection("Auto-rotate pages") {
                Choice(null, OverlayPrefs.ROTATE_CHOICES, o.rotateSec, ::secondsLabel) { v -> vm.update { it.copy(rotateSec = v) } }
                Hint("Cycles through the full pages. It never minimizes.")
            }
        }
        item(key = "smart") {
            SettingsSection("Smart automatic mode") {
                Hint("Lets the overlay act on its own: pop up on big moments, shrink when nothing's happening, and stay out of the way when no games are on. Off = it only changes when you press a button.")
                SpacedRow {
                    SofaButton("On", onClick = { vm.update { it.copy(smart = true) } }, selected = o.smart)
                    SofaButton("Off", onClick = { vm.update { it.copy(smart = false) } }, selected = !o.smart)
                }
                if (o.smart) SmartOptions(o, vm)
            }
        }
    }
}

@Composable
private fun SmartOptions(o: OverlayPrefs, vm: OverlaySettingsViewModel) {
    Column(Modifier.widthIn(max = 1400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Label("Pop on big moments")
        Hint("The card comes forward for ~8 seconds with what happened, even if it's hidden or minimized, then goes back.")
        SpacedRow {
            SofaButton("On", onClick = { vm.update { it.copy(pop = true) } }, selected = o.pop)
            SofaButton("Off", onClick = { vm.update { it.copy(pop = false) } }, selected = !o.pop)
        }
        if (o.pop) SpacedRow {
            MomentKind.entries.forEach { k ->
                SofaButton(k.label, onClick = { vm.update { it.copy(popKinds = it.popKinds.toggled(k)) } }, selected = k in o.popKinds)
            }
        }
        Choice("Minimize when quiet", OverlayPrefs.MINIMIZE_CHOICES, o.minimizeSec, ::secondsLabel) { v -> vm.update { it.copy(minimizeSec = v) } }
        Hint("After this long without a button press or a big moment, the card shrinks to the one-line pill (needs the Minimized page on).")
        Label("Only show during live games")
        SpacedRow {
            SofaButton("On", onClick = { vm.update { it.copy(liveOnly = true) } }, selected = o.liveOnly)
            SofaButton("Off", onClick = { vm.update { it.copy(liveOnly = false) } }, selected = !o.liveOnly)
        }
        Choice("Quiet hours", QuietHours.entries, o.quiet, { it.label }) { v -> vm.update { it.copy(quiet = v) } }
        Hint("Your remote button always wins: pressing show/hide overrides these until the situation changes (e.g. games start or end).")
    }
}

@Composable
internal fun <T> Choice(title: String?, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let { Label(it) }
        SpacedRow {
            options.forEach { v -> SofaButton(label(v), onClick = { onPick(v) }, selected = v == selected) }
        }
    }
}

@Composable
internal fun Label(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)

@Composable
internal fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextMuted, modifier = Modifier.widthIn(max = 1100.dp))

internal fun secondsLabel(s: Int) = when {
    s == 0 -> "Off"
    s < 60 -> "${s}s"
    else -> "${s / 60} min"
}
