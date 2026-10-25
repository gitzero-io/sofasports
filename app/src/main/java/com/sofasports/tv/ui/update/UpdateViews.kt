package com.sofasports.tv.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.update.InstallState
import com.sofasports.tv.data.update.UpdateChecker
import com.sofasports.tv.data.update.UpdateInstaller
import com.sofasports.tv.data.update.UpdateStatus
import com.sofasports.tv.data.update.VersionInfo
import com.sofasports.tv.ui.components.QrCode
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.SofaShape
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
    private val usage: UsageEvents,
) : ViewModel() {
    val status = checker.status
    val install = installer.state
    fun check() = viewModelScope.launch {
        usage.track(UsageEvent.UpdateChecked)
        checker.check()
    }
    fun install(info: VersionInfo) = viewModelScope.launch { installer.install(info) }
}

/** Settings → About: manual check + result. */
@Composable
fun UpdateSection(vm: UpdateViewModel = hiltViewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SpacedRow {
            SofaButton(if (status == UpdateStatus.Checking) "Checking…" else "Check for updates", onClick = vm::check)
            Text(
                when (val s = status) {
                    UpdateStatus.Unknown, UpdateStatus.Checking -> ""
                    UpdateStatus.UpToDate -> "You're on the latest version."
                    is UpdateStatus.Failed -> "Couldn't check: ${s.reason}"
                    is UpdateStatus.Available -> ""
                },
                style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
            )
        }
        (status as? UpdateStatus.Available)?.let { UpdateCard(it, vm) }
    }
}

/** Shown on Home when a check found a newer build. */
@Composable
fun UpdateBanner(vm: UpdateViewModel = hiltViewModel()) {
    val s = vm.status.collectAsStateWithLifecycle().value as? UpdateStatus.Available ?: return
    UpdateCard(s, vm)
}

@Composable
private fun UpdateCard(s: UpdateStatus.Available, vm: UpdateViewModel) {
    val install by vm.install.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().background(SofaColors.SurfaceRaised, SofaShape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (s.required) "Update required: " else "Update available: ") + s.info.latestVersionName,
                style = MaterialTheme.typography.titleLarge, color = if (s.required) SofaColors.Live else SofaColors.TextPrimary,
            )
            if (s.info.notes.isNotBlank()) Text(s.info.notes, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
            SpacedRow {
                SofaButton(installLabel(install), onClick = { vm.install(s.info) })
                installHint(install)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (install is InstallState.Failed) SofaColors.Live else SofaColors.TextSecondary)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QrCode(s.info.apkUrl, size = 96.dp)
            Text("Or use Downloader / adb", style = MaterialTheme.typography.bodySmall, color = SofaColors.TextMuted)
        }
    }
}

private fun installLabel(state: InstallState) = when (state) {
    is InstallState.Downloading -> state.percent?.let { "Downloading $it%" } ?: "Downloading…"
    InstallState.Installing -> "Installing…"
    InstallState.NeedsPermission -> "Install update"
    is InstallState.Failed -> "Try again"
    InstallState.Idle -> "Install update"
}

private fun installHint(state: InstallState): String? = when (state) {
    InstallState.NeedsPermission -> "Turn on Sofa Sports under \"Install unknown apps\", come back, and press Install again."
    InstallState.Installing -> "Confirm on the next screen. Sofa Sports closes while it updates; open it again after."
    is InstallState.Failed -> "Couldn't update: ${state.reason}"
    else -> null
}
