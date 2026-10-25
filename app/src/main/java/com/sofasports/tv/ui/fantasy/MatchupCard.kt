package com.sofasports.tv.ui.fantasy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.sofasports.tv.domain.FantasyPlayer
import com.sofasports.tv.domain.MatchupLive
import com.sofasports.tv.domain.NflTeams
import com.sofasports.tv.domain.PlayerGamePhase
import com.sofasports.tv.domain.StarterLive
import com.sofasports.tv.domain.TeamLive
import com.sofasports.tv.ui.components.SofaCard
import com.sofasports.tv.ui.components.formatKickoff
import com.sofasports.tv.ui.components.scorePulse
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.SofaShape
import com.sofasports.tv.ui.theme.focusableRow
import java.util.Locale

fun formatPoints(p: Double): String = String.format(Locale.US, "%.2f", p)
private fun formatProj(p: Double): String = String.format(Locale.US, "%.1f", p)

/**
 * Me (blue) vs opponent (red), laid out like ESPN's matchup screen: totals with live projections,
 * a win-probability bar between the teams, then each lineup slot side by side with photos.
 * Compact (Home) shows the top three scorers a side; pressing OK ([onClick]) toggles the full lineup.
 */
@Composable
fun MatchupCard(live: MatchupLive, modifier: Modifier = Modifier, compact: Boolean = false, onClick: (() -> Unit)? = null) {
    val body: @Composable ColumnScope.() -> Unit = {
        MatchupSummary(live, compact)
        if (compact) TopScorers(live) else Lineups(live)
        if (onClick != null) {
            Text(
                if (compact) "Press OK for the full matchup" else "Press OK to show less",
                style = MaterialTheme.typography.labelMedium, color = SofaColors.TextMuted,
            )
        }
    }
    if (onClick != null) {
        SofaCard(onClick = onClick, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = body)
        }
    } else {
        Column(
            modifier.fillMaxWidth().focusableRow().background(SofaColors.Surface, SofaShape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = body,
        )
    }
}

/**
 * The Fantasy page: the summary, then one row per lineup slot as separate list items, so the
 * remote can move down the lineup and the list scrolls with it.
 */
fun LazyListScope.matchupItems(live: MatchupLive) {
    item(key = "summary") {
        Column(
            Modifier.fillMaxWidth().focusableRow().background(SofaColors.Surface, SofaShape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { MatchupSummary(live, compact = false) }
    }
    val mine = live.me.starters
    val theirs = live.opponent?.starters.orEmpty()
    items(maxOf(mine.size, theirs.size), key = { "slot-$it" }) { i ->
        LineupRow(mine.getOrNull(i), theirs.getOrNull(i), Modifier.focusableRow())
    }
}

/** League and week, both teams' totals and projections, win probability and who's still to play. */
@Composable
private fun MatchupSummary(live: MatchupLive, compact: Boolean) {
    val m = live.matchup
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "${m.leagueName} · Week ${m.week}".uppercase(),
            style = MaterialTheme.typography.labelLarge, color = SofaColors.TextSecondary,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val opp = live.opponent
            TeamHeader(live.me, SofaColors.Blue, leading = opp == null || m.me.points >= opp.team.points, compact, Modifier.weight(1f), alignEnd = false)
            Text("vs", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextMuted, modifier = Modifier.padding(horizontal = 16.dp))
            if (opp != null) {
                TeamHeader(opp, SofaColors.Red, leading = opp.team.points > m.me.points, compact, Modifier.weight(1f), alignEnd = true)
            } else {
                Text("Bye week", style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
        live.winProbability?.let { WinProbabilityBar(it) }
        live.opponent?.let { opp ->
            Row(Modifier.fillMaxWidth()) {
                PlayCounts(live.me, Modifier.weight(1f), alignEnd = false)
                PlayCounts(opp, Modifier.weight(1f), alignEnd = true)
            }
        }
    }
}

@Composable
private fun TeamHeader(t: TeamLive, color: Color, leading: Boolean, compact: Boolean, modifier: Modifier, alignEnd: Boolean) {
    val pointsColor by animateColorAsState(if (leading) SofaColors.TextPrimary else SofaColors.TextSecondary, label = "pts")
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(t.team.teamName, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        t.team.record?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextMuted) }
        Text(
            formatPoints(t.team.points),
            style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
            color = pointsColor,
            modifier = Modifier.scorePulse(t.team.points, color, onlyIncrease = false).padding(horizontal = 8.dp),
        )
        t.projected?.let {
            Text("Proj ${formatProj(it)}", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary)
        }
    }
}

/** Blue fills from the left with my chance to win, red from the right with theirs. */
@Composable
private fun WinProbabilityBar(mine: Double) {
    val target = mine.toFloat().coerceIn(0f, 1f)
    val share by animateFloatAsState(target, label = "win")
    val myPct = Math.round(mine * 100).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$myPct%", style = MaterialTheme.typography.titleLarge, color = SofaColors.BlueBright)
            Text(
                "WIN PROBABILITY", style = MaterialTheme.typography.labelMedium, color = SofaColors.TextMuted,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Text("${100 - myPct}%", style = MaterialTheme.typography.titleLarge, color = SofaColors.RedBright)
        }
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            // Weights can't be 0; a 100% side still leaves a hairline of the other colour.
            Box(Modifier.weight(share.coerceIn(0.005f, 0.995f)).fillMaxHeight().background(SofaColors.Blue))
            Box(Modifier.width(3.dp).fillMaxHeight().background(SofaColors.Surface))
            Box(Modifier.weight((1f - share).coerceIn(0.005f, 0.995f)).fillMaxHeight().background(SofaColors.Red))
        }
    }
}

