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
import com.sofasports.tv.domain.BetTicket
import com.sofasports.tv.domain.TrackedPick
import com.sofasports.tv.ui.components.Badge
import com.sofasports.tv.ui.theme.SofaShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.style.TextOverflow
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

/** BOOK → TYPE → TEAM → (PLAYER → STAT) → LINE; a parlay ends on SLIP to review its legs and save. */
enum class AddStep { BOOK, TYPE, TEAM, PLAYER, STAT, LINE, SLIP }

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
    /** Legs already on the slip; more than one makes it a parlay (a PrizePicks entry). */
    val slip: List<TrackedPick> = emptyList(),
) {
    /** Where a new leg starts once the book is known. */
    val legStart get() = if (book?.propsOnly == true) AddStep.TEAM else AddStep.TYPE

    fun draft(): TrackedPick? {
        val book = book ?: return null
        val type = type ?: return null
        val team = team ?: return null
        return TrackedPick(
            id = UUID.randomUUID().toString(), book = book, type = type, teamAbbr = team.abbr,
            line = line, over = over, playerId = player?.id, playerName = player?.name, stat = stat,
            createdAtMs = System.currentTimeMillis(),
        )
    }
}


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
        s.nfl?.games.orEmpty().filter { it.state == GameState.PRE || it.state == GameState.LIVE }.sortedBy { it.startEpochMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun chooseBook(b: PickBook) = _ui.update {
        // PrizePicks is player props only.
        if (b.propsOnly) it.copy(book = b, type = PickType.PROP, step = AddStep.TEAM) else it.copy(book = b, step = AddStep.TYPE)
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
            // Half the game total is a fair starting point for one team.
            PickType.TEAM_TOTAL -> _ui.update {
                it.copy(game = game, team = team, step = AddStep.LINE, line = game.odds?.overUnder?.let { t -> Math.round(t) / 2 + 0.5 } ?: 21.5)
            }
        }
    }

    fun choosePlayer(p: RosterPlayer) = _ui.update { it.copy(player = p, step = AddStep.STAT) }

    fun chooseStat(s: PropStat) = _ui.update { it.copy(stat = s, line = s.defaultLine, over = true, step = AddStep.LINE) }

    fun nudge(delta: Double) = _ui.update {
        val next = Math.round((it.line + delta) * 2) / 2.0
        it.copy(line = if (it.type?.overUnder == true) next.coerceAtLeast(0.5) else next)
    }

    fun setOver(over: Boolean) = _ui.update { it.copy(over = over) }

    fun back(onExit: () -> Unit) = _ui.update {
        when (it.step) {
            AddStep.BOOK -> { onExit(); it }
            // With legs on the slip the book is fixed, so going back from the first step goes to the slip.
            AddStep.TYPE -> if (it.slip.isNotEmpty()) it.copy(step = AddStep.SLIP) else it.copy(step = AddStep.BOOK)
            AddStep.TEAM -> when {
                it.book?.propsOnly != true -> it.copy(step = AddStep.TYPE)
                it.slip.isNotEmpty() -> it.copy(step = AddStep.SLIP)
                else -> it.copy(step = AddStep.BOOK)
            }
            AddStep.PLAYER -> it.copy(step = AddStep.TEAM)
            AddStep.STAT -> it.copy(step = AddStep.PLAYER)
            AddStep.LINE -> it.copy(step = if (it.type == PickType.PROP) AddStep.STAT else AddStep.TEAM)
            AddStep.SLIP -> it.copy(step = it.legStart)
        }
    }

    /** Puts the leg being built on the slip; [more] starts another leg, otherwise goes to the slip. */
    fun addLeg(more: Boolean) = _ui.update {
        val leg = it.draft() ?: return@update it
        it.copy(
            slip = it.slip + leg,
            step = if (more) it.legStart else AddStep.SLIP,
            type = if (it.book?.propsOnly == true) PickType.PROP else null,
            game = null, team = null, player = null, stat = null, over = true,
        )
    }

    fun finishSlip() = _ui.update { if (it.slip.isEmpty()) it else it.copy(step = AddStep.SLIP) }
    fun removeLeg(id: String) = _ui.update {
        val slip = it.slip.filterNot { l -> l.id == id }
        it.copy(slip = slip, step = if (slip.isEmpty()) it.legStart else it.step)
    }
    /** One leg is a plain pick; two or more are a parlay. */
    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val u = _ui.value
        val book = u.book ?: return@launch
        val legs = u.slip.ifEmpty { listOfNotNull(u.draft()) }
        if (legs.isEmpty()) return@launch
        if (legs.size == 1) picks.add(legs.single())
        else picks.addTicket(BetTicket(UUID.randomUUID().toString(), book, System.currentTimeMillis()), legs)
        legs.forEach { usage.track(UsageEvent.PickAdded(book.name, it.type.name)) }
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
        AddStep.TYPE -> if (ui.slip.isEmpty()) "What kind of ${ui.book?.label ?: ""} bet?" else "Next leg: what kind of bet?"
        AddStep.TEAM -> when (ui.type) {
            PickType.PROP -> "Which team is the player on?"
            PickType.TOTAL -> "Which game?"
            PickType.TEAM_TOTAL -> "Which team's points?"
            else -> "Which team did you take?"
        }
        AddStep.PLAYER -> "Which ${ui.team?.abbr ?: ""} player?"
        AddStep.STAT -> "${ui.player?.name ?: "Player"}: which stat?"
        AddStep.LINE -> if (ui.type == PickType.MONEYLINE || ui.stat?.yesNo == true) "Check it and save" else "Set the line"
        AddStep.SLIP -> if (ui.slip.size > 1) "Your ${ui.slip.size}-leg ${if (ui.book?.propsOnly == true) "entry" else "parlay"}" else "Your bet"
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SpacedRow {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary, modifier = Modifier.weight(1f))
            SofaButton("Back", onClick = { vm.back(onDone) })
            SofaButton("Cancel", onClick = onDone)
        }
        if (ui.slip.isNotEmpty() && ui.step != AddStep.SLIP) SlipStrip(ui.slip, onFinish = vm::finishSlip)
        LazyVerticalGrid(
            columns = GridCells.Fixed(
                when (ui.step) { AddStep.BOOK -> 5; AddStep.TYPE -> 5; AddStep.TEAM -> 4; AddStep.PLAYER -> 4; AddStep.STAT -> 4; AddStep.LINE -> 6; AddStep.SLIP -> 1 },
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            when (ui.step) {
                AddStep.BOOK -> itemsIndexed(PickBook.entries) { i, b ->
                    SofaCard(onClick = { vm.chooseBook(b) }, modifier = Modifier.firstIf(i)) {
                        Text(b.label, style = MaterialTheme.typography.headlineSmall, color = bookColor(b))
                        Text(if (b.propsOnly) "Player props" else "Props, spreads, totals, moneylines", style = MaterialTheme.typography.bodySmall, color = SofaColors.TextSecondary)
                    }
                }
                AddStep.TYPE -> itemsIndexed(PickType.entries) { i, t ->
                    SofaCard(onClick = { vm.chooseType(t) }, modifier = Modifier.firstIf(i)) {
                        Text(t.label, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextPrimary)
                        Text(t.hint, style = MaterialTheme.typography.bodySmall, color = SofaColors.TextSecondary)
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
                AddStep.SLIP -> slipStep(ui, vm, Modifier.focusRequester(first), onDone)
            }
        }
    }
}

private fun LazyGridScope.lineStep(ui: AddPickUi, vm: AddPickViewModel, firstMod: Modifier, onDone: () -> Unit) {
    val draft = ui.draft() ?: return
    val fixed = ui.type == PickType.MONEYLINE || ui.stat?.yesNo == true
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(draft.describe(ui.game), style = MaterialTheme.typography.displaySmall, color = SofaColors.TextPrimary)
    }
    if (!fixed) {
        val steps = listOf(-10.0, -1.0, -0.5, 0.5, 1.0, 10.0)
        steps.forEachIndexed { i, d ->
            item {
                SofaButton((if (d > 0) "+" else "") + Betting.formatNumber(d), onClick = { vm.nudge(d) }, modifier = if (i == 3) firstMod else Modifier)
            }
        }
    }
    if (ui.type?.overUnder == true && !fixed) {
        val (yes, no) = if (ui.book?.propsOnly == true) "More" to "Less" else "Over" to "Under"
        item { SofaButton(yes, onClick = { vm.setOver(true) }, selected = ui.over) }
        item { SofaButton(no, onClick = { vm.setOver(false) }, selected = !ui.over) }
    }
    item(span = { GridItemSpan(maxLineSpan) }) {
        SpacedRow(Modifier.padding(top = 12.dp)) {
            SofaButton(
                if (ui.slip.isEmpty()) "Save bet" else "Add as last leg",
                onClick = { if (ui.slip.isEmpty()) vm.save(onDone) else vm.addLeg(more = false) },
                selected = true, modifier = if (fixed) firstMod else Modifier,
            )
            SofaButton(if (ui.slip.isEmpty()) "Make it a parlay: add another leg" else "Add leg, then another", onClick = { vm.addLeg(more = true) })
        }
    }
}

