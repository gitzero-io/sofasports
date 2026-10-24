package com.sofasports.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.SofaShape

/**
 * Text entry for a remote. A focused text field pops the keyboard and traps D-pad focus,
 * so this is a button until OK is pressed; only then does it become editable.
 * Tip for long values: the Google TV phone app's keyboard can paste from the phone.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    width: Dp = 420.dp,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    maxLength: Int = 40,
    onDone: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    // After typing, put focus back on the box instead of letting it fall to the top of the screen.
    var returnFocus by remember { mutableStateOf(false) }
    if (editing) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = SofaColors.TextPrimary),
            cursorBrush = SolidColor(SofaColors.Blue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { editing = false; returnFocus = true; onDone() }),
            modifier = modifier
                .width(width)
                .focusRequester(fieldFocus)
                .onFocusChanged {
                    if (wasFocused && !it.isFocused) { editing = false; returnFocus = true }
                    wasFocused = it.isFocused
                }
                .background(SofaColors.SurfaceRaised, SofaShape)
                .border(BorderStroke(3.dp, SofaColors.Focus), SofaShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextMuted)
                inner()
            },
        )
        LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    } else {
        Surface(
            onClick = { editing = true },
            modifier = modifier.width(width).focusRequester(buttonFocus),
            shape = ClickableSurfaceDefaults.shape(SofaShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = SofaColors.SurfaceRaised, focusedContainerColor = SofaColors.SurfaceFocused),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, SofaColors.Outline), shape = SofaShape),
                focusedBorder = Border(BorderStroke(3.dp, SofaColors.Focus), shape = SofaShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Text(
                value.ifEmpty { placeholder },
                style = MaterialTheme.typography.titleLarge,
                color = if (value.isEmpty()) SofaColors.TextMuted else SofaColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        LaunchedEffect(returnFocus) {
            if (returnFocus) { runCatching { buttonFocus.requestFocus() }; returnFocus = false; wasFocused = false }
        }
    }
}
