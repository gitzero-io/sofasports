package com.sofasports.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.SofaShape

private val focusBorder = Border(BorderStroke(3.dp, SofaColors.Focus), shape = SofaShape)

/** Focusable card with the standard focus ring and a gentle scale. */
@Composable
fun SofaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = SofaColors.Surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(SofaShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = SofaColors.SurfaceFocused,
            pressedContainerColor = SofaColors.SurfaceFocused,
            contentColor = SofaColors.TextPrimary,
            focusedContentColor = SofaColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusBorder),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Pill button used throughout (nav, settings, pickers). */
@Composable
fun SofaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectedColor: Color = SofaColors.Blue,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) selectedColor else SofaColors.SurfaceRaised,
            focusedContainerColor = if (selected) selectedColor else SofaColors.SurfaceFocused,
            contentColor = SofaColors.TextPrimary,
            focusedContentColor = SofaColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, SofaColors.Focus), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

/** Small coloured tag: LIVE, CLOSE, FINAL. */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = SofaColors.Background) {
    Box(
        modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = SofaColors.TextPrimary,
        modifier = modifier.padding(bottom = 12.dp),
    )
}

@Composable
fun MessageBox(title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextPrimary)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
    }
}

@Composable
fun SpacedRow(modifier: Modifier = Modifier, gap: Int = 12, content: @Composable RowScope.() -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
