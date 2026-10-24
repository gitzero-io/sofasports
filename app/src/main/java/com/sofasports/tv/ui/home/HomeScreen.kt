package com.sofasports.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.ui.components.GameCard
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.rememberNowMs
import com.sofasports.tv.ui.fantasy.MatchupCard
import com.sofasports.tv.ui.fantasy.SleeperLinkForm
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.SofaShape
import com.sofasports.tv.ui.theme.focusableRow
import com.sofasports.tv.ui.update.UpdateBanner
import com.sofasports.tv.domain.PickEvaluator
import com.sofasports.tv.ui.picks.PickRow
import com.sofasports.tv.ui.picks.sortForDisplay

@Composable
fun HomeScreen(
    onOpenGame: (String) -> Unit,
    onOpenScores: () -> Unit,
    onAddEspnLeague: () -> Unit,
    onOpenPicks: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs(1_000)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        ui.announcement?.let { msg ->
            item(key = "announce") {
                Text(
                    msg, style = MaterialTheme.typography.titleMedium, color = SofaColors.Background,
                    modifier = Modifier.fillMaxWidth().focusableRow().background(SofaColors.Live, SofaShape).padding(14.dp),
                )
            }
        }
        item(key = "update") { UpdateBanner() }
        // Hidden entirely once skipped (until linked from Settings), so Home is just scores.
        if (ui.fantasyEnabled && (ui.fantasy.linked || !ui.fantasyPromptSkipped)) {
            item(key = "fantasy") { FantasySection(ui, vm, onAddEspnLeague) }
        }
        if (ui.picks.isNotEmpty()) {
            item(key = "picks") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Picks · ${PickEvaluator.hitting(ui.picks)} of ${ui.picks.size} hitting",
                        style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextPrimary,
                    )
                    Column(Modifier.fillMaxWidth().background(SofaColors.Surface, SofaShape)) {
                        sortForDisplay(ui.picks).take(5).forEach { PickRow(it, Modifier.focusableRow(SofaShape)) }
                    }
                    SofaButton("All picks", onClick = onOpenPicks)
                }
            }
        }
        item(key = "mine") {
            GameRow(
                title = "Your teams",
                games = ui.myGames,
                empty = when {
                    ui.scoresLoading -> "Loading scores…"
                    ui.favorites.isEmpty() -> "No favorite teams yet. Pick some in Settings."
                    else -> "Your teams don't play this week."
                },
                ui = ui, now = now, onOpenGame = onOpenGame,
            )
        }
        if (ui.closeGames.isNotEmpty()) {
            item(key = "close") { GameRow("Close games right now", ui.closeGames, "", ui, now, onOpenGame) }
        }
        item(key = "all") { SofaButton("All scores", onClick = onOpenScores) }
    }
}

@Composable
private fun FantasySection(ui: HomeUi, vm: HomeViewModel, onAddEspnLeague: () -> Unit) {
    val f = ui.fantasy
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Fantasy", style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextPrimary)
        when {
            !f.linked -> Column(
                Modifier.fillMaxWidth().background(SofaColors.Surface, SofaShape).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Link Sleeper or add an ESPN league to see your matchup live, here and in the overlay.", style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary)
                SleeperLinkForm(onLink = vm::link)
                SpacedRow {
                    SofaButton("Add ESPN league", onClick = onAddEspnLeague)
                    SofaButton("Skip for now", onClick = vm::skipFantasyPrompt)
                    Text("You can do this any time in Settings.", style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextMuted)
                }
            }
            f.leagues.isEmpty() -> Text(
                when {
                    f.loading -> "Loading your leagues…"
                    f.error != null -> "Couldn't load Sleeper leagues: ${f.error}"
                    else -> "${f.username} has no NFL leagues this season. You can change the username in Settings."
                },
                style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary,
            )
            else -> {
                if (f.leagues.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                        items(f.leagues, key = { it.id }) { league ->
                            SofaButton("${league.source.label} · ${league.name}", onClick = { vm.selectLeague(league.id) }, selected = league.id == f.selectedLeagueId)
                        }
                    }
                }
                val m = f.matchup
                when {
                    m != null -> MatchupCard(m)
                    f.loading -> Text("Loading matchup…", style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary)
                    else -> Text(
                        f.error ?: "No matchup this week in ${f.selectedLeague?.name ?: "this league"}.",
                        style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameRow(title: String, games: List<Game>, empty: String, ui: HomeUi, now: Long, onOpenGame: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextPrimary)
        if (games.isEmpty()) {
            Text(empty, style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                items(games, key = { it.id }) { g ->
                    GameCard(
                        g, ui.favorites, ui.closeRule, if (g.state == GameState.PRE) now else 0L,
                        onClick = { if (ui.gameCenterEnabled) onOpenGame(g.id) },
                        modifier = Modifier.width(420.dp),
                        showOdds = ui.showOdds,
                    )
                }
            }
        }
    }
}
