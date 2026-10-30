package com.sofasports.tv.ui.picks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.BetStatus
import com.sofasports.tv.domain.Betting
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.PickBook
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.domain.PickType
import com.sofasports.tv.ui.components.Badge
import com.sofasports.tv.ui.components.formatKickoff
import com.sofasports.tv.ui.components.scorePulse
import com.sofasports.tv.ui.theme.SofaColors

/** Book badges never use red or blue, so the Red vs Blue meaning stays clean. */
fun bookColor(b: PickBook): Color = when (b) {
    PickBook.DRAFTKINGS -> SofaColors.DraftKings
    PickBook.PRIZEPICKS -> SofaColors.PrizePicks
    PickBook.FANDUEL, PickBook.BETMGM, PickBook.OTHER -> SofaColors.TextSecondary
}

fun statusText(p: PickProgress): String = when (p.status) {
    BetStatus.PENDING -> p.game?.let { if (it.state == GameState.PRE) formatKickoff(it.startEpochMs) else "waiting for stats" } ?: "no game this week"
    BetStatus.WINNING -> "hitting"
    BetStatus.LOSING -> "not yet"
    BetStatus.PUSHING -> "on the number"
    BetStatus.WON -> "WON"
    BetStatus.LOST -> "lost"
    BetStatus.PUSH -> "push"
}

fun statusColor(s: BetStatus): Color = when (s) {
    BetStatus.WON, BetStatus.WINNING -> SofaColors.Positive
    BetStatus.LOSING -> SofaColors.Live
    BetStatus.PENDING, BetStatus.LOST, BetStatus.PUSH, BetStatus.PUSHING -> SofaColors.TextMuted
}

/** "187 / 224.5" for props and totals, "+5.5" for spreads/moneylines. */
fun currentText(p: PickProgress): String? {
    val c = p.current ?: return null
    return when (p.pick.type) {
        PickType.PROP, PickType.TOTAL, PickType.TEAM_TOTAL -> "${Betting.formatNumber(c)} / ${Betting.formatNumber(p.pick.line)}"
        PickType.SPREAD -> "${if (c >= 0) "covering by" else "short by"} ${Betting.formatNumber(kotlin.math.abs(c))}"
        PickType.MONEYLINE -> if (c > 0) "up ${c.toInt()}" else if (c < 0) "down ${(-c).toInt()}" else "tied"
    }
}

@Composable
fun PickRow(p: PickProgress, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(p.pick.book.tag, bookColor(p.pick.book))
            Spacer(Modifier.width(12.dp))
            Text(
                p.pick.describe(p.game), style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            currentText(p)?.let {
                Text(
                    it, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp).scorePulse(p.current, statusColor(p.status), onlyIncrease = false).padding(horizontal = 6.dp),
                )
            }
            Text(statusText(p), style = MaterialTheme.typography.labelLarge, color = statusColor(p.status))
        }
        progressFraction(p)?.let { ProgressBar(it, statusColor(p.status), Modifier.padding(top = 8.dp)) }
    }
}

/** How far an over/under pick's live number has climbed toward its line; null for spreads, moneylines and picks with no number yet. */
fun progressFraction(p: PickProgress): Float? {
    val c = p.current ?: return null
    return if (p.pick.type.overUnder && p.pick.line > 0) (c / p.pick.line).toFloat() else null
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).background(SofaColors.SurfaceRaised, RoundedCornerShape(height / 2))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(height / 2)))
    }
}
