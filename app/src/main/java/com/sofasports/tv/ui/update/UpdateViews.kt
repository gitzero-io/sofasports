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
import com.sofasports.tv.data.update.UpdateChecker
import com.sofasports.tv.data.update.UpdateStatus
import com.sofasports.tv.ui.components.QrCode
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.SofaShape
import com.sofasports.tv.ui.theme.focusableRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(private val checker: UpdateChecker) : ViewModel() {
    val status = checker.status
    fun check() = viewModelScope.launch { checker.check() }
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
        (status as? UpdateStatus.Available)?.let { UpdateCard(it) }
    }
}

/** Shown on Home when a check found a newer build. */
@Composable
fun UpdateBanner(vm: UpdateViewModel = hiltViewModel()) {
    val s = vm.status.collectAsStateWithLifecycle().value as? UpdateStatus.Available ?: return
    UpdateCard(s)
}

@Composable
private fun UpdateCard(s: UpdateStatus.Available) {
    Row(
        Modifier.fillMaxWidth().focusableRow().background(SofaColors.SurfaceRaised, SofaShape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        QrCode(s.info.apkUrl, size = 120.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (s.required) "Update required: " else "Update available: ") + s.info.latestVersionName,
                style = MaterialTheme.typography.titleLarge, color = if (s.required) SofaColors.Live else SofaColors.TextPrimary,
            )
            if (s.info.notes.isNotBlank()) Text(s.info.notes, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
            Text("Install with the Downloader app or adb using:", style = MaterialTheme.typography.bodySmall, color = SofaColors.TextMuted)
            Text(s.info.apkUrl, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextPrimary)
        }
    }
}
