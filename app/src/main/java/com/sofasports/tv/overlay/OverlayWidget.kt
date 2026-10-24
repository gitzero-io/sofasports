package com.sofasports.tv.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.sofasports.tv.domain.CloseGame
import com.sofasports.tv.domain.Countdown
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.Perspective
import com.sofasports.tv.ui.components.Badge
import com.sofasports.tv.ui.components.rememberNowMs
import com.sofasports.tv.ui.fantasy.formatPoints
import com.sofasports.tv.ui.theme.SofaColors

/** The corner card. Kept small (≈ 320dp) and high-contrast so it reads over any video. */
@Composable
fun OverlayWidget(state: OverlayState) {
    Column(
        Modifier
            .widthIn(min = 260.dp, max = 340.dp)
            .background(SofaColors.Background, RoundedCornerShape(12.dp))
            .border(1.dp, SofaColors.Outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val m = state.matchup
        val g = state.game
        if (m == null && g == null) {
            Text("SOFA SPORTS", style = MaterialTheme.typography.labelLarge, color = SofaColors.TextSecondary)
            Text("Pin a game or link Sleeper", style = MaterialTheme.typography.bodySmall, color = SofaColors.TextMuted)
            return@Column
        }
        if (m != null) MatchupRows(m)
        if (m != null && g != null) Box(Modifier.fillMaxWidth().height(1.dp).background(SofaColors.Outline))
        if (g != null) GameRows(g, state)
    }
}

@Composable
private fun MatchupRows(m: FantasyMatchup) {
    Text(m.leagueName.uppercase(), style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    OverlayScoreRow(m.me.teamName, formatPoints(m.me.points), SofaColors.Blue, m.opponent != null && m.me.points >= m.opponent.points)
    if (m.opponent != null) {
        OverlayScoreRow(m.opponent.teamName, formatPoints(m.opponent.points), SofaColors.Red, m.opponent.points > m.me.points)
    }
}

@Composable
private fun GameRows(g: Game, state: OverlayState) {
    val me = Perspective.mySide(g, state.favorites)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (g.state) {
            GameState.LIVE -> {
                Badge("LIVE", SofaColors.Live)
                Text(g.shortDetail, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextPrimary, maxLines = 1)
                if (CloseGame.isClose(g, state.closeRule)) Badge("CLOSE", SofaColors.Close, outlined = true)
            }
            GameState.PRE -> {
                val now = rememberNowMs(Countdown.tickMs(System.currentTimeMillis(), g.startEpochMs))
                Text(Countdown.format(now, g.startEpochMs)?.let { "Kickoff $it" } ?: g.shortDetail, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1)
            }
            else -> Text(g.shortDetail.ifBlank { "Final" }, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1)
        }
    }
    g.teams.forEach { t ->
        val other = if (t.id == g.home.id) g.away else g.home
        OverlayScoreRow(
            t.abbr, t.score?.toString() ?: "–",
            if (t.id == me.id) SofaColors.Blue else SofaColors.Red,
            leading = (t.score ?: 0) > (other.score ?: 0),
        )
    }
}

/** Colour bar + name on the left, score on the right; the leader's score is brighter. */
@Composable
internal fun OverlayScoreRow(name: String, score: String, sideColor: Color, leading: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(22.dp).background(sideColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = SofaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(score, style = MaterialTheme.typography.headlineSmall, color = if (leading) SofaColors.TextPrimary else SofaColors.TextSecondary, modifier = Modifier.padding(start = 8.dp))
    }
}
