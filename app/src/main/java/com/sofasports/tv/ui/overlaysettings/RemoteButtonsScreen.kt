package com.sofasports.tv.ui.overlaysettings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.remote.RemoteKeys
import com.sofasports.tv.remote.RemoteSlot
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.settings.SettingsSection
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.focusableRow

/** Turn on the accessibility service, then teach it one or two buttons. */
@Composable
fun RemoteButtonsScreen(onDone: () -> Unit, vm: OverlaySettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val enabled by vm.enabledInSettings.collectAsStateWithLifecycle()
    val learning by vm.learning.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.recheck() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp), contentPadding = PaddingValues(bottom = 64.dp)) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remote buttons", style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary)
                    Hint("Show, hide and flip the overlay while you watch, without leaving your video.")
                }
                SofaButton("Done", onClick = onDone, selected = true, modifier = Modifier.focusRequester(first))
            }
        }
        if (!connected) {
            item(key = "enable") {
                SettingsSection("Step 1: turn on Sofa Sports remote buttons") {
                    Hint(
                        "Android only lets an app react to remote buttons inside other apps through an accessibility service. " +
                            "Sofa Sports uses it only to listen for the buttons you pick below. It never reads your screen.",
                    )
                    Text(
                        "On the TV: Settings → System → Accessibility → Sofa Sports remote buttons → On.",
                        style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary,
                    )
                    SofaButton("Open accessibility settings", onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    })
                    if (enabled) Hint("It's switched on. Waiting for Android to start it…")
                    AdbBox(vm.adbCommands)
                }
            }
        }
        item(key = "buttons") {
            SettingsSection(if (connected) "Your buttons" else "Step 2: pick your buttons") {
                Hint("Pick a button you don't use while watching: on the onn 4K Pro remote the ★ button is ideal; on others try a colour button, Channel up/down, Info or Guide. The D-pad, OK, Back, Home, volume and play/pause can't be used.")
                SlotRow(
                    "Show / hide", settings.overlay.toggleKey, connected, learning == RemoteSlot.TOGGLE,
                    onSet = { vm.learn(RemoteSlot.TOGGLE) }, onClear = { vm.clear(RemoteSlot.TOGGLE) },
                )
                SlotRow(
                    "Next page (optional)", settings.overlay.nextKey, connected, learning == RemoteSlot.NEXT,
                    onSet = { vm.learn(RemoteSlot.NEXT) }, onClear = { vm.clear(RemoteSlot.NEXT) },
                )
                if (learning != null) {
                    Text(
                        "Press the button on your remote now…  (Back to cancel)",
                        style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextPrimary,
                        modifier = Modifier.background(SofaColors.BlueDim, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                message?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = SofaColors.Live) }
            }
        }
        item(key = "howto") {
            SettingsSection("How the buttons work") {
                Column(Modifier.focusableRow().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HowTo("Show / hide button", "press = show or hide · double-press = next page · hold = open Sofa Sports")
                    HowTo("Next page button", "press = next page · double-press = previous page · hold = show or hide")
                    Hint("If the overlay is off, the show/hide button turns it on.")
                }
            }
        }
    }
}

@Composable
private fun SlotRow(title: String, key: Int?, connected: Boolean, learningThis: Boolean, onSet: () -> Unit, onClear: () -> Unit) {
    SpacedRow {
        Text(title, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary)
        Text(
            if (learningThis) "Waiting…" else key?.let(RemoteKeys::keyName) ?: "Not set",
            style = MaterialTheme.typography.titleLarge,
            color = if (key != null) SofaColors.BlueBright else SofaColors.TextMuted,
        )
        if (connected) {
            SofaButton(if (key == null) "Set button" else "Change", onClick = onSet)
            if (key != null) SofaButton("Clear", onClick = onClear)
        }
    }
}

@Composable
private fun HowTo(button: String, what: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(button, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary)
        Text(what, style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary)
    }
}

@Composable
private fun AdbBox(commands: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .focusableRow()
            .background(SofaColors.SurfaceRaised, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Greyed out, \"restricted setting\", or not listed?", style = MaterialTheme.typography.titleMedium, color = SofaColors.Live)
        Hint("Android blocks this for apps installed outside the Play Store. From a computer on the same Wi-Fi (adb connect <TV IP> first), run:")
        commands.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = SofaColors.TextPrimary)
        }
        Hint("The first line lifts the restriction so the switch works; the next two turn the service on directly. Then come back here.")
    }
}
