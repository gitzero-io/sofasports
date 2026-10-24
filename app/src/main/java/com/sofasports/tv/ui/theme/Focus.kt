package com.sofasports.tv.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val SofaShape = RoundedCornerShape(12.dp)

/**
 * The D-pad focus ring. Every focusable surface either uses tv-material's
 * focused border (see [com.sofasports.tv.ui.components.SofaCard]) or this modifier,
 * so focus always looks the same: a 3dp white ring.
 */
fun Modifier.focusRing(
    shape: Shape = SofaShape,
    width: Dp = 3.dp,
    color: Color = SofaColors.Focus,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.hasFocus || it.isFocused }
        .border(width, if (focused) color else Color.Transparent, shape)
}
