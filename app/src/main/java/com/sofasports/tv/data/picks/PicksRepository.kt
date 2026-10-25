package com.sofasports.tv.data.picks

import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.provider.GameStatsProvider
import com.sofasports.tv.data.repo.ScoresRepository
import com.sofasports.tv.di.AppScope
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.PickEvaluator
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.domain.PickType
import com.sofasports.tv.domain.BetTicket
import com.sofasports.tv.domain.TicketEvaluator
import com.sofasports.tv.domain.TicketProgress
import com.sofasports.tv.domain.TrackedPick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live status of every tracked pick. Game-level picks need only the scoreboard; props need the
 * game's box score, which is fetched only for games that have props, every 30 s while live and
 * once after the final.
 */
@Singleton
class PicksRepository @Inject constructor(
    private val store: PicksStore,
    private val scores: ScoresRepository,
    private val stats: GameStatsProvider,
    private val config: ConfigRepository,
    @AppScope scope: CoroutineScope,
) {
    private val summaries = MutableStateFlow<Map<String, GameSummary>>(emptyMap())

    private val summaryPoller = flow {
        while (true) {
            val games = scores.state.value.scoreboard?.games.orEmpty()
            val propGames = store.picks.value.filter { it.type == PickType.PROP }
                .mapNotNull { p -> gameFor(p, games) }
                .distinctBy { it.id }
            for (g in propGames) {
                val have = summaries.value[g.id]
                val needed = g.state == GameState.LIVE || (g.state == GameState.FINAL && have?.game?.state != GameState.FINAL)
                if (!needed) continue
                runCatching { stats.summary(g.id) }.onSuccess { s -> summaries.value = summaries.value + (g.id to s) }
            }
            emit(Unit)
            delay(config.config.value.pollIntervalLiveSec * 1000L)
        }
    }

    val progress: StateFlow<List<PickProgress>> =
        combine(store.picks, scores.state, summaries, summaryPoller) { picks, s, sums, _ ->
            val games = s.scoreboard?.games.orEmpty()
            picks.map { p ->
                val g = gameFor(p, games)
                // Prefer the box score's game state: it can be fresher than the scoreboard poll.
                val summary = g?.let { sums[it.id] }
                PickEvaluator.evaluate(p, summary?.game?.takeIf { p.type == PickType.PROP } ?: g, summary)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bet slips with their legs' live progress. */
    val tickets: StateFlow<List<TicketProgress>> =
        combine(store.tickets, progress) { t, p -> TicketEvaluator.group(t, p) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun add(pick: TrackedPick) = store.add(pick)
    suspend fun addTicket(ticket: BetTicket, legs: List<TrackedPick>) = store.addTicket(ticket, legs)
    suspend fun remove(id: String) = store.remove(id)

    /** Settled singles, and whole slips once they're settled (an open parlay keeps its finished legs). */
    suspend fun clearSettled() {
        val singles = progress.value.filter { it.settled && it.pick.ticketId == null }.map { it.pick.id }
        val slips = TicketEvaluator.group(store.tickets.value, progress.value).filter { it.settled }.map { it.ticket.id }
        store.removeAll((singles + slips).toSet())
    }

    companion object {
        /** This week's game for the pick's team. */
        fun gameFor(p: TrackedPick, games: List<Game>): Game? = games.firstOrNull { it.involves(p.teamAbbr) }
    }
}
