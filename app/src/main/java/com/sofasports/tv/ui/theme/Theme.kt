package com.sofasports.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val SofaColorScheme = darkColorScheme(
    primary = SofaColors.Blue,
    onPrimary = SofaColors.TextPrimary,
    primaryContainer = SofaColors.BlueDim,
    onPrimaryContainer = SofaColors.TextPrimary,
    secondary = SofaColors.Red,
    onSecondary = SofaColors.TextPrimary,
    secondaryContainer = SofaColors.RedDim,
    onSecondaryContainer = SofaColors.TextPrimary,
    tertiary = SofaColors.Live,
    background = SofaColors.Background,
    onBackground = SofaColors.TextPrimary,
    surface = SofaColors.Surface,
    onSurface = SofaColors.TextPrimary,
    surfaceVariant = SofaColors.SurfaceRaised,
    onSurfaceVariant = SofaColors.TextSecondary,
    border = SofaColors.Outline,
    inverseSurface = SofaColors.TextPrimary,
    inverseOnSurface = SofaColors.Background,
    error = SofaColors.Red,
)

/** Always dark: it's a living-room TV app. */
@Composable
fun SofaSportsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SofaColorScheme,
        typography = SofaTypography,
        content = content,
    )
}
