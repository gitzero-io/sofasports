package com.sofasports.tv.ui.picks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.picks.PicksRepository
import com.sofasports.tv.data.picks.RosterPlayer
import com.sofasports.tv.data.picks.RosterProvider
import com.sofasports.tv.data.repo.ScoresRepository
import com.sofasports.tv.domain.Betting
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.PickBook
import com.sofasports.tv.domain.PickType
import com.sofasports.tv.domain.PropStat
import com.sofasports.tv.domain.TeamSide
import com.sofasports.tv.domain.TrackedPick
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SofaCard
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.formatKickoff
import com.sofasports.tv.ui.theme.SofaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class AddStep { BOOK, TYPE, TEAM, PLAYER, STAT, LINE }

data class AddPickUi(
    val step: AddStep = AddStep.BOOK,
    val book: PickBook? = null,
    val type: PickType? = null,
    val game: Game? = null,
    val team: TeamSide? = null,
    val roster: List<RosterPlayer> = emptyList(),
    val rosterMessage: String? = null,
    val player: RosterPlayer? = null,
    val stat: PropStat? = null,
    val line: Double = 0.5,
    val over: Boolean = true,
)

@HiltViewModel
class AddPickViewModel @Inject constructor(
    scores: ScoresRepository,
    private val rosters: RosterProvider,
    private val picks: PicksRepository,
    private val usage: UsageEvents,
) : ViewModel() {
    private val _ui = MutableStateFlow(AddPickUi())
    val ui = _ui.asStateFlow()

    /** Games you can still bet on, or are in progress. */
    val games: StateFlow<List<Game>> = scores.state.map { s ->
        s.scoreboard?.games.orEmpty().filter { it.state == GameState.PRE || it.state == GameState.LIVE }.sortedBy { it.startEpochMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun chooseBook(b: PickBook) = _ui.update {
        // PrizePicks is player props only.
        if (b == PickBook.PRIZEPICKS) it.copy(book = b, type = PickType.PROP, step = AddStep.TEAM) else it.copy(book = b, step = AddStep.TYPE)
    }

    fun chooseType(t: PickType) = _ui.update { it.copy(type = t, step = AddStep.TEAM) }

    fun chooseTeam(game: Game, team: TeamSide) {
        val type = _ui.value.type ?: return
        when (type) {
            PickType.PROP -> {
                _ui.update { it.copy(game = game, team = team, step = AddStep.PLAYER, roster = emptyList(), rosterMessage = "Loading ${team.abbr} roster…") }
                viewModelScope.launch {
                    val r = runCatching { rosters.offense(team.id) }
                    _ui.update {
                        it.copy(
                            roster = r.getOrDefault(emptyList()),
                            rosterMessage = when {
                                r.isFailure -> "Couldn't load the roster: ${r.exceptionOrNull()?.message}"
                                r.getOrNull().isNullOrEmpty() -> "No roster available for ${team.abbr}."
                                else -> null
                            },
                        )
                    }
                }
            }
            PickType.SPREAD -> _ui.update {
                it.copy(game = game, team = team, step = AddStep.LINE, line = game.odds?.let { o -> Betting.spreadFor(o, game, team.abbr) } ?: 0.5)
            }
            PickType.TOTAL -> _ui.update { it.copy(game = game, team = team, step = AddStep.LINE, line = game.odds?.overUnder ?: 44.5) }
            PickType.MONEYLINE -> _ui.update { it.copy(game = game, team = team, step = AddStep.LINE, line = 0.0) }
        }
    }

    fun choosePlayer(p: RosterPlayer) = _ui.update { it.copy(player = p, step = AddStep.STAT) }

    fun chooseStat(s: PropStat) = _ui.update { it.copy(stat = s, line = s.defaultLine, step = AddStep.LINE) }

    fun nudge(delta: Double) = _ui.update {
        val next = Math.round((it.line + delta) * 2) / 2.0
        it.copy(line = if (it.type == PickType.PROP || it.type == PickType.TOTAL) next.coerceAtLeast(0.5) else next)
    }

    fun setOver(over: Boolean) = _ui.update { it.copy(over = over) }

    fun back(onExit: () -> Unit) = _ui.update {
        when (it.step) {
            AddStep.BOOK -> { onExit(); it }
            AddStep.TYPE -> it.copy(step = AddStep.BOOK)
            AddStep.TEAM -> it.copy(step = if (it.book == PickBook.PRIZEPICKS) AddStep.BOOK else AddStep.TYPE)
            AddStep.PLAYER -> it.copy(step = AddStep.TEAM)
            AddStep.STAT -> it.copy(step = AddStep.PLAYER)
            AddStep.LINE -> it.copy(step = if (it.type == PickType.PROP) AddStep.STAT else AddStep.TEAM)
        }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val u = _ui.value
        val book = u.book ?: return@launch
        val type = u.type ?: return@launch
        val team = u.team ?: return@launch
        picks.add(
            TrackedPick(
                id = UUID.randomUUID().toString(), book = book, type = type, teamAbbr = team.abbr,
                line = u.line, over = u.over, playerId = u.player?.id, playerName = u.player?.name, stat = u.stat,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
        usage.track(UsageEvent.PickAdded(book.name, type.name))
        onDone()
    }
}

@Composable
fun AddPickScreen(onDone: () -> Unit, vm: AddPickViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val games by vm.games.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(ui.step, ui.roster.size) { runCatching { first.requestFocus() } }
    fun Modifier.firstIf(i: Int) = if (i == 0) focusRequester(first) else this

    val title = when (ui.step) {
        AddStep.BOOK -> "Where did you place it?"
        AddStep.TYPE -> "What kind of DraftKings bet?"
        AddStep.TEAM -> when (ui.type) {
            PickType.PROP -> "Which team is the player on?"
            PickType.TOTAL -> "Which game?"
            else -> "Which team did you take?"
        }
        AddStep.PLAYER -> "Which ${ui.team?.abbr ?: ""} player?"
        AddStep.STAT -> "${ui.player?.name ?: "Player"}: which stat?"
        AddStep.LINE -> "Set the line"
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SpacedRow {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary, modifier = Modifier.weight(1f))
            SofaButton("Back", onClick = { vm.back(onDone) })
            SofaButton("Cancel", onClick = onDone)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(
                when (ui.step) { AddStep.BOOK, AddStep.TYPE -> 4; AddStep.TEAM -> 4; AddStep.PLAYER -> 4; AddStep.STAT -> 3; AddStep.LINE -> 6 },
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            when (ui.step) {
                AddStep.BOOK -> itemsIndexed(PickBook.entries) { i, b ->
                    SofaCard(onClick = { vm.chooseBook(b) }, modifier = Modifier.firstIf(i)) {
                        Text(b.label, style = MaterialTheme.typography.headlineSmall, color = bookColor(b))
                        Text(if (b == PickBook.PRIZEPICKS) "Player props" else "Props, spreads, totals, moneylines", style = MaterialTheme.typography.bodySmall, color = SofaColors.TextSecondary)
                    }
                }
                AddStep.TYPE -> itemsIndexed(PickType.entries) { i, t ->
                    SofaCard(onClick = { vm.chooseType(t) }, modifier = Modifier.firstIf(i)) {
                        Text(t.label, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextPrimary)
                    }
                }
                AddStep.TEAM -> {
                    if (games.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("No upcoming or live games right now.", style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary)
                    }
                    val total = ui.type == PickType.TOTAL
                    val entries = games.flatMap { g -> if (total) listOf(g to g.home) else listOf(g to g.away, g to g.home) }
                    itemsIndexed(entries, key = { _, (g, t) -> "${g.id}-${t.id}" }) { i, (g, t) ->
                        SofaCard(onClick = { vm.chooseTeam(g, t) }, modifier = Modifier.firstIf(i)) {
                            Text(if (total) "${g.away.abbr} @ ${g.home.abbr}" else t.abbr, style = MaterialTheme.typography.headlineSmall, color = SofaColors.TextPrimary)
                            Text(
                                if (total) (g.odds?.overUnder?.let { "O/U ${Betting.formatNumber(it)}" } ?: formatKickoff(g.startEpochMs))
                                else "${if (t.isHome) "vs" else "@"} ${if (t.isHome) g.away.abbr else g.home.abbr} · ${if (g.state == GameState.LIVE) "LIVE" else formatKickoff(g.startEpochMs)}",
                                style = MaterialTheme.typography.bodySmall, color = SofaColors.TextSecondary, maxLines = 1,
                            )
                        }
                    }
                }
                AddStep.PLAYER -> {
                    ui.rosterMessage?.let { m -> item(span = { GridItemSpan(maxLineSpan) }) { Text(m, style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary) } }
                    itemsIndexed(ui.roster, key = { _, p -> p.id }) { i, p ->
                        SofaCard(onClick = { vm.choosePlayer(p) }, modifier = Modifier.firstIf(i)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary, maxLines = 1)
                            Text(listOfNotNull(p.position, p.jersey?.let { "#$it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = SofaColors.TextSecondary)
                        }
                    }
                }
                AddStep.STAT -> itemsIndexed(PropStat.forPosition(ui.player?.position)) { i, s ->
                    SofaCard(onClick = { vm.chooseStat(s) }, modifier = Modifier.firstIf(i)) {
                        Text(s.label, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextPrimary)
                    }
                }
                AddStep.LINE -> lineStep(ui, vm, Modifier.focusRequester(first), onDone)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.lineStep(ui: AddPickUi, vm: AddPickViewModel, firstMod: Modifier, onDone: () -> Unit) {
    val draft = TrackedPick("", ui.book ?: PickBook.DRAFTKINGS, ui.type ?: PickType.PROP, ui.team?.abbr ?: "", ui.line, ui.over, ui.player?.id, ui.player?.name, ui.stat)
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(draft.describe(ui.game), style = MaterialTheme.typography.displaySmall, color = SofaColors.TextPrimary)
    }
    if (ui.type != PickType.MONEYLINE) {
        val steps = listOf(-10.0, -1.0, -0.5, 0.5, 1.0, 10.0)
        steps.forEachIndexed { i, d ->
            item {
                SofaButton((if (d > 0) "+" else "") + Betting.formatNumber(d), onClick = { vm.nudge(d) }, modifier = if (i == 3) firstMod else Modifier)
            }
        }
    }
    if (ui.type == PickType.PROP || ui.type == PickType.TOTAL) {
        val (yes, no) = if (ui.book == PickBook.PRIZEPICKS) "More" to "Less" else "Over" to "Under"
        item { SofaButton(yes, onClick = { vm.setOver(true) }, selected = ui.over, modifier = if (ui.type == PickType.MONEYLINE) firstMod else Modifier) }
        item { SofaButton(no, onClick = { vm.setOver(false) }, selected = !ui.over) }
    }
    item(span = { GridItemSpan(2) }) {
        SofaButton("Save pick", onClick = { vm.save(onDone) }, selected = true, modifier = if (ui.type == PickType.MONEYLINE) firstMod else Modifier)
    }
}
