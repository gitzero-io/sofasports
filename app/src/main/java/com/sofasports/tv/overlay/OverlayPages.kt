package com.sofasports.tv.overlay

import com.sofasports.tv.data.prefs.OverlayPage
import com.sofasports.tv.domain.CloseGame
import com.sofasports.tv.domain.EventFormat
import com.sofasports.tv.domain.FantasyStarter
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.NflTeams

/** Which overlay pages have something to show, and how the remote moves between them. */
object OverlayPages {
    const val LEAGUE_MAX = 6
    const val PLAYING_NOW_MAX = 4

    fun hasContent(page: OverlayPage, s: OverlayState, nowMs: Long): Boolean = when (page) {
        OverlayPage.MATCHUP -> s.matchup != null
        OverlayPage.MY_GAME -> s.game != null || s.race != null
        OverlayPage.LEAGUE -> leagueGames(s, nowMs).isNotEmpty()
        OverlayPage.PICKS -> s.picks.isNotEmpty()
        OverlayPage.MINI -> s.matchup != null || s.game != null || s.race != null || s.picks.isNotEmpty()
    }

    /** Pages with content, regardless of what the user enabled (a pop may show any of them). */
    fun withContent(s: OverlayState, nowMs: Long): List<OverlayPage> = OverlayPage.entries.filter { hasContent(it, s, nowMs) }

    /** Enabled pages that have content, in display order. */
    fun available(s: OverlayState, enabled: Set<OverlayPage>, nowMs: Long): List<OverlayPage> =
        withContent(s, nowMs).filter { it in enabled }

    /** The page to draw: the chosen one if it's available, else the first full page, else Minimized. */
    fun resolve(current: OverlayPage, available: List<OverlayPage>): OverlayPage? =
        current.takeIf { it in available } ?: available.firstOrNull { it != OverlayPage.MINI } ?: available.firstOrNull()

    /** Next (or previous) page, wrapping around. [fullOnly] skips Minimized (auto-rotate never minimizes). */
    fun step(current: OverlayPage, available: List<OverlayPage>, forward: Boolean = true, fullOnly: Boolean = false): OverlayPage? {
        val pages = if (fullOnly) available.filter { it != OverlayPage.MINI } else available
        if (pages.isEmpty()) return null
        val i = pages.indexOf(current)
        if (i < 0) return if (forward) pages.first() else pages.last()
        return pages[(i + if (forward) 1 else pages.size - 1) % pages.size]
    }

    /**
     * "Around the league": live games (close ones first), then today's kickoffs, then recent finals,
     * across every followed team-sport league. Fights and races don't fit the one-line score rows.
     */
    fun leagueGames(s: OverlayState, nowMs: Long): List<Game> {
        val games = s.games.filter { it.league.format == EventFormat.TEAMS }
        val live = games.filter { it.state == GameState.LIVE }
            .sortedWith(compareByDescending<Game> { CloseGame.isClose(it, s.closeRule) }.thenBy { it.startEpochMs })
        val upcoming = games.filter { it.state == GameState.PRE && it.startEpochMs - nowMs in 0..12 * HOUR }.sortedBy { it.startEpochMs }
        val finals = games.filter { it.state == GameState.FINAL && nowMs - it.startEpochMs < 6 * HOUR }.sortedByDescending { it.startEpochMs }
        return (live + upcoming + finals).take(LEAGUE_MAX)
    }

    /** My fantasy starters whose NFL game is live right now, highest points first. */
    fun playingNow(s: OverlayState): List<FantasyStarter> {
        val m = s.matchup ?: return emptyList()
        val liveTeams = s.games.filter { it.league == League.NFL && it.state == GameState.LIVE }.flatMap { g -> g.teams.map { it.abbr } }.toSet()
        if (liveTeams.isEmpty()) return emptyList()
        return m.me.starters.filter { st -> st.player.team?.let { teamAbbr(it) in liveTeams } == true }
            .sortedByDescending { it.points }
            .take(PLAYING_NOW_MAX)
    }

    fun teamAbbr(fantasyTeam: String): String = NflTeams.scoreboardAbbr(fantasyTeam)

    private const val HOUR = 3_600_000L
}
