package com.sofasports.tv.ui.fantasy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sofasports.tv.data.repo.FantasyRepository
import com.sofasports.tv.data.repo.FantasyState
import com.sofasports.tv.data.repo.ScoresRepository
import com.sofasports.tv.domain.FantasyProjection
import com.sofasports.tv.domain.Game
import com.sofasports.tv.ui.components.MessageBox
import com.sofasports.tv.ui.components.SofaButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FantasyViewModel @Inject constructor(private val fantasy: FantasyRepository, scores: ScoresRepository) : ViewModel() {
    val state: StateFlow<FantasyState> = fantasy.state
    val games: StateFlow<List<Game>> = scores.state.map { it.nfl?.games.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun selectLeague(id: String) = viewModelScope.launch { fantasy.selectLeague(id) }
}

/** The full matchup: every lineup slot side by side with projections and win probability. */
@Composable
fun FantasyScreen(onLink: () -> Unit, vm: FantasyViewModel = hiltViewModel()) {
    val f by vm.state.collectAsStateWithLifecycle()
    val games by vm.games.collectAsStateWithLifecycle()
    when {
        !f.linked -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { MessageBox("No fantasy league linked", "Link Sleeper or add an ESPN league in Settings → Fantasy to see your matchup here, on Home and in the overlay.") }
                item { SofaButton("Go to Settings", onClick = onLink, selected = true) }
            }
        }
        f.leagues.isEmpty() -> MessageBox(if (f.loading) "Loading your leagues…" else f.error ?: "No NFL leagues found this season.")
        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
            if (f.leagues.size > 1) {
                item(key = "leagues") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                        items(f.leagues, key = { it.id }) { league ->
                            SofaButton("${league.source.label} · ${league.name}", onClick = { vm.selectLeague(league.id) }, selected = league.id == f.selectedLeagueId)
                        }
                    }
                }
            }
            val m = f.matchup
            when {
                m != null -> matchupItems(FantasyProjection.live(m, games))
                f.loading -> item(key = "msg") { MessageBox("Loading matchup…") }
                else -> item(key = "msg") { MessageBox(f.error ?: "No matchup this week in ${f.selectedLeague?.name ?: "this league"}.") }
            }
        }
    }
}
