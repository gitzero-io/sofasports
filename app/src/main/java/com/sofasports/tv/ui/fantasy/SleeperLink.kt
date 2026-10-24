package com.sofasports.tv.ui.fantasy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sofasports.tv.data.repo.LinkResult
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.SofaShape
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
    var editing by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
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
            // On a remote, a focused text field pops the keyboard and traps D-pad focus. So the field
            // is a button until OK is pressed; only then does it become editable.
            if (editing) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = SofaColors.TextPrimary),
                    cursorBrush = SolidColor(SofaColors.Blue),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { editing = false; submit() }),
                    modifier = Modifier
                        .width(420.dp)
                        .focusRequester(fieldFocus)
                        .onFocusChanged { if (wasFocused && !it.isFocused) editing = false; wasFocused = it.isFocused }
                        .background(SofaColors.SurfaceRaised, SofaShape)
                        .border(BorderStroke(3.dp, SofaColors.Focus), SofaShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("e.g. yourname", style = MaterialTheme.typography.titleLarge, color = SofaColors.TextMuted)
                        inner()
                    },
                )
                LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
            } else {
                Surface(
                    onClick = { editing = true },
                    modifier = Modifier.width(420.dp),
                    shape = ClickableSurfaceDefaults.shape(SofaShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = SofaColors.SurfaceRaised,
                        focusedContainerColor = SofaColors.SurfaceFocused,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, SofaColors.Outline), shape = SofaShape),
                        focusedBorder = Border(BorderStroke(3.dp, SofaColors.Focus), shape = SofaShape),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Text(
                        text.ifEmpty { "Press OK to type your username" },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (text.isEmpty()) SofaColors.TextMuted else SofaColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            SofaButton(if (busy) "Linking…" else "Link", onClick = { submit() }, selected = text.isNotBlank())
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SofaColors.Live) }
    }
}
