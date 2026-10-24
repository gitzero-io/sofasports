package com.sofasports.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.prefs.OverlayCorner
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.theme.SofaColors

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val canDraw by vm.canDrawOverlay.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.recheckPermission() }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item {
            SettingsSection("Score overlay") {
                OverlaySettings(
                    enabled = settings.overlayEnabled,
                    canDraw = canDraw,
                    corner = settings.overlayCorner,
                    adbCommand = vm.adbGrantCommand,
                    onEnabled = vm::setOverlayEnabled,
                    onCorner = vm::setOverlayCorner,
                )
            }
        }
    }
}

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
        Text("Corner", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
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
            .background(SofaColors.SurfaceRaised, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("One-time setup: allow the overlay", style = MaterialTheme.typography.titleMedium, color = SofaColors.Live)
        Text(
            "Android TV hides the \"display over other apps\" setting, so grant it from a computer on the same Wi-Fi:",
            style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
        )
        Text("1. On the TV: Settings → System → Developer options → USB/Network debugging on.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
        Text("2. On the computer: adb connect <TV IP address>", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = SofaColors.TextPrimary)
        Text("3. Then run:", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
        Text(command, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), color = SofaColors.TextPrimary)
        Text("4. Come back here and turn the overlay on.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
    }
}

internal fun cornerLabel(c: OverlayCorner) = when (c) {
    OverlayCorner.TOP_LEFT -> "Top left"
    OverlayCorner.TOP_RIGHT -> "Top right"
    OverlayCorner.BOTTOM_LEFT -> "Bottom left"
    OverlayCorner.BOTTOM_RIGHT -> "Bottom right"
}
