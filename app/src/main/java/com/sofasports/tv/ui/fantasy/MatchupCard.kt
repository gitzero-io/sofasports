package com.sofasports.tv.ui.fantasy

import androidx.compose.animation.animateColorAsState
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

/** Me (blue) vs opponent (red), with each side's starters. */
@Composable
fun MatchupCard(m: FantasyMatchup, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().focusableRow().background(SofaColors.Surface, SofaShape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${m.leagueName} · Week ${m.week}", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamTotal(m.me, SofaColors.Blue, leading = m.opponent == null || m.me.points >= m.opponent.points, Modifier.weight(1f), alignEnd = false)
            Text("vs", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextMuted, modifier = Modifier.padding(horizontal = 16.dp))
            if (m.opponent != null) {
                TeamTotal(m.opponent, SofaColors.Red, leading = m.opponent.points > m.me.points, Modifier.weight(1f), alignEnd = true)
            } else {
                Text("Bye week", style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            StarterList(m.me, Modifier.weight(1f), alignEnd = false)
            Spacer(Modifier.width(32.dp))
            m.opponent?.let { StarterList(it, Modifier.weight(1f), alignEnd = true) } ?: Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TeamTotal(team: FantasyTeam, color: Color, leading: Boolean, modifier: Modifier, alignEnd: Boolean) {
    val pointsColor by animateColorAsState(if (leading) SofaColors.TextPrimary else SofaColors.TextSecondary, label = "pts")
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(team.teamName, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            formatPoints(team.points), style = MaterialTheme.typography.displayMedium, color = pointsColor,
            modifier = Modifier.scorePulse(team.points, color, onlyIncrease = false).padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun StarterList(team: FantasyTeam, modifier: Modifier, alignEnd: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        team.starters.forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(s.player.position ?: "", style = MaterialTheme.typography.labelMedium, color = SofaColors.TextMuted, modifier = Modifier.width(40.dp))
                Text(
                    listOfNotNull(s.player.name, s.player.team?.takeIf { it != s.player.id }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(formatPoints(s.points), style = MaterialTheme.typography.titleSmall, color = SofaColors.TextPrimary, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start)
            }
        }
    }
}
