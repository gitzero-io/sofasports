package com.sofasports.tv.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.ui.theme.SofaColors

/** The corner card. Kept small (≈ 300dp) and high-contrast so it reads over any video. */
@Composable
fun OverlayWidget(state: OverlayState) {
    Column(
        Modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .background(SofaColors.Background.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
            .border(1.dp, SofaColors.Outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.matchup == null && state.game == null) {
            Text("SOFA SPORTS", style = MaterialTheme.typography.labelLarge, color = SofaColors.TextSecondary)
            Text("Pick a game or link Sleeper", style = MaterialTheme.typography.bodySmall, color = SofaColors.TextMuted)
        }
    }
}

@Composable
internal fun OverlayScoreRow(
    leftLabel: String,
    leftScore: String,
    leftColor: Color,
    rightLabel: String,
    rightScore: String,
    rightColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(leftLabel, style = MaterialTheme.typography.labelLarge, color = leftColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(leftScore, style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextPrimary)
        Spacer(Modifier.width(10.dp))
        Text(rightScore, style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextPrimary)
        Text(rightLabel, style = MaterialTheme.typography.labelLarge, color = rightColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
    }
}
