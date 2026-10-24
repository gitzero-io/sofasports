package com.sofasports.tv.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.NflTeams
import com.sofasports.tv.ui.components.SofaCard
import com.sofasports.tv.ui.components.TeamLogo
import com.sofasports.tv.ui.theme.SofaColors

/** 32-team grid grouped by division. Selected teams turn blue ("mine") and show their pick order. */
@Composable
fun FavoritesPicker(selected: List<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        NflTeams.divisions.chunked(2).forEach { pair ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    pair.forEach { (division, _) ->
                        Text(division, style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary, modifier = Modifier.weight(1f))
                    }
                }
            }
            items(pair.flatMap { it.second }, key = { it.abbr }) { team ->
                val order = selected.indexOf(team.abbr)
                val isSelected = order >= 0
                SofaCard(
                    onClick = { onToggle(team.abbr) },
                    containerColor = if (isSelected) SofaColors.BlueDim else SofaColors.Surface,
                    contentPadding = PaddingValues(10.dp),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            TeamLogo(team.logoUrl, team.name, size = 56.dp)
                            Text(team.abbr, style = MaterialTheme.typography.headlineSmall, color = if (isSelected) SofaColors.BlueBright else SofaColors.TextPrimary)
                            Text(team.name, style = MaterialTheme.typography.labelSmall, color = SofaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isSelected) {
                            Box(
                                Modifier.align(Alignment.TopEnd).background(SofaColors.Blue, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                            ) { Text("${order + 1}", style = MaterialTheme.typography.labelMedium, color = SofaColors.TextPrimary) }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.height(24.dp)) }
    }
}