/** "3 yet to play · 2 playing" */
@Composable
private fun PlayCounts(t: TeamLive, modifier: Modifier, alignEnd: Boolean) {
    val parts = listOfNotNull(
        t.count(PlayerGamePhase.PRE).takeIf { it > 0 }?.let { "$it yet to play" },
        t.count(PlayerGamePhase.LIVE).takeIf { it > 0 }?.let { "$it playing" },
    ).ifEmpty { listOf("All done") }
    Text(
        parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary,
        modifier = modifier, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
    )
}

@Composable
private fun TopScorers(live: MatchupLive) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            live.me.starters.sortedByDescending { it.starter.points }.take(COMPACT).forEach { PlayerCell(it, alignEnd = false, small = true) }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            live.opponent?.starters?.sortedByDescending { it.starter.points }?.take(COMPACT)?.forEach { PlayerCell(it, alignEnd = true, small = true) }
        }
    }
}

/** Slot by slot: my player, the slot, their player. */
@Composable
private fun Lineups(live: MatchupLive) {
    val mine = live.me.starters
    val theirs = live.opponent?.starters.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until maxOf(mine.size, theirs.size)) LineupRow(mine.getOrNull(i), theirs.getOrNull(i))
    }
}

@Composable
private fun LineupRow(a: StarterLive?, b: StarterLive?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(SofaColors.Surface, SofaShape).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { a?.let { PlayerCell(it, alignEnd = false) } }
        Text(
            (a?.starter?.slot ?: b?.starter?.slot ?: a?.starter?.player?.position ?: "").uppercase(),
            style = MaterialTheme.typography.labelLarge, color = SofaColors.TextMuted, textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
        Box(Modifier.weight(1f)) { b?.let { PlayerCell(it, alignEnd = true) } }
    }
}

/** Photo, name, injury tag, game status, points and projection; mirrored for the opponent. */
@Composable
private fun PlayerCell(s: StarterLive, alignEnd: Boolean, small: Boolean = false) {
    val p = s.starter.player
    val avatar = @Composable { PlayerAvatar(p, if (small) 40.dp else 48.dp, live = s.phase == PlayerGamePhase.LIVE) }
    val info = @Composable { mod: Modifier ->
        Column(mod, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (alignEnd) p.injury?.let { InjuryTag(it) }
                Text(
                    p.name, style = if (small) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    color = SofaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (!alignEnd) p.injury?.let { InjuryTag(it) }
            }
            if (!small) {
                Text(
                    gameLine(s), style = MaterialTheme.typography.labelMedium,
                    color = if (s.phase == PlayerGamePhase.LIVE) SofaColors.Live else SofaColors.TextMuted, maxLines = 1,
                )
            }
        }
    }
    val points = @Composable {
        Column(horizontalAlignment = if (alignEnd) Alignment.Start else Alignment.End) {
            Text(
                formatPoints(s.starter.points),
                style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = if (s.phase == PlayerGamePhase.PRE) SofaColors.TextSecondary else SofaColors.TextPrimary,
                modifier = Modifier.scorePulse(s.starter.points, if (alignEnd) SofaColors.Red else SofaColors.Blue).padding(horizontal = 4.dp),
            )
            s.starter.projected?.let {
                Text("proj ${formatProj(it)}", style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (alignEnd) {
            points(); info(Modifier.weight(1f)); avatar()
        } else {
            avatar(); info(Modifier.weight(1f)); points()
        }
    }
}

/** Headshot in a circle; team logo for defenses or when there's no photo. A live dot when his game is on. */
@Composable
private fun PlayerAvatar(p: FantasyPlayer, size: Dp, live: Boolean) {
    val logo = p.team?.let { NflTeams.find(NflTeams.scoreboardAbbr(it))?.logoUrl }
    Box {
        AsyncImage(
            model = p.headshotUrl ?: logo,
            contentDescription = p.name,
            contentScale = if (p.headshotUrl != null) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.size(size).clip(CircleShape).background(SofaColors.SurfaceFocused, CircleShape)
                .padding(if (p.headshotUrl != null) 0.dp else 6.dp),
        )
        if (live) Box(Modifier.align(Alignment.BottomEnd).size(12.dp).background(SofaColors.Live, CircleShape))
    }
}

/** Q / D / O / IR in amber: status colours never use red or blue. */
@Composable
private fun InjuryTag(tag: String) {
    Text(
        tag, style = MaterialTheme.typography.labelSmall, color = SofaColors.Background,
        modifier = Modifier.background(if (tag == "Q") SofaColors.Live else SofaColors.TextSecondary, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
    )
}

/** "WR · HOU @ JAX · 3rd 6:19", "Sun 1:00 PM", "Final", "Bye". */
private fun gameLine(s: StarterLive): String {
    val p = s.starter.player
    val g = s.game
    val matchup = g?.let { game ->
        val abbr = p.team?.let(NflTeams::scoreboardAbbr)
        when (abbr) {
            game.home.abbr -> "vs ${game.away.abbr}"
            game.away.abbr -> "@ ${game.home.abbr}"
            else -> null
        }
    }
    val status = when (s.phase) {
        PlayerGamePhase.PRE -> g?.let { formatKickoff(it.startEpochMs) }
        PlayerGamePhase.LIVE -> g?.shortDetail
        PlayerGamePhase.DONE -> "Final"
        PlayerGamePhase.NO_GAME -> if (p.team == null) null else "Bye"
    }
    return listOfNotNull(p.position, p.team, matchup, status).joinToString(" · ")
}

private const val COMPACT = 3
