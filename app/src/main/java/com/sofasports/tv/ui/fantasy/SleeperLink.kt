package com.sofasports.tv.ui.fantasy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.repo.LinkResult
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.TvTextField
import com.sofasports.tv.ui.theme.SofaColors
import kotlinx.coroutines.launch

/** Username entry. Sleeper's public API needs only the username: no password, no OAuth. */
@Composable
fun SleeperLinkForm(
    onLink: suspend (String) -> LinkResult,
    modifier: Modifier = Modifier,
    initial: String = "",
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun submit() {
        if (busy) return
        if (text.isBlank()) {
            status = "Type your Sleeper username first."
            return
        }
        busy = true
        status = "Looking up \"${text.trim()}\"…"
        scope.launch {
            status = when (val r = onLink(text)) {
                LinkResult.Ok -> null
                LinkResult.NotFound -> "No Sleeper user called \"${text.trim()}\". Check the spelling."
                is LinkResult.Failed -> "Couldn't reach Sleeper: ${r.message}"
            }
            busy = false
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sleeper username", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
        SpacedRow {
            TvTextField(text, { text = it }, "Press OK to type your username", onDone = ::submit)
            SofaButton(if (busy) "Linking…" else "Link", onClick = ::submit, selected = text.isNotBlank())
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SofaColors.Live) }
    }
}
