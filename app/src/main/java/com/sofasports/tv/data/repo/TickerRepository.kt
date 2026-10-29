package com.sofasports.tv.data.repo

import com.sofasports.tv.data.espn.NewsProvider
import com.sofasports.tv.data.picks.PicksRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.di.AppScope
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.NewsItem
import com.sofasports.tv.domain.PickType
import com.sofasports.tv.domain.TeamKey
import com.sofasports.tv.domain.TickerBuilder
import com.sofasports.tv.domain.TickerInterests
import com.sofasports.tv.domain.TickerItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Items for the news ticker: headlines (ESPN, every 5 min, from each followed team-sport league),
 * scoring plays detected between scoreboard polls, and live scores, with anything about my
 * teams / fantasy players / picks first.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class TickerRepository @Inject constructor(
    private val newsProvider: NewsProvider,
    scores: ScoresRepository,
    settings: SettingsStore,
    fantasy: FantasyRepository,
    picks: PicksRepository,
    @AppScope scope: CoroutineScope,
) {
    private var lastGames: List<Game>? = null
    private val events = MutableStateFlow<List<TickerItem>>(emptyList())

    private val newsLeagues: Flow<List<League>> = scores.state.map { st -> st.followed.filter { it.hasTeams } }.distinctUntilChanged()

    private val news: Flow<List<NewsItem>> = newsLeagues.flatMapLatest { leagues ->
        flow {
            val last = mutableMapOf<League, List<NewsItem>>()
            while (true) {
                coroutineScope {
                    leagues.map { l -> l to async { runCatching { newsProvider.headlines(l) }.getOrNull() } }
                        .forEach { (l, d) -> d.await()?.let { last[l] = it } }
                }
                emit(leagues.flatMap { last[it].orEmpty() })
                delay(NEWS_REFRESH_MS)
            }
        }
    }

    private val games: Flow<List<Game>> = flow {
            scores.state.collect { st ->
                if (st.loading) return@collect
                val now = st.followedGames
                lastGames?.let { prev ->
                    val fresh = TickerBuilder.scoreEvents(prev, now, System.currentTimeMillis())
                    if (fresh.isNotEmpty()) {
                        val keep = events.value.filter { System.currentTimeMillis() - it.timeMs < TickerBuilder.EVENT_TTL_MS }
                        events.value = (fresh + keep).distinctBy { it.key }.take(MAX_EVENTS)
                    }
                }
                lastGames = now
                emit(now)
            }
    }

    private val interests: Flow<TickerInterests> = combine(settings.settings, fantasy.state, picks.progress) { s, f, p ->
        val mine = f.matchup?.me?.starters.orEmpty()
        TickerInterests(
            teams = (s.favoriteTeams + p.map { TeamKey.of(League.NFL, it.pick.teamAbbr) }).toSet(),
            playerIds = p.mapNotNull { it.pick.playerId.takeIf { _ -> it.pick.type == PickType.PROP } }.toSet(),
            playerNames = (mine.map { it.player.name } + p.mapNotNull { it.pick.playerName }).toSet(),
        )
    }

    val items: StateFlow<List<TickerItem>> = combine(news, games, events, interests) { n, g, e, i ->
        TickerBuilder.build(n, g, e, i, System.currentTimeMillis())
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private companion object {
        const val NEWS_REFRESH_MS = 5 * 60_000L
        const val MAX_EVENTS = 20
    }
}
