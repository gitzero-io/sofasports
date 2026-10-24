package com.sofasports.tv.ui.picks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.picks.PicksRepository
import com.sofasports.tv.domain.PickEvaluator
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.SofaShape
import com.sofasports.tv.ui.theme.focusableRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PicksViewModel @Inject constructor(private val repo: PicksRepository) : ViewModel() {
    val progress = repo.progress
    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }
    fun clearSettled() = viewModelScope.launch { repo.clearSettled() }
}

/** Live-first ordering: live and pending above settled. */
fun sortForDisplay(list: List<PickProgress>): List<PickProgress> =
    list.sortedWith(compareBy<PickProgress> { it.settled }.thenBy { it.game?.startEpochMs ?: Long.MAX_VALUE })

@Composable
fun PicksScreen(onAddPick: () -> Unit, vm: PicksViewModel = hiltViewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (progress.isEmpty()) "Track your PrizePicks and DraftKings picks"
                    else "My picks · ${PickEvaluator.hitting(progress)} of ${progress.size} hitting",
                    style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary,
                )
                Text(
                    "Enter picks here and they're tracked live from ESPN stats, on this screen, on Home and in the overlay. " +
                        "Neither app offers a way to import your entries, so they're entered by hand.",
                    style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary, modifier = Modifier.widthIn(max = 1100.dp),
                )
                SpacedRow {
                    SofaButton("Add pick", onClick = onAddPick, selected = true)
                    if (progress.any { it.settled }) SofaButton("Clear settled", onClick = vm::clearSettled)
                }
            }
        }
        items(sortForDisplay(progress), key = { it.pick.id }) { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PickRow(p, Modifier.weight(1f).focusableRow(SofaShape))
                SofaButton("Remove", onClick = { vm.remove(p.pick.id) }, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
