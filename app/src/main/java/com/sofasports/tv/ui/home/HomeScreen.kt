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
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.sofasports.tv.domain.FantasyProjection
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.ui.components.GameCard
import com.sofasports.tv.ui.components.RowLabel
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
import com.sofasports.tv.domain.GameRef
import com.sofasports.tv.ui.components.FightCardTile
import com.sofasports.tv.ui.components.RaceCard

@Composable
fun HomeScreen(
    onOpenGame: (String) -> Unit,
    onOpenEvent: (GameRef) -> Unit,
    onOpenScores: () -> Unit,
    onAddEspnLeague: () -> Unit,
    onOpenPicks: () -> Unit,
    onOpenFantasy: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs(1_000)
    val fantasyLinked = ui.fantasyEnabled && ui.fantasy.linked
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)) {
        ui.announcement?.let { msg ->
            item(key = "announce") {
                Text(
                    msg, style = MaterialTheme.typography.titleMedium, color = SofaColors.Background,
                    modifier = Modifier.fillMaxWidth().focusableRow().background(SofaColors.Live, SofaShape).padding(14.dp),
                )
            }
        }
        item(key = "update") { UpdateBanner() }
        // The two things people open the app for, side by side: my team's game and my matchup.
        item(key = "hero") {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(if (fantasyLinked) 0.42f else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RowLabel(if (ui.myGames.size > 1) "Your teams" else "Your game")
                    val hero = ui.myGames.firstOrNull()
                    if (hero == null) {
                        Text(
                            when {
                                ui.scoresLoading -> "Loading scores…"
                                ui.favorites.isEmpty() -> "No favorite teams yet. Pick some in Settings."
                                ui.weeklyOnly -> "Your teams don't play this week."
                                else -> "None of your teams are playing today."
                            },
                            style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary,
                        )
                    } else {
                        GameCard(
                            hero, ui.favorites, ui.closeRule, if (hero.state == GameState.PRE) now else 0L,
                            onClick = { if (ui.gameCenterEnabled && hero.league.hasGameCenter) onOpenGame(hero.ref.key) },
                            modifier = if (fantasyLinked) Modifier.fillMaxWidth() else Modifier.width(520.dp),
                            showOdds = ui.showOdds,
                            showLeague = ui.multiLeague,
                        )
                        if (hero.state != GameState.FINAL) {
                            // Just the best option here; Game Center lists every way to watch.
                            val best = vm.watchOptions(hero, ui.watchApp).firstOrNull()
                            if (best != null) SofaButton("Watch on ${best.label}", onClick = { vm.watch(best) }, selected = true)
                            else SofaButton("Find on Google TV", onClick = { vm.findOnGoogleTv(hero) })
                        }
                    }
                }
                if (fantasyLinked) {
                    Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RowLabel("Fantasy")
                        FantasySection(ui, vm, onAddEspnLeague, onOpenFantasy)
                    }
                }
            }
        }
        if (ui.myGames.size > 1) {
            item(key = "mine") { GameRow("More of your teams", ui.myGames.drop(1), ui, now, onOpenGame) }
        }
        // Not linked yet: the prompt sits below the scores so it never hides them.
        if (ui.fantasyEnabled && !ui.fantasy.linked && !ui.fantasyPromptSkipped) {
            item(key = "fantasy") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RowLabel("Fantasy")
                    FantasySection(ui, vm, onAddEspnLeague)
                }
            }
        }
        if (ui.picks.isNotEmpty()) {
            item(key = "picks") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RowLabel("Picks", trailing = "${PickEvaluator.hitting(ui.picks)} of ${ui.picks.size} hitting")
                    Column(Modifier.fillMaxWidth().background(SofaColors.Surface, SofaShape)) {
                        sortForDisplay(ui.picks).take(5).forEach { PickRow(it, Modifier.focusableRow(SofaShape)) }
                    }
                    SofaButton("All picks", onClick = onOpenPicks)
                }
            }
        }
        if (ui.aroundLeague.games.isNotEmpty()) {
            item(key = "league") {
                GameRow(if (ui.aroundLeague.live) "Live now" else "Up next", ui.aroundLeague.games, ui, now, onOpenGame)
            }
        }
        if (ui.fightCards.isNotEmpty() || ui.races.isNotEmpty()) {
            item(key = "events") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RowLabel("Fights & racing")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                        items(ui.fightCards, key = { "fc-${it.ref.key}" }) { c ->
                            FightCardTile(c, if (c.state == GameState.PRE) now else 0L, onClick = { onOpenEvent(c.ref) }, modifier = Modifier.width(420.dp), showLeague = true, favorites = ui.favorites)
                        }
                        items(ui.races, key = { "r-${it.ref.key}" }) { r ->
                            RaceCard(r, if (r.state == GameState.PRE) now else 0L, onClick = { onOpenEvent(r.ref) }, modifier = Modifier.width(420.dp), showLeague = true, favorites = ui.favorites)
                        }
                    }
                }
            }
        }
        item(key = "all") { SofaButton("All scores", onClick = onOpenScores) }
    }
}

@Composable
private fun FantasySection(ui: HomeUi, vm: HomeViewModel, onAddEspnLeague: () -> Unit, onOpenFantasy: () -> Unit = {}) {
    val f = ui.fantasy
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    m != null -> {
                        val live = remember(m, ui.games) { FantasyProjection.live(m, ui.games) }
                        MatchupCard(live, compact = true, onClick = onOpenFantasy)
                    }
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
private fun GameRow(title: String, games: List<Game>, ui: HomeUi, now: Long, onOpenGame: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RowLabel(title, trailing = games.size.takeIf { it > 1 }?.toString())
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
            items(games, key = { it.ref.key }) { g ->
                GameCard(
                    g, ui.favorites, ui.closeRule, if (g.state == GameState.PRE) now else 0L,
                    onClick = { if (ui.gameCenterEnabled && g.league.hasGameCenter) onOpenGame(g.ref.key) },
                    modifier = Modifier.width(420.dp),
                    showOdds = ui.showOdds,
                    showLeague = ui.multiLeague,
                )
            }
        }
    }
}
