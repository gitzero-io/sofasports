package com.sofasports.tv.ui.fantasy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.sofasports.tv.ui.components.SofaCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyTeam
import com.sofasports.tv.ui.components.scorePulse
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.SofaShape
import com.sofasports.tv.ui.theme.focusableRow
import java.util.Locale

fun formatPoints(p: Double): String = String.format(Locale.US, "%.2f", p)

/**
 * Me (blue) vs opponent (red). Compact (Home) shows the totals, a lead bar and each side's top
 * scorers; pressing OK on it ([onClick]) is how Home switches to the full lineup.
 */
@Composable
fun MatchupCard(m: FantasyMatchup, modifier: Modifier = Modifier, compact: Boolean = false, onClick: (() -> Unit)? = null) {
    val body: @Composable ColumnScope.() -> Unit = {
        Text("${m.leagueName} · Week ${m.week}", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamTotal(m.me, SofaColors.Blue, leading = m.opponent == null || m.me.points >= m.opponent.points, Modifier.weight(1f), alignEnd = false, compact = compact)
            Text("vs", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextMuted, modifier = Modifier.padding(horizontal = 16.dp))
            if (m.opponent != null) {
                TeamTotal(m.opponent, SofaColors.Red, leading = m.opponent.points > m.me.points, Modifier.weight(1f), alignEnd = true, compact = compact)
            } else {
                Text("Bye week", style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
        m.opponent?.let { LeadBar(m.me.points, it.points) }
        val top = if (compact) COMPACT_STARTERS else Int.MAX_VALUE
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            StarterList(m.me, Modifier.weight(1f), alignEnd = false, limit = top, showTeam = !compact)
            Spacer(Modifier.width(if (compact) 20.dp else 32.dp))
            m.opponent?.let { StarterList(it, Modifier.weight(1f), alignEnd = true, limit = top, showTeam = !compact) } ?: Spacer(Modifier.weight(1f))
        }
        if (onClick != null) {
            Text(
                if (compact) "Press OK for the full lineup" else "Press OK to show less",
                style = MaterialTheme.typography.labelMedium, color = SofaColors.TextMuted,
            )
        }
    }
    if (onClick != null) {
        SofaCard(onClick = onClick, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
        }
    } else {
        Column(
            modifier.fillMaxWidth().focusableRow().background(SofaColors.Surface, SofaShape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = body,
        )
    }
}

private const val COMPACT_STARTERS = 3

/** Share of the combined points: blue fills from the left, red from the right. */
@Composable
private fun LeadBar(me: Double, opponent: Double) {
    val total = me + opponent
    val target = if (total <= 0.0) 0.5f else (me / total).toFloat().coerceIn(0.03f, 0.97f)
    val share by animateFloatAsState(target, label = "lead")
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
        Box(Modifier.weight(share).fillMaxHeight().background(SofaColors.Blue))
        Box(Modifier.width(3.dp).fillMaxHeight().background(SofaColors.Surface))
        Box(Modifier.weight(1f - share).fillMaxHeight().background(SofaColors.Red))
    }
}

@Composable
private fun TeamTotal(team: FantasyTeam, color: Color, leading: Boolean, modifier: Modifier, alignEnd: Boolean, compact: Boolean = false) {
    val pointsColor by animateColorAsState(if (leading) SofaColors.TextPrimary else SofaColors.TextSecondary, label = "pts")
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(team.teamName, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            formatPoints(team.points), style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium, color = pointsColor,
            modifier = Modifier.scorePulse(team.points, color, onlyIncrease = false).padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun StarterList(team: FantasyTeam, modifier: Modifier, alignEnd: Boolean, limit: Int = Int.MAX_VALUE, showTeam: Boolean = true) {
    val shown = if (limit < team.starters.size) team.starters.sortedByDescending { it.points }.take(limit) else team.starters
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(s.player.position ?: "", style = MaterialTheme.typography.labelMedium, color = SofaColors.TextMuted, modifier = Modifier.width(40.dp))
                Text(
                    listOfNotNull(s.player.name, s.player.team?.takeIf { showTeam && it != s.player.id }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(formatPoints(s.points), style = MaterialTheme.typography.titleSmall, color = SofaColors.TextPrimary, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start)
            }
        }
    }
}
