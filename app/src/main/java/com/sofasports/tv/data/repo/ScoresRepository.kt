package com.sofasports.tv.data.repo

import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.picks.PicksStore
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.provider.ScoresProvider
import com.sofasports.tv.di.AppScope
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameRef
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.PollPolicy
import com.sofasports.tv.domain.Race
import com.sofasports.tv.domain.Scoreboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** One league's latest scoreboard. [scoreboard] keeps the last good data when a fetch fails. */
data class LeagueBoard(
    val scoreboard: Scoreboard? = null,
    val updatedAtMs: Long = 0L,
    val error: String? = null,
)

data class ScoresState(
    /** Leagues being polled, in the user's order. */
    val leagues: List<League> = emptyList(),
    val boards: Map<League, LeagueBoard> = emptyMap(),
) {
    fun board(league: League): Scoreboard? = boards[league]?.scoreboard

    /** The NFL scoreboard: fantasy, picks and the score ticker's play text are NFL-only. */
    val nfl: Scoreboard? get() = board(League.NFL)

    /** Every polled league's games, in league order. */
    val games: List<Game> get() = leagues.flatMap { board(it)?.games.orEmpty() }
    val races: List<Race> get() = leagues.flatMap { board(it)?.races.orEmpty() }

    /** Nothing has come back yet from any league. */
    val loading: Boolean get() = leagues.isNotEmpty() && leagues.none { boards[it]?.scoreboard != null || boards[it]?.error != null }

    /** Set while any league's last fetch failed. */
    val error: String? get() = leagues.firstNotNullOfOrNull { boards[it]?.error }
    val updatedAtMs: Long get() = boards.values.maxOfOrNull { it.updatedAtMs } ?: 0L

    fun game(ref: GameRef): Game? = board(ref.league)?.games?.firstOrNull { it.id == ref.id }
    fun race(ref: GameRef): Race? = board(ref.league)?.races?.firstOrNull { it.id == ref.id }
}

/**
 * Single shared scoreboard poller for the whole app (screens + overlay).
 * Each followed league polls on its own, only while someone is watching, at the cadence
 * [PollPolicy] picks for that league (30 s while it has a live game, otherwise until its next start).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ScoresRepository @Inject constructor(
    private val provider: ScoresProvider,
    private val config: ConfigRepository,
    settings: SettingsStore,
    picks: PicksStore,
    @AppScope scope: CoroutineScope,
) {
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<ScoresState> = combine(settings.settings, config.config, picks.picks.map { it.isNotEmpty() }.distinctUntilChanged()) { s, c, hasPicks ->
        // Fantasy and picks are NFL-only, so a linked Sleeper account or a tracked pick keeps the NFL
        // scoreboard coming even if the user doesn't follow the NFL.
        val needNfl = s.sleeperUserId != null || hasPicks
        val leagues = (s.followedLeagues + listOfNotNull(League.NFL.takeIf { needNfl }))
            .distinct().filter(c::leagueEnabled)
        s.dataMode to leagues
    }
        .distinctUntilChanged()
        .flatMapLatest { (_, leagues) -> pollAll(leagues) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ScoresState())

    fun refreshNow() {
        refreshSignal.tryEmit(Unit)
    }

    private fun pollAll(leagues: List<League>): Flow<ScoresState> {
        if (leagues.isEmpty()) return flowOf(ScoresState())
        return combine(leagues.map { l -> poll(l).map { l to it } }) { boards ->
            ScoresState(leagues, boards.toMap())
        }
    }

    private fun poll(league: League): Flow<LeagueBoard> = flow {
        var last = LeagueBoard()
        emit(last)
        while (true) {
            last = runCatching { provider.scoreboard(league) }.fold(
                onSuccess = { LeagueBoard(it, System.currentTimeMillis(), null) },
                onFailure = { last.copy(error = it.message ?: "Couldn't load ${league.label} scores") },
            )
            emit(last)
            val cfg = config.config.value
            val wait = PollPolicy.nextDelayMs(
                last.scoreboard, System.currentTimeMillis(),
                cfg.pollIntervalLiveSec, cfg.pollIntervalIdleSec, lastFetchFailed = last.error != null,
            )
            withTimeoutOrNull(wait) { refreshSignal.first() }
        }
    }
}
