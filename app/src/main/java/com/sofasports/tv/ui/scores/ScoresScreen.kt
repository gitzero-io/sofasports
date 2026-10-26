package com.sofasports.tv.ui.scores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.GameRef
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.League
import com.sofasports.tv.ui.components.FightCardTile
import com.sofasports.tv.ui.components.GameCard
import com.sofasports.tv.ui.components.MessageBox
import com.sofasports.tv.ui.components.RaceCard
import com.sofasports.tv.ui.components.RowLabel
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.rememberNowMs
import com.sofasports.tv.ui.theme.SofaColors

/**
 * Scores for the followed leagues, one league at a time (or all together). Team sports use the
 * favorites-first sections; fights show one tile per card and races one per weekend.
 * [onOpenGame] gets a game's [GameRef] key; [onOpenEvent] a fight card's or race's.
 */
@Composable
fun ScoresScreen(onOpenGame: (String) -> Unit, onOpenEvent: (GameRef) -> Unit, vm: ScoresViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs(1_000)
    // The filter sits outside the grid: inside, it scrolled away and Up from the cards skipped it.
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ui.leagues.size > 1) LeagueFilter(ui.leagues, ui.selected, onSelect = vm::select)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                SpacedRow {
                    Text(ui.title, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextSecondary)
                    if (ui.error != null && !ui.isEmpty) {
                        Text("Offline: showing last scores", style = MaterialTheme.typography.labelMedium, color = SofaColors.Live)
                    }
                }
            }
            when {
                ui.loading -> item(span = { GridItemSpan(maxLineSpan) }) { MessageBox("Loading scores…") }
                ui.isEmpty -> item(span = { GridItemSpan(maxLineSpan) }) {
                    MessageBox(
                        if (ui.error != null) "Couldn't load scores" else if (ui.selected?.weekly == true) "No games this week" else "Nothing scheduled right now",
                        ui.error?.let { "We'll keep trying every 30 seconds." },
                    )
                }
            }
            ui.sections.forEach { (section, games) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-${section.name}") {
                    RowLabel(section.title, Modifier.padding(top = 12.dp), trailing = games.size.takeIf { it > 1 }?.toString())
                }
                items(games, key = { "${section.name}-${it.ref.key}" }) { game ->
                    GameCard(
                        game = game,
                        favorites = ui.favorites,
                        closeRule = ui.closeRule,
                        nowMs = if (game.state == GameState.PRE) now else 0L,
                        onClick = { if (ui.gameCenterEnabled && game.league.hasGameCenter) onOpenGame(game.ref.key) },
                        showOdds = ui.showOdds,
                        showLeague = ui.showLeagueTags,
                    )
                }
            }
            if (ui.fightCards.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-fights") { RowLabel("Fight cards", Modifier.padding(top = 12.dp)) }
                items(ui.fightCards, key = { "fc-${it.ref.key}" }) { card ->
                    FightCardTile(card, if (card.state == GameState.PRE) now else 0L, onClick = { onOpenEvent(card.ref) }, showLeague = ui.showLeagueTags, favorites = ui.favorites)
                }
            }
            if (ui.races.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-races") { RowLabel("Racing", Modifier.padding(top = 12.dp)) }
                items(ui.races, key = { "r-${it.ref.key}" }) { race ->
                    RaceCard(race, if (race.state == GameState.PRE) now else 0L, onClick = { onOpenEvent(race.ref) }, showLeague = ui.showLeagueTags, favorites = ui.favorites)
                }
            }
        }
    }
}

/** [All] [NFL] [NBA] [UFC] … */
@Composable
private fun LeagueFilter(leagues: List<League>, selected: League?, onSelect: (League?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        item(key = "all") { SofaButton("All", onClick = { onSelect(null) }, selected = selected == null) }
        rowItems(leagues, key = { it.id }) { l -> SofaButton(l.label, onClick = { onSelect(l) }, selected = selected == l) }
    }
}
