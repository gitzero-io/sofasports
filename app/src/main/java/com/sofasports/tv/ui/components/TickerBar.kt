package com.sofasports.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.TickerItem
import com.sofasports.tv.domain.TickerKind
import com.sofasports.tv.ui.theme.SofaColors

private fun tagColor(i: TickerItem): Color = when {
    i.mine -> SofaColors.Live
    i.kind == TickerKind.SCORE_EVENT -> SofaColors.Positive
    i.kind == TickerKind.LIVE_SCORE -> SofaColors.TextSecondary
    else -> SofaColors.TextMuted
}

/**
 * Scrolling crawl. Never focusable, so it can't steal the D-pad.
 * Source label is always visible on the left ("ESPN"), per its terms.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TickerCrawl(
    items: List<TickerItem>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    velocity: Dp = 70.dp,
) {
    if (items.isEmpty()) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.background(SofaColors.SurfaceFocused).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text("NFL", style = MaterialTheme.typography.labelMedium, color = SofaColors.TextPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately, initialDelayMillis = 0, velocity = velocity),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { i ->
                Text((if (i.mine) "★ " else "") + i.kind.tag, style = MaterialTheme.typography.labelMedium, color = tagColor(i))
                Spacer(Modifier.width(8.dp))
                Text(i.text, style = textStyle, color = SofaColors.TextPrimary, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(36.dp))
            }
            Text("Headlines: ESPN", style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted)
            Spacer(Modifier.width(48.dp))
        }
    }
}

/**
 * Full-width bar: in the app (bottom of every screen) and over video (Settings → News ticker).
 * The background runs edge to edge but the text stays inside the TV overscan-safe area.
 */
@Composable
fun TickerBar(items: List<TickerItem>, modifier: Modifier = Modifier, bottomInset: Dp = 0.dp) {
    if (items.isEmpty()) return
    Box(
        modifier
            .fillMaxWidth()
            .background(SofaColors.Surface)
            .padding(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 8.dp + bottomInset),
    ) { TickerCrawl(items) }
}
