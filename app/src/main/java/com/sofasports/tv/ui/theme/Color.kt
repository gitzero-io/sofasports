package com.sofasports.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Red vs Blue design tokens.
 *
 * Rule: Blue is always "me" (home team when neutral, my favorite team, my fantasy roster).
 * Red is always the opponent. Nothing else in the UI may use these two hues, so the
 * signal stays unambiguous from across the room. Status colours (live, close game) use
 * amber/green instead.
 *
 * NOTE: SPEC.md was not present when this was written; these values are a stand-in.
 * Every colour in the app comes from this file, so swapping in the spec values is a one-file change.
 */
object SofaColors {
    // Surfaces
    val Background = Color(0xFF0A0D14)
    val Surface = Color(0xFF131925)
    val SurfaceRaised = Color(0xFF1B2230)
    val SurfaceFocused = Color(0xFF243044)
    val Outline = Color(0xFF2B3548)

    // Text
    val TextPrimary = Color(0xFFF3F5F9)
    val TextSecondary = Color(0xFFA6B0C3)
    val TextMuted = Color(0xFF6C778C)

    // Sides
    val Blue = Color(0xFF2F7DFF)
    val BlueBright = Color(0xFF6AA5FF)
    val BlueDim = Color(0xFF16346B)
    val Red = Color(0xFFFF3D47)
    val RedBright = Color(0xFFFF7A80)
    val RedDim = Color(0xFF6B1A20)

    // Status
    val Live = Color(0xFFFFB020)
    val Close = Color(0xFFFFB020)
    val Positive = Color(0xFF34D399)

    // Books (badges only; never used for teams, so red/blue keep their meaning)
    val DraftKings = Color(0xFF53D337)
    val PrizePicks = Color(0xFFA78BFA)

    // Focus ring
    val Focus = Color(0xFFFFFFFF)
}
