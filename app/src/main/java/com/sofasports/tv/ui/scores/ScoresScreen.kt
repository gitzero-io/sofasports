package com.sofasports.tv.ui.scores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.ui.components.GameCard
import com.sofasports.tv.ui.components.MessageBox
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.rememberNowMs
import com.sofasports.tv.ui.theme.SofaColors

@Composable
fun ScoresScreen(onOpenGame: (String) -> Unit, vm: ScoresViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs(1_000)
    when {
        ui.loading -> MessageBox("Loading scores…")
        ui.sections.isEmpty() -> MessageBox(
            if (ui.error != null) "Couldn't load scores" else "No games this week",
            ui.error?.let { "We'll keep trying every 30 seconds." },
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SpacedRow {
                    Text(ui.week?.let { "NFL · Week $it" } ?: "NFL", style = MaterialTheme.typography.titleLarge, color = SofaColors.TextSecondary)
                    if (ui.error != null) {
                        Text("Offline: showing last scores", style = MaterialTheme.typography.labelMedium, color = SofaColors.Live)
                    }
                }
            }
            ui.sections.forEach { (section, games) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-${section.name}") {
                    Text(section.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = SofaColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                items(games, key = { "${section.name}-${it.id}" }) { game ->
                    GameCard(
                        game = game,
                        favorites = ui.favorites,
                        closeRule = ui.closeRule,
                        nowMs = if (game.state == GameState.PRE) now else 0L,
                        onClick = { onOpenGame(game.id) },
                    )
                }
            }
        }
    }
}
