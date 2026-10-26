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
import com.sofasports.tv.domain.BetStatus
import com.sofasports.tv.domain.Betting
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameOdds
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.WatchOption
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.Periods
import com.sofasports.tv.domain.Play
import com.sofasports.tv.domain.Sport
import com.sofasports.tv.domain.TeamSide
import com.sofasports.tv.domain.WinProbability
import com.sofasports.tv.ui.components.Badge
import com.sofasports.tv.ui.components.MessageBox
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.TeamLogo
import com.sofasports.tv.ui.components.formatKickoff
import com.sofasports.tv.ui.components.rememberNowMs
import com.sofasports.tv.ui.components.scorePulse
import com.sofasports.tv.domain.Countdown
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
        if (summary.game.state != GameState.FINAL) {
            WatchRow(summary.game, vm.watchOptions(summary.game, ui.watchApp), onWatch = vm::watch, onSearch = { vm.findOnGoogleTv(summary.game) })
        }
        SpacedRow {
            GameTab.entries.forEach { t ->
                SofaButton(t.label, onClick = { tab = t }, selected = tab == t, modifier = if (t == GameTab.SUMMARY) Modifier.focusRequester(firstTab) else Modifier)
            }
            Spacer(Modifier.weight(1f))
            val pinned = ui.pinnedGameId == summary.game.ref.key
            SofaButton(if (pinned) "Pinned to overlay ✓" else "Pin to overlay", onClick = vm::togglePin, selected = pinned)
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (tab) {
                GameTab.SUMMARY -> summaryTab(summary, ui.favorites, ui.showWinProbability, ui.showOdds)
                GameTab.BOX -> boxTab(summary)
                GameTab.PLAYS -> playsTab(summary)
            }
            item { Spacer(Modifier.padding(24.dp)) }
        }
    }
}

/** "On NBC · Peacock   [Watch on YouTube TV] [Watch on Peacock] [Find on Google TV]" */
@Composable
fun WatchRow(game: Game, options: List<WatchOption>, onWatch: (WatchOption) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    SpacedRow(modifier) {
        val networks = game.networks.ifEmpty { listOfNotNull(game.broadcast) }
        if (networks.isNotEmpty()) {
            Text("On ${networks.joinToString(" · ")}", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
        }
        options.forEachIndexed { i, o -> SofaButton("Watch on ${o.label}", onClick = { onWatch(o) }, selected = i == 0) }
        SofaButton("Find on Google TV", onClick = onSearch)
        if (options.isEmpty()) {
            Text("Pick your live TV app in Settings → Watch games", style = MaterialTheme.typography.bodySmall, color = SofaColors.TextMuted)
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
                GameState.PRE -> {
                    val now = rememberNowMs(1_000)
                    Text(formatKickoff(game.startEpochMs), style = MaterialTheme.typography.titleLarge, color = SofaColors.TextSecondary)
                    val word = Countdown.startWord(game.league)
                    Countdown.format(now, game.startEpochMs, word)?.let {
                        Text("$word $it", style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary)
                    }
                }
                else -> Text(game.shortDetail.ifBlank { "Final" }, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextSecondary)
            }
            game.situation?.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary) }
        }
        HeaderTeam(game.home, game, favorites, Modifier.weight(1f), alignEnd = true)
    }
}

@Composable
private fun HeaderTeam(team: TeamSide, game: Game, favorites: List<String>, modifier: Modifier, alignEnd: Boolean) {
    val color = sideColor(team, game, favorites)
    val score = @Composable {
        Text(
            team.score?.toString() ?: "", style = MaterialTheme.typography.displayLarge, color = SofaColors.TextPrimary,
            modifier = Modifier.scorePulse(team.score?.toDouble(), color).padding(horizontal = 8.dp),
        )
    }
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

private fun LazyListScope.summaryTab(s: GameSummary, favorites: List<String>, showWp: Boolean, showOdds: Boolean) {
    if (showOdds) s.game.odds?.let { o -> item(key = "odds") { LineCard(o, s.game) } }
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
    // Basketball has no scoring-play list (every basket would be on it).
    if (s.game.league.sport != Sport.BASKETBALL) {
        item(key = "scoring-h") { SubHeader(scoringTitle(s.game.league.sport)) }
        if (s.scoringPlays.isEmpty()) item(key = "noscore") { InfoRow("", "No scoring yet") }
        items(s.scoringPlays, key = { "sp-${it.id}" }) { PlayRow(it, s.game) }
    }
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
        // Basketball and hockey box scores are 12+ columns wide; keep the ones that fit a TV row.
        val cols = boxColumns(s.game.league.sport, table.labels)
        item(key = "pt-${table.teamAbbr}-${table.category}") { SubHeader("${table.teamAbbr} · ${table.category}") }
        item(key = "pl-${table.teamAbbr}-${table.category}") { StatLine("", cols.map { table.labels[it] }, header = true) }
        items(table.rows.take(rowsFor(s.game.league.sport)), key = { "pr-${table.teamAbbr}-${table.category}-${it.name}" }) { r ->
            StatLine(r.name, cols.map { r.stats.getOrElse(it) { "" } })
        }
    }
}

