package com.sofasports.tv.ui.picks

import androidx.compose.foundation.background
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
import com.sofasports.tv.domain.BetStatus
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.domain.TicketProgress
import com.sofasports.tv.ui.components.Badge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
    val tickets = repo.tickets
    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }
    fun clearSettled() = viewModelScope.launch { repo.clearSettled() }
}

/** Live-first ordering: live and pending above settled. */
fun sortForDisplay(list: List<PickProgress>): List<PickProgress> =
    list.sortedWith(compareBy<PickProgress> { it.settled }.thenBy { it.game?.startEpochMs ?: Long.MAX_VALUE })

@Composable
fun PicksScreen(onAddPick: () -> Unit, vm: PicksViewModel = hiltViewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val tickets by vm.tickets.collectAsStateWithLifecycle()
    val singles = progress.filter { it.pick.ticketId == null }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (progress.isEmpty()) "Track your bets and parlays"
                    else "My bets · ${PickEvaluator.hitting(progress)} of ${progress.size} legs hitting",
                    style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary,
                )
                Text(
                    "Enter a bet or parlay from any book (DraftKings, FanDuel, PrizePicks and more) and every leg is tracked live " +
                        "from ESPN stats, here, on Home and in the overlay. No account needed: books don't let other apps read your bets, so they're entered by hand.",
                    style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary, modifier = Modifier.widthIn(max = 1100.dp),
                )
                SpacedRow {
                    SofaButton("Add bet or parlay", onClick = onAddPick, selected = true)
                    if (singles.any { it.settled } || tickets.any { it.settled }) SofaButton("Clear settled", onClick = vm::clearSettled)
                }
            }
        }
        if (progress.isEmpty()) {
            item(key = "how") { HowPicksWork() }
        }
        items(tickets.sortedBy { it.settled }, key = { "t-${it.ticket.id}" }) { t ->
            TicketCard(t, onRemove = { vm.remove(t.ticket.id) })
        }
        items(sortForDisplay(singles), key = { it.pick.id }) { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PickRow(p, Modifier.weight(1f).focusableRow(SofaShape))
                SofaButton("Remove", onClick = { vm.remove(p.pick.id) }, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** A parlay: whether the whole slip is alive, then each leg live. */
@Composable
private fun TicketCard(t: TicketProgress, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(SofaColors.Surface, SofaShape).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().focusableRow(SofaShape).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(t.ticket.book.tag, bookColor(t.ticket.book))
            Spacer(Modifier.width(12.dp))
            Text("${t.legs.size}-leg parlay", style = MaterialTheme.typography.titleLarge, color = SofaColors.TextPrimary)
            Spacer(Modifier.width(16.dp))
            Text(
                "${t.legsWon} of ${t.legs.size} legs won",
                style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary, modifier = Modifier.weight(1f),
            )
            Text(ticketStatusText(t.status), style = MaterialTheme.typography.titleMedium, color = statusColor(t.status))
            SofaButton("Remove", onClick = onRemove, modifier = Modifier.padding(start = 16.dp))
        }
        sortForDisplay(t.legs).forEach { leg -> PickRow(leg, Modifier.padding(start = 24.dp).focusableRow(SofaShape)) }
    }
}

private fun ticketStatusText(s: BetStatus) = when (s) {
    BetStatus.PENDING -> "pending"
    BetStatus.WINNING -> "alive"
    BetStatus.LOSING -> "in danger"
    BetStatus.PUSHING -> "on the number"
    BetStatus.WON -> "WON"
    BetStatus.LOST -> "lost"
    BetStatus.PUSH -> "push"
}

/** Empty state: three steps instead of a blank page. */
@Composable
private fun HowPicksWork() {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        listOf(
            "1" to "Add a bet" to "Player props, spreads, totals, team totals and moneylines. Add more legs to make it a parlay.",
            "2" to "Watch it live" to "It updates from ESPN stats as the game goes, here, on Home and in the overlay.",
            "3" to "See the result" to "Each leg and the whole parlay are marked won, lost or push. Clear settled bets any time.",
        ).forEach { (head, body) ->
            val (n, title) = head
            Column(
                Modifier.weight(1f).focusableRow().background(SofaColors.Surface, SofaShape).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(n, style = MaterialTheme.typography.displaySmall, color = SofaColors.BlueBright)
                Text(title, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextPrimary)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary)
            }
        }
    }
}