/** The slip's legs, then save. */
private fun LazyGridScope.slipStep(ui: AddPickUi, vm: AddPickViewModel, firstMod: Modifier, onDone: () -> Unit) {
    items(ui.slip, key = { it.id }) { leg ->
        SpacedRow(Modifier.fillMaxWidth().background(SofaColors.Surface, SofaShape).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Badge(leg.book.tag, bookColor(leg.book))
            Text(leg.describe(), style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary, modifier = Modifier.weight(1f))
            SofaButton("Remove", onClick = { vm.removeLeg(leg.id) })
        }
    }
    item {
        SpacedRow(Modifier.padding(top = 8.dp)) {
            SofaButton("Add another leg", onClick = { vm.back {} })
        }
    }
    item {
        SpacedRow(Modifier.padding(top = 8.dp)) {
            SofaButton(if (ui.slip.size > 1) "Save ${ui.slip.size}-leg parlay" else "Save bet", onClick = { vm.save(onDone) }, selected = true, modifier = firstMod)
        }
    }
}

/** While building a parlay: what's on the slip so far. */
@Composable
private fun SlipStrip(slip: List<TrackedPick>, onFinish: () -> Unit) {
    SpacedRow(Modifier.fillMaxWidth().background(SofaColors.SurfaceRaised, SofaShape).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Slip · ${slip.size} leg${if (slip.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
        Text(
            slip.joinToString("  ·  ") { it.describe() }, style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        SofaButton("Review parlay", onClick = onFinish)
    }
}