private fun LazyListScope.playsTab(s: GameSummary) {
    val drives = s.drives.asReversed()
    if (drives.isEmpty() && s.plays.isNotEmpty()) {
        items(s.plays.asReversed(), key = { "p-${it.id}" }) { PlayRow(it, s.game) }
        return
    }
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
        Text(
            "${play.periodLabel ?: Periods.label(game.league, play.period)} ${play.clock}".trim(),
            style = MaterialTheme.typography.labelLarge, color = SofaColors.TextMuted, modifier = Modifier.width(110.dp),
        )
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

/** DraftKings line plus how each side and the total are doing against it right now. */
@Composable
private fun LineCard(o: GameOdds, game: Game) {
    Column(Modifier.fillMaxWidth().focusableRow().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpacedRow {
            Badge(Betting.providerTag(o.provider), SofaColors.Positive)
            Text("${o.provider} line", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary)
        }
        game.teams.forEach { t ->
            val spread = Betting.spreadFor(o, game, t.abbr)
            val ml = if (t.isHome) o.homeMoneyline else o.awayMoneyline
            val status = spread?.let { Betting.status(Betting.coverMargin(game, t.abbr, it), game.state) }
            Text(
                listOfNotNull(
                    t.abbr,
                    spread?.let { Betting.formatLine(it) },
                    ml?.let { "ML $it" },
                    status?.let { statusWord(it) },
                ).joinToString("   "),
                style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary,
            )
        }
        o.overUnder?.let { ou ->
            val total = Betting.total(game)
            val word = if (total == null || game.state == GameState.PRE) null else when {
                total > ou -> if (game.state == GameState.FINAL) "went OVER ($total)" else "already OVER ($total)"
                total < ou -> if (game.state == GameState.FINAL) "stayed UNDER ($total)" else "$total so far, ${Betting.formatNumber(ou - total)} to go for the over"
                else -> "on the number"
            }
            Text(listOfNotNull("Total ${Betting.formatNumber(ou)}", word).joinToString("   "), style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary)
        }
    }
}

private fun scoringTitle(sport: Sport) = if (sport == Sport.SOCCER || sport == Sport.HOCKEY) "Goals" else "Scoring plays"

/** Columns worth showing per sport, by label; everything when the sport isn't listed or labels don't match. */
private val preferredColumns = mapOf(
    Sport.BASKETBALL to listOf("MIN", "PTS", "REB", "AST", "FG", "3PT", "FT", "STL", "BLK", "TO", "+/-"),
    Sport.HOCKEY to listOf("G", "A", "+/-", "SOG", "HT", "BS", "TOI", "PIM", "GA", "SA", "SV", "SV%"),
    Sport.BASEBALL to listOf("H-AB", "R", "H", "RBI", "HR", "BB", "K", "AVG", "IP", "ER", "PC-ST", "ERA"),
)

private fun boxColumns(sport: Sport, labels: List<String>): List<Int> {
    val wanted = preferredColumns[sport] ?: return labels.indices.toList()
    val picked = wanted.mapNotNull { w -> labels.indexOf(w).takeIf { it >= 0 } }
    return picked.ifEmpty { labels.indices.take(10) }
}

private fun rowsFor(sport: Sport) = when (sport) {
    Sport.FOOTBALL -> 6
    Sport.BASEBALL -> 10
    else -> 12
}

private fun statusWord(s: BetStatus) = when (s) {
    BetStatus.WINNING -> "covering"
    BetStatus.LOSING -> "not covering"
    BetStatus.PUSHING -> "on the number"
    BetStatus.WON -> "covered"
    BetStatus.LOST -> "didn't cover"
    BetStatus.PUSH -> "push"
    BetStatus.PENDING -> null
}
