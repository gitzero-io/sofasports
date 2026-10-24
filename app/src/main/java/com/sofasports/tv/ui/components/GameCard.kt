package com.sofasports.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.config.CloseGameRule
import com.sofasports.tv.domain.CloseGame
import com.sofasports.tv.domain.Countdown
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.Perspective
import com.sofasports.tv.domain.TeamSide
import com.sofasports.tv.ui.theme.SofaColors

/** Blue for my side, red for the opponent (Red vs Blue rule). */
fun sideColor(team: TeamSide, game: Game, favorites: List<String>): Color =
    if (Perspective.mySide(game, favorites).id == team.id) SofaColors.Blue else SofaColors.Red

@Composable
fun GameCard(
    game: Game,
    favorites: List<String>,
    closeRule: CloseGameRule,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isClose = CloseGame.isClose(game, closeRule)
    SofaCard(onClick = onClick, modifier = modifier, containerColor = if (isClose) SofaColors.SurfaceRaised else SofaColors.Surface) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLine(game, isClose, nowMs)
            TeamRow(game.away, game, favorites)
            TeamRow(game.home, game, favorites)
            val last = game.situation?.lastPlay
            if (game.state == GameState.LIVE && !last.isNullOrBlank()) {
                Text(last, style = MaterialTheme.typography.bodySmall, color = SofaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatusLine(game: Game, isClose: Boolean, nowMs: Long) {
    val label = MaterialTheme.typography.labelLarge
    SpacedRow(gap = 8) {
        when (game.state) {
            GameState.LIVE -> {
                Badge("LIVE", SofaColors.Live)
                Text(game.shortDetail, style = label, color = SofaColors.TextPrimary, maxLines = 1)
                if (isClose) Badge("CLOSE", SofaColors.Close, outlined = true)
                Spacer(Modifier.weight(1f))
                game.situation?.downDistance?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            GameState.PRE -> {
                val countdown = Countdown.format(nowMs, game.startEpochMs)
                Text(
                    listOfNotNull(formatKickoff(game.startEpochMs), game.broadcast).joinToString(" · "),
                    style = label, color = SofaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (countdown != null) Text(countdown, style = label, color = SofaColors.TextPrimary, maxLines = 1)
            }
            else -> {
                Text(game.shortDetail.ifBlank { "Final" }, style = label, color = SofaColors.TextSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                game.broadcast?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun TeamRow(team: TeamSide, game: Game, favorites: List<String>) {
    val color = sideColor(team, game, favorites)
    val other = if (team.id == game.home.id) game.away else game.home
    val winning = (team.score ?: 0) > (other.score ?: 0)
    val dim = game.state == GameState.FINAL && !winning
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).size(width = 6.dp, height = 36.dp).background(color, MaterialTheme.shapes.small))
        Spacer(Modifier.width(10.dp))
        TeamLogo(team.logoUrl, team.name, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(team.abbr, style = MaterialTheme.typography.headlineSmall, color = if (dim) SofaColors.TextMuted else SofaColors.TextPrimary)
                if (game.state == GameState.LIVE && game.situation?.possessionTeamId == team.id) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(10.dp).background(SofaColors.Live, CircleShape))
                }
            }
            team.record?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted) }
        }
        Text(
            team.score?.toString() ?: "",
            style = MaterialTheme.typography.displaySmall,
            color = if (dim) SofaColors.TextMuted else SofaColors.TextPrimary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
