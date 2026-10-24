package com.sofasports.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sofasports.tv.domain.ScorePulse

/**
 * Brief scale bump + colour flash when [value] changes (see [ScorePulse] for the rule).
 * The colour is the scoring side's (blue = me, red = opponent).
 */
fun Modifier.scorePulse(value: Double?, color: Color, onlyIncrease: Boolean = true): Modifier = composed {
    val anim = remember { Animatable(0f) }
    val last = remember { mutableDoubleHolder() }
    LaunchedEffect(value) {
        val previous = last.value
        last.value = value
        if (ScorePulse.shouldPulse(previous, value, onlyIncrease)) {
            repeat(2) {
                anim.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
                anim.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
            }
        }
    }
    val t = anim.value
    this
        .graphicsLayer { scaleX = 1f + 0.15f * t; scaleY = 1f + 0.15f * t }
        .background(color.copy(alpha = 0.55f * t), RoundedCornerShape(8.dp))
}

private class DoubleHolder(var value: Double? = null)
private fun mutableDoubleHolder() = DoubleHolder()
