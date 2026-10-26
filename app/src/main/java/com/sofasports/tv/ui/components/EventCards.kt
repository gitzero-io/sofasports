package com.sofasports.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.Countdown
import com.sofasports.tv.domain.FightCard
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.Race
import com.sofasports.tv.domain.RaceEntry
import com.sofasports.tv.ui.theme.SofaColors

/** One fight card on the scoreboard: the bout in the cage when live, else a favorite's bout, else the main event. */
@Composable
fun FightCardTile(
    card: FightCard,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLeague: Boolean = false,
    favorites: List<String> = emptyList(),
) {
    val mine = card.favoriteBout(favorites)
    val shown = card.live ?: mine ?: card.mainEvent
    SofaCard(onClick = onClick, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EventStatus(card.state, card.startEpochMs, nowMs, if (card.live != null) shown.shortDetail else null, shown.broadcast, card.league.label.takeIf { showLeague })
            Text(card.name, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    when {
                        card.live != null -> "In the cage"
                        mine != null && mine.ref != card.mainEvent.ref -> "Your fighter"
                        else -> "Main event"
                    },
                    shown.note,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1,
            )
            FighterLine(shown, shown.away, favorites)
            FighterLine(shown, shown.home, favorites)
            if (mine != null && card.live != null && mine.ref != card.live?.ref) {
                Text("Your fighter: ${mine.away.name} vs ${mine.home.name}", style = MaterialTheme.typography.labelMedium, color = SofaColors.BlueBright, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (card.bouts.size > 1) {
                Text("${card.bouts.size} fights", style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted)
            }
        }
    }
}

@Composable
private fun FighterLine(bout: Game, side: com.sofasports.tv.domain.TeamSide, favorites: List<String>) {
    val lost = bout.state == GameState.FINAL && bout.winnerSide?.let { it.id != side.id } == true
    val mine = favorites.any { bout.keyOf(side).equals(it, ignoreCase = true) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Boxing has no flags or photos; don't leave a gap for one.
        side.logoUrl?.let {
            TeamLogo(it, side.name, size = 28.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                side.name, style = MaterialTheme.typography.titleMedium,
                color = when {
                    mine -> SofaColors.BlueBright
                    lost -> SofaColors.TextMuted
                    else -> SofaColors.TextPrimary
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            side.record?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted) }
        }
        if (side.winner == true) {
            Spacer(Modifier.width(8.dp))
            Badge("WIN", SofaColors.Positive)
        }
    }
}

/** One race weekend: the current session, its top three (or the start time), and where favorite drivers are. */
@Composable
fun RaceCard(
    race: Race,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLeague: Boolean = false,
    favorites: List<String> = emptyList(),
) {
    val mine = race.favorites(favorites)
    SofaCard(onClick = onClick, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EventStatus(race.state, race.startEpochMs, nowMs, race.shortDetail.takeIf { race.state == GameState.LIVE }, race.broadcast, race.league.label.takeIf { showLeague })
            Text(race.name, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(race.session, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1)
            if (race.results.isEmpty()) {
                race.venue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SofaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (mine.isNotEmpty()) {
                    Text("Racing: ${mine.joinToString(", ") { it.name }}", style = MaterialTheme.typography.labelMedium, color = SofaColors.BlueBright, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                val top = race.results.take(3)
                top.forEach { RaceEntryLine(it, mine = mine.any { m -> m.driverId == it.driverId }) }
                // Favorites outside the top three still get a line.
                mine.filter { m -> top.none { it.driverId == m.driverId } }.forEach { m ->
                    race.results.firstOrNull { it.driverId == m.driverId }?.let { RaceEntryLine(it, mine = true) }
                }
            }
        }
    }
}

/** "1  🇮🇹  Kimi Antonelli"; a favorite driver ([mine]) is blue. */
@Composable
fun RaceEntryLine(e: RaceEntry, modifier: Modifier = Modifier, mine: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${e.position}", style = MaterialTheme.typography.titleMedium, color = if (e.position == 1) SofaColors.Close else SofaColors.TextSecondary, modifier = Modifier.width(44.dp))
        TeamLogo(e.flagUrl, null, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(e.name, style = MaterialTheme.typography.bodyLarge, color = if (mine) SofaColors.BlueBright else SofaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "LIVE R2 3:14", "Sat 5:00 PM · ESPN   in 4h 10m" or "Final", with an optional league tag. */
@Composable
private fun EventStatus(state: GameState, startMs: Long, nowMs: Long, liveDetail: String?, broadcast: String?, league: String?) {
    val label = MaterialTheme.typography.labelLarge
    SpacedRow(gap = 8) {
        league?.let { Badge(it, SofaColors.Outline, outlined = true) }
        when (state) {
            GameState.LIVE -> {
                Badge("LIVE", SofaColors.Live)
                liveDetail?.let { Text(it, style = label, color = SofaColors.TextPrimary, maxLines = 1) }
            }
            GameState.PRE -> {
                Text(
                    listOfNotNull(formatKickoff(startMs), broadcast).joinToString(" · "),
                    style = label, color = SofaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Countdown.format(nowMs, startMs, "Starts")?.let { Text(it, style = label, color = SofaColors.TextPrimary, maxLines = 1) }
            }
            GameState.FINAL -> Text("Final", style = label, color = SofaColors.TextSecondary)
            GameState.OTHER -> Text("Postponed", style = label, color = SofaColors.TextSecondary)
        }
    }
}
