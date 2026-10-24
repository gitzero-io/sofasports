package com.sofasports.tv.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.Play
import com.sofasports.tv.domain.TeamSide
import com.sofasports.tv.domain.WinProbability
import com.sofasports.tv.ui.components.Badge
import com.sofasports.tv.ui.components.MessageBox
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.TeamLogo
import com.sofasports.tv.ui.components.formatKickoff
import com.sofasports.tv.ui.components.sideColor
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.focusableRow

private enum class GameTab(val label: String) { SUMMARY("Summary"), BOX("Box score"), PLAYS("Play-by-play") }

@Composable
fun GameCenterScreen(vm: GameCenterViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(GameTab.SUMMARY) }
    val firstTab = remember { FocusRequester() }
    val summary = ui.summary
    LaunchedEffect(summary != null) { if (summary != null) runCatching { firstTab.requestFocus() } }
    if (summary == null) {
        MessageBox(if (ui.error != null) ui.error!! else "Loading game…")
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GameHeader(summary.game, ui.favorites)
        SpacedRow {
            GameTab.entries.forEach { t ->
                SofaButton(t.label, onClick = { tab = t }, selected = tab == t, modifier = if (t == GameTab.SUMMARY) Modifier.focusRequester(firstTab) else Modifier)
            }
            Spacer(Modifier.weight(1f))
            val pinned = ui.pinnedGameId == summary.game.id
            SofaButton(if (pinned) "Pinned to overlay ✓" else "Pin to overlay", onClick = vm::togglePin, selected = pinned)
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (tab) {
                GameTab.SUMMARY -> summaryTab(summary, ui.favorites, ui.showWinProbability)
                GameTab.BOX -> boxTab(summary)
                GameTab.PLAYS -> playsTab(summary)
            }
            item { Spacer(Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun GameHeader(game: Game, favorites: List<String>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeaderTeam(game.away, game, favorites, Modifier.weight(1f), alignEnd = false)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
            when (game.state) {
                GameState.LIVE -> { Badge("LIVE", SofaColors.Live); Text(game.shortDetail, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextPrimary) }
                GameState.PRE -> Text(formatKickoff(game.startEpochMs), style = MaterialTheme.typography.titleLarge, color = SofaColors.TextSecondary)
                else -> Text(game.shortDetail.ifBlank { "Final" }, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextSecondary)
            }
            game.situation?.downDistance?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary) }
        }
        HeaderTeam(game.home, game, favorites, Modifier.weight(1f), alignEnd = true)
    }
}

@Composable
private fun HeaderTeam(team: TeamSide, game: Game, favorites: List<String>, modifier: Modifier, alignEnd: Boolean) {
    val color = sideColor(team, game, favorites)
    val score = @Composable { Text(team.score?.toString() ?: "", style = MaterialTheme.typography.displayLarge, color = SofaColors.TextPrimary) }
    val info = @Composable {
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(team.shortName, style = MaterialTheme.typography.headlineMedium, color = color)
            team.record?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextMuted) }
        }
    }
    Row(modifier, horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        if (alignEnd) { score(); Spacer(Modifier.width(20.dp)); info(); Spacer(Modifier.width(12.dp)); TeamLogo(team.logoUrl, team.name, 72.dp) }
        else { TeamLogo(team.logoUrl, team.name, 72.dp); Spacer(Modifier.width(12.dp)); info(); Spacer(Modifier.width(20.dp)); score() }
    }
}

private fun LazyListScope.summaryTab(s: GameSummary, favorites: List<String>, showWp: Boolean) {
    val wp = if (showWp) WinProbability.display(s.game, s.winProbability, favorites) else null
    if (wp != null) {
        item(key = "wp") {
            Column(Modifier.fillMaxWidth().focusableRow().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpacedRow {
                    Text("Win probability", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary)
                    Text(wp.label, style = MaterialTheme.typography.headlineMedium, color = if (wp.isMine) SofaColors.BlueBright else SofaColors.RedBright)
                }
                WinProbabilityChart(WinProbability.mySeries(s.game, s.winProbability, favorites))
            }
        }
    }
    s.game.situation?.lastPlay?.takeIf { s.game.state == GameState.LIVE }?.let { last ->
        item(key = "last") { InfoRow("Last play", last) }
    }
    item(key = "scoring-h") { SubHeader("Scoring plays") }
    if (s.scoringPlays.isEmpty()) item(key = "noscore") { InfoRow("", "No scoring yet") }
    items(s.scoringPlays, key = { "sp-${it.id}" }) { PlayRow(it, s.game) }
    s.venue?.let { v -> item(key = "venue") { InfoRow("Venue", v) } }
}

private fun LazyListScope.boxTab(s: GameSummary) {
    if (s.teamStats.isEmpty() && s.playerStats.isEmpty()) {
        item { InfoRow("", "Box score appears once the game starts.") }
        return
    }
    item(key = "ts-h") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(s.game.away.abbr, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary, modifier = Modifier.weight(1f))
            Text("Team stats", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary, modifier = Modifier.weight(2f), textAlign = TextAlign.Center)
            Text(s.game.home.abbr, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
    items(s.teamStats, key = { "ts-${it.label}" }) { row ->
        Row(Modifier.fillMaxWidth().focusableRow().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(row.away, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(row.label, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary, modifier = Modifier.weight(2f), textAlign = TextAlign.Center)
            Text(row.home, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
    s.playerStats.forEach { table ->
        item(key = "pt-${table.teamAbbr}-${table.category}") { SubHeader("${table.teamAbbr} · ${table.category}") }
        item(key = "pl-${table.teamAbbr}-${table.category}") { StatLine("", table.labels, header = true) }
        items(table.rows.take(6), key = { "pr-${table.teamAbbr}-${table.category}-${it.name}" }) { StatLine(it.name, it.stats) }
    }
}

private fun LazyListScope.playsTab(s: GameSummary) {
    val drives = s.drives.asReversed()
    if (drives.isEmpty()) {
        item { InfoRow("", "Play-by-play appears once the game starts.") }
        return
    }
    drives.forEach { d ->
        item(key = "d-${d.id}") {
            SubHeader(listOfNotNull(d.teamAbbr, d.result, d.description).joinToString(" · "))
        }
        items(d.plays.asReversed(), key = { "p-${d.id}-${it.id}" }) { PlayRow(it, s.game) }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().focusableRow().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.titleSmall, color = SofaColors.TextMuted, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextPrimary)
    }
}

@Composable
private fun PlayRow(play: Play, game: Game) {
    Row(Modifier.fillMaxWidth().focusableRow().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${if (play.period <= 4) "Q${play.period}" else "OT"} ${play.clock}", style = MaterialTheme.typography.labelLarge, color = SofaColors.TextMuted, modifier = Modifier.width(110.dp))
        Text(
            play.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (play.isScoring) SofaColors.TextPrimary else SofaColors.TextSecondary,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (play.isScoring) {
            Text("${game.away.abbr} ${play.awayScore} – ${play.homeScore} ${game.home.abbr}", style = MaterialTheme.typography.titleSmall, color = SofaColors.Live, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun StatLine(name: String, values: List<String>, header: Boolean = false) {
    val color = if (header) SofaColors.TextMuted else SofaColors.TextPrimary
    val mod = if (header) Modifier.fillMaxWidth().padding(horizontal = 16.dp) else Modifier.fillMaxWidth().focusableRow().padding(horizontal = 16.dp, vertical = 6.dp)
    Row(mod) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(2.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        values.forEach { v -> Text(v, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.End) }
    }
}
