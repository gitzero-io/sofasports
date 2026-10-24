package com.sofasports.tv.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sofasports.tv.ui.theme.SofaColors

/**
 * My side's win chance over the game. Above the midline = me (blue), below = opponent (red).
 * Two filled areas make "who's ahead" readable from the couch without axis labels.
 */
@Composable
fun WinProbabilityChart(series: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val mid = size.height / 2
        drawLine(SofaColors.Outline, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)
        if (series.size < 2) return@Canvas
        val step = size.width / (series.size - 1)
        fun y(v: Double) = (size.height * (1.0 - v.coerceIn(0.0, 1.0))).toFloat()
        val line = Path().apply {
            moveTo(0f, y(series[0]))
            series.forEachIndexed { i, v -> if (i > 0) lineTo(i * step, y(v)) }
        }
        // Fill between the line and the midline, clipped per side.
        val area = Path().apply {
            addPath(line)
            lineTo((series.size - 1) * step, mid)
            lineTo(0f, mid)
            close()
        }
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, 0f, size.width, mid)
        drawPath(area, SofaColors.Blue.copy(alpha = 0.35f))
        drawContext.canvas.restore()
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, mid, size.width, size.height)
        drawPath(area, SofaColors.Red.copy(alpha = 0.35f))
        drawContext.canvas.restore()
        drawPath(line, SofaColors.TextPrimary, style = Stroke(width = 3f))
    }
}
