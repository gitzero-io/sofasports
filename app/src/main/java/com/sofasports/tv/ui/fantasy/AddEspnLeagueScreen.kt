package com.sofasports.tv.ui.fantasy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.espnff.EspnCookies
import com.sofasports.tv.data.espnff.EspnFantasyProvider
import com.sofasports.tv.data.espnff.EspnFfLeague
import com.sofasports.tv.data.espnff.EspnLeagueLink
import com.sofasports.tv.data.espnff.EspnLeagueStore
import com.sofasports.tv.data.espnff.EspnLookup
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SofaCard
import com.sofasports.tv.ui.components.SpacedRow
import com.sofasports.tv.ui.components.TvTextField
import com.sofasports.tv.ui.theme.SofaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEspnUi(
    val leagueId: String = "",
    val espnS2: String = "",
    val swid: String = "",
    val needsCookies: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val league: EspnFfLeague? = null,
    val suggestedTeamId: Int? = null,
)

@HiltViewModel
class AddEspnLeagueViewModel @Inject constructor(
    private val provider: EspnFantasyProvider,
    private val store: EspnLeagueStore,
    private val usage: UsageEvents,
) : ViewModel() {
    private val _ui = MutableStateFlow(AddEspnUi())
    val ui = _ui.asStateFlow()

    fun setLeagueId(v: String) = _ui.update { it.copy(leagueId = v.filter(Char::isDigit), message = null) }
    fun setS2(v: String) = _ui.update { it.copy(espnS2 = v.trim()) }
    fun setSwid(v: String) = _ui.update { it.copy(swid = v.trim()) }

    private fun cookies(u: AddEspnUi) =
        if (u.espnS2.isNotBlank() && u.swid.isNotBlank()) EspnCookies(u.espnS2, u.swid) else null

    fun find() = viewModelScope.launch {
        val u = _ui.value
        if (u.leagueId.isBlank()) { _ui.update { it.copy(message = "Enter your league ID first.") }; return@launch }
        if (u.needsCookies && cookies(u) == null) { _ui.update { it.copy(message = "Enter both espn_s2 and SWID.") }; return@launch }
        _ui.update { it.copy(busy = true, message = "Looking up league ${u.leagueId}…") }
        val r = provider.lookup(u.leagueId, cookies(u))
        _ui.update {
            when (r) {
                is EspnLookup.Found -> it.copy(busy = false, message = null, league = r.league, suggestedTeamId = r.suggestedTeamId)
                EspnLookup.Private -> it.copy(busy = false, needsCookies = true, message = "This league is private. Add your ESPN cookies below, then press Find again.")
                EspnLookup.CookiesRejected -> it.copy(busy = false, needsCookies = true, message = "ESPN didn't accept those cookies. They expire when you log out of ESPN; copy fresh ones.")
                EspnLookup.NotFound -> it.copy(busy = false, message = "No ESPN league ${u.leagueId} this season. Check the number.")
                is EspnLookup.Failed -> it.copy(busy = false, message = "Couldn't reach ESPN: ${r.message}")
            }
        }
    }

    fun pickTeam(teamId: Int, onDone: () -> Unit) = viewModelScope.launch {
        val u = _ui.value
        val league = u.league ?: return@launch
        val team = league.teams.firstOrNull { it.id == teamId } ?: return@launch
        val c = cookies(u)
        store.add(
            EspnLeagueLink(
                leagueId = u.leagueId, season = league.season, teamId = teamId,
                leagueName = league.name, teamName = team.name, espnS2 = c?.espnS2, swid = c?.swid,
            ),
        )
        usage.track(UsageEvent.EspnLeagueAdded(private = c != null))
        onDone()
    }
}

@Composable
fun AddEspnLeagueScreen(onDone: () -> Unit, vm: AddEspnLeagueViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val league = ui.league
    val firstTeam = remember { FocusRequester() }
    LaunchedEffect(league != null) { if (league != null) runCatching { firstTeam.requestFocus() } }
    if (league != null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(league.name, style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary)
            Text(
                if (ui.suggestedTeamId != null) "Your team is highlighted. Press OK on it to confirm." else "Which team is yours?",
                style = MaterialTheme.typography.titleMedium, color = SofaColors.TextSecondary,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(league.teams.sortedByDescending { it.id == ui.suggestedTeamId }, key = { _, t -> t.id }) { i, t ->
                    SofaCard(
                        onClick = { vm.pickTeam(t.id, onDone) },
                        modifier = if (i == 0) Modifier.focusRequester(firstTeam) else Modifier,
                        containerColor = if (t.id == ui.suggestedTeamId) SofaColors.BlueDim else SofaColors.Surface,
                    ) {
                        Text(t.name, style = MaterialTheme.typography.titleLarge, color = SofaColors.TextPrimary, maxLines = 1)
                        t.abbrev?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextMuted) }
                    }
                }
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { Text("Add an ESPN Fantasy league", style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary) }
        item {
            Text(
                "Your league ID is the number after leagueId= in the league's web address, e.g. fantasy.espn.com/football/league?leagueId=123456",
                style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary, modifier = Modifier.widthIn(max = 1100.dp),
            )
        }
        item {
            SpacedRow {
                TvTextField(ui.leagueId, vm::setLeagueId, "Press OK to type the league ID", keyboardType = KeyboardType.Number, maxLength = 12, onDone = { vm.find() })
                SofaButton(if (ui.busy) "Looking…" else "Find league", onClick = { vm.find() }, selected = ui.leagueId.isNotBlank())
                SofaButton("Cancel", onClick = onDone)
            }
        }
        ui.message?.let { m -> item { Text(m, style = MaterialTheme.typography.bodyLarge, color = SofaColors.Live) } }
        if (ui.needsCookies) {
            item { CookieHelp() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("espn_s2", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
                    TvTextField(ui.espnS2, vm::setS2, "Press OK to paste espn_s2", width = 1000.dp, maxLength = 1000)
                    Text("SWID", style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary)
                    TvTextField(ui.swid, vm::setSwid, "Press OK to paste SWID, e.g. {1A2B3C4D-...}", width = 1000.dp, maxLength = 60, onDone = { vm.find() })
                }
            }
        }
    }
}

@Composable
private fun CookieHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 1100.dp)) {
        Text("Getting the two cookies (one time):", style = MaterialTheme.typography.titleMedium, color = SofaColors.TextPrimary)
        val body = MaterialTheme.typography.bodyMedium
        Text("1. On a computer, log in at fantasy.espn.com.", style = body, color = SofaColors.TextSecondary)
        Text("2. Open developer tools → Application (Chrome) or Storage (Firefox) → Cookies → espn.com.", style = body, color = SofaColors.TextSecondary)
        Text("3. Copy the values of espn_s2 and SWID.", style = body, color = SofaColors.TextSecondary)
        Text(
            "Easiest way to enter them: open the Google TV app on your phone, tap the remote → keyboard, and paste.",
            style = body, color = SofaColors.TextSecondary,
        )
        Text(
            "They stay on this TV only (excluded from backups) and are sent only to ESPN. Logging out of ESPN invalidates them.",
            style = body.copy(fontFamily = FontFamily.Default), color = SofaColors.TextMuted,
        )
    }
}
