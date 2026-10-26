package com.sofasports.tv.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.data.provider.TeamsProvider
import com.sofasports.tv.domain.Favorites
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.TeamInfo
import com.sofasports.tv.ui.components.MessageBox
import com.sofasports.tv.ui.components.SofaButton
import com.sofasports.tv.ui.components.SofaCard
import com.sofasports.tv.ui.components.TeamLogo
import com.sofasports.tv.ui.theme.SofaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TeamsLoad {
    data object Loading : TeamsLoad
    data class Loaded(val teams: List<TeamInfo>) : TeamsLoad
    data object Failed : TeamsLoad
}

/** Team lists per league for the picker: NFL is built in, the rest come from ESPN once per session. */
@HiltViewModel
class TeamsViewModel @Inject constructor(private val provider: TeamsProvider) : ViewModel() {
    private val _teams = MutableStateFlow<Map<League, TeamsLoad>>(emptyMap())
    val teams: StateFlow<Map<League, TeamsLoad>> = _teams.asStateFlow()

    fun load(league: League) {
        val cur = _teams.value[league]
        if (cur is TeamsLoad.Loaded || cur == TeamsLoad.Loading) return
        _teams.update { it + (league to TeamsLoad.Loading) }
        viewModelScope.launch {
            val result = runCatching { provider.teams(league) }.getOrNull()
            _teams.update { it + (league to if (result == null) TeamsLoad.Failed else TeamsLoad.Loaded(result)) }
        }
    }

    fun retry(league: League) {
        _teams.update { it - league }
        load(league)
    }
}

/** Leagues with more teams than this get an A–Z filter (college has hundreds). */
private const val LETTER_FILTER_MIN = 60

/**
 * Pick favorite teams league by league. [leagues] are the followed team-sport leagues (one tab each).
 * Selected teams turn blue ("mine") and show their pick order within their league.
 */
@Composable
fun FavoritesPicker(
    leagues: List<League>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: TeamsViewModel = hiltViewModel(),
) {
    val teamLeagues = leagues.filter { it.hasTeams }
    if (teamLeagues.isEmpty()) {
        MessageBox("No teams to pick", "Fights and races don't have teams. Add a team sport to pick favorites.", modifier)
        return
    }
    var tab by rememberSaveable { mutableStateOf(teamLeagues.first().id) }
    val league = teamLeagues.firstOrNull { it.id == tab } ?: teamLeagues.first()
    val loads by vm.teams.collectAsStateWithLifecycle()
    LaunchedEffect(league) { vm.load(league) }
    var letter by rememberSaveable(league.id) { mutableStateOf<Char?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (teamLeagues.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                rowItems(teamLeagues, key = { it.id }) { l ->
                    val n = Favorites.inLeague(selected, l).size
                    SofaButton(if (n > 0) "${l.label} ($n)" else l.label, onClick = { tab = l.id }, selected = l == league)
                }
            }
        }
        Text(
            "${league.fullName}: up to ${Favorites.MAX_PER_LEAGUE} teams. Your teams show first and are always blue.",
            style = MaterialTheme.typography.bodyMedium, color = SofaColors.TextSecondary,
        )
        when (val load = loads[league]) {
            is TeamsLoad.Loaded -> {
                val all = load.teams
                val letters = all.mapNotNull { it.name.firstOrNull()?.uppercaseChar() }.distinct().sorted()
                val useLetters = all.size >= LETTER_FILTER_MIN
                if (useLetters) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        item(key = "mine") { SofaButton("Mine", onClick = { letter = null }, selected = letter == null) }
                        rowItems(letters, key = { it }) { c -> SofaButton("$c", onClick = { letter = c }, selected = letter == c) }
                    }
                }
                val shown = when {
                    !useLetters -> all
                    letter == null -> all.filter { it.key in selected }
                    else -> all.filter { it.name.firstOrNull()?.uppercaseChar() == letter }
                }
                if (useLetters && letter == null && shown.isEmpty()) {
                    Text("Pick a letter to find your team.", style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextMuted)
                }
                TeamGrid(shown, selected, onToggle, grouped = !useLetters, Modifier.weight(1f))
            }
            TeamsLoad.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Couldn't load ${league.label} teams.", style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary)
                SofaButton("Try again", onClick = { vm.retry(league) })
            }
            else -> Text("Loading ${league.label} teams…", style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary)
        }
    }
}

@Composable
private fun TeamGrid(teams: List<TeamInfo>, selected: List<String>, onToggle: (String) -> Unit, grouped: Boolean, modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        val groups = if (grouped && teams.any { it.group != null }) teams.groupBy { it.group.orEmpty() }.toList() else listOf("" to teams)
        groups.forEach { (group, list) ->
            if (group.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "g-$group") {
                    Text(group, style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(list, key = { it.key }) { team ->
                val order = selected.filter { it.substringBefore(':') == team.league.id }.indexOf(team.key)
                val isSelected = order >= 0
                SofaCard(
                    onClick = { onToggle(team.key) },
                    containerColor = if (isSelected) SofaColors.BlueDim else SofaColors.Surface,
                    contentPadding = PaddingValues(10.dp),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            TeamLogo(team.logoUrl, team.name, size = 56.dp)
                            Text(team.abbr, style = MaterialTheme.typography.headlineSmall, color = if (isSelected) SofaColors.BlueBright else SofaColors.TextPrimary, maxLines = 1)
                            Text(team.shortName, style = MaterialTheme.typography.labelSmall, color = SofaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Every league, grouped by sport; selected ones are blue and numbered in follow order. The first card takes focus. */
@Composable
fun LeaguePicker(selected: List<League>, onToggle: (League) -> Unit, modifier: Modifier = Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        League.bySport.forEach { (sport, leagues) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "s-${sport.name}") {
                Text(sport.label, style = MaterialTheme.typography.titleSmall, color = SofaColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
            }
            items(leagues, key = { it.id }) { l ->
                val order = selected.indexOf(l)
                val on = order >= 0
                SofaCard(
                    onClick = { onToggle(l) },
                    modifier = if (l == League.entries.first()) Modifier.focusRequester(first) else Modifier,
                    containerColor = if (on) SofaColors.BlueDim else SofaColors.Surface,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.label, style = MaterialTheme.typography.headlineSmall, color = if (on) SofaColors.BlueBright else SofaColors.TextPrimary, maxLines = 1)
                            Text(
                                l.fullName.takeIf { it != l.label } ?: l.sport.label,
                                style = MaterialTheme.typography.labelSmall, color = SofaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (on) {
                            Box(Modifier.background(SofaColors.Blue, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("${order + 1}", style = MaterialTheme.typography.labelMedium, color = SofaColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.height(24.dp)) }
    }
}

/**
 * Title, a line of help, and the action buttons on their own row underneath, left-aligned so
 * pressing Up from any card reaches them (buttons parked at the far right were unreachable).
 */
@Composable
fun PickerHeader(title: String, subtitle: String?, buttons: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = SofaColors.TextPrimary)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = SofaColors.TextSecondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = buttons)
    }
}
