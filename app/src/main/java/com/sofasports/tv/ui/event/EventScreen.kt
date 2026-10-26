package com.sofasports.tv.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.config.CloseGameRule
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.repo.ScoresRepository
import com.sofasports.tv.domain.EventFormat
import com.sofasports.tv.domain.FightCard
import com.sofasports.tv.domain.FightCards
import com.sofasports.tv.domain.GameRef
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.Race
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import com.sofasports.tv.ui.components.GameCard
import com.sofasports.tv.ui.components.MessageBox
import com.sofasports.tv.ui.components.RaceEntryLine
import com.sofasports.tv.ui.components.RowLabel
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.formatKickoff
import com.sofasports.tv.ui.components.rememberNowMs
import com.sofasports.tv.ui.theme.SofaColors
import com.sofasports.tv.ui.theme.focusableRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventUi(
    val loading: Boolean = true,
    val card: FightCard? = null,
    val race: Race? = null,
    val pinnedKey: String? = null,
    val favorites: List<String> = emptyList(),
)

/** A fight card or a race weekend, straight from the shared scoreboard (ESPN has no detail feed worth more). */
@HiltViewModel
class EventViewModel @Inject constructor(
    savedState: SavedStateHandle,
    scores: ScoresRepository,
    private val settings: SettingsStore,
    private val usage: UsageEvents,
) : ViewModel() {
    val ref: GameRef? = GameRef.parse(savedState["ref"])

    val ui: StateFlow<EventUi> = combine(scores.state, settings.settings) { s, prefs ->
        val r = ref ?: return@combine EventUi(loading = false)
        val board = s.board(r.league)
        EventUi(
            loading = board == null && s.boards[r.league]?.error == null,
            card = if (r.league.format == EventFormat.FIGHT) FightCards.group(board?.games.orEmpty()).firstOrNull { it.eventId == r.id } else null,
            race = if (r.league.format == EventFormat.RACE) s.race(r) else null,
            pinnedKey = prefs.pinnedGameId,
            favorites = prefs.favoriteTeams,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventUi())

    /** Pins one bout, or a race weekend, to the overlay. */
    fun togglePin(ref: GameRef) = viewModelScope.launch {
        val pin = ui.value.pinnedKey != ref.key
        settings.setPinnedGame(if (pin) ref else null)
        if (pin) usage.track(UsageEvent.GamePinned)
    }
}

@Composable
fun EventScreen(vm: EventViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    when {
        ui.card != null -> FightCardContent(ui.card!!, ui.pinnedKey, ui.favorites, vm::togglePin)
        ui.race != null -> RaceContent(ui.race!!, ui.pinnedKey, ui.favorites, vm::togglePin)
        ui.loading -> MessageBox("Loading…")
        else -> MessageBox("This event is no longer on the scoreboard")
    }
}

@Composable
private fun FightCardContent(card: FightCard, pinnedKey: String?, favorites: List<String>, onPin: (GameRef) -> Unit) {
    val now = rememberNowMs(1_000)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.name, style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary)
                Text(
                    listOfNotNull(card.league.fullName, formatKickoff(card.startEpochMs), card.mainEvent.broadcast).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary,
                )
                if (selected != null) {
                    val bout = card.bouts.firstOrNull { it.ref.key == selected }
                    if (bout != null) {
                        val pinned = pinnedKey == bout.ref.key
                        SpacedRow(Modifier.padding(top = 8.dp)) {
                            Text("${bout.away.name} vs ${bout.home.name}", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary)
                            SofaButton(if (pinned) "Pinned to overlay ✓" else "Pin this fight to overlay", onClick = { onPin(bout.ref) }, selected = pinned)
                        }
                    }
                }
            }
        }
        card.bouts.forEachIndexed { i, bout ->
            if (i == 0 || i == 1) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-$i") {
                    RowLabel(if (i == 0) "Main event" else "The rest of the card", Modifier.padding(top = 8.dp))
                }
            }
            item(key = bout.ref.key) {
                GameCard(
                    game = bout,
                    favorites = favorites,
                    closeRule = CloseGameRule(),
                    nowMs = if (bout.state == GameState.PRE) now else 0L,
                    onClick = { selected = bout.ref.key },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun RaceContent(race: Race, pinnedKey: String?, favorites: List<String>, onPin: (GameRef) -> Unit) {
    val mine = race.favorites(favorites).map { it.driverId }.toSet()
    val pinned = pinnedKey == race.ref.key
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    var session by rememberSaveable(race.id) { mutableIntStateOf(race.sessions.indexOfFirst { it.name == race.session }.coerceAtLeast(0)) }
    val s = race.sessions.getOrNull(session)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(race.name, style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary)
                Text(
                    listOfNotNull(race.league.fullName, race.venue, race.broadcast).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary,
                )
                SpacedRow {
                    if (race.sessions.size > 1) {
                        race.sessions.forEachIndexed { i, rs ->
                            SofaButton(rs.name, onClick = { session = i }, selected = i == session, modifier = if (i == session) Modifier.focusRequester(first) else Modifier)
                        }
                    }
                    SofaButton(
                        if (pinned) "Pinned to overlay ✓" else "Pin to overlay", onClick = { onPin(race.ref) }, selected = pinned,
                        modifier = if (race.sessions.size <= 1) Modifier.focusRequester(first) else Modifier,
                    )
                }
                Spacer(Modifier.padding(4.dp))
            }
        }
        if (s == null || s.state == GameState.PRE || s.results.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    s?.let { "${it.name} starts ${formatKickoff(it.startEpochMs)}" } ?: "No results yet",
                    style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().focusableRow().padding(16.dp),
                )
            }
        } else {
            items(s.results, key = { "${s.id}-${it.driverId}" }) { e ->
                RaceEntryLine(e, Modifier.focusableRow().padding(horizontal = 16.dp, vertical = 8.dp), mine = e.driverId in mine)
            }
        }
    }
}
