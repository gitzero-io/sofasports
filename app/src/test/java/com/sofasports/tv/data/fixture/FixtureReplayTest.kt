package com.sofasports.tv.data.fixture

import com.sofasports.tv.Fixtures
import com.sofasports.tv.domain.GameState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureReplayTest {

    private val source = object : FixtureSource {
        override fun read(path: String) = runCatching { Fixtures.read(path) }.getOrNull()
    }
    private val replay = FixtureReplay(source)
    private val t0 = System.currentTimeMillis()

    @Test fun `replayed games are live at start and final at the end`() {
        val now = replay.scoreboard(t0)
        val hou = now.games.single { it.id == "401872996" }
        assertEquals(GameState.LIVE, hou.state)
        assertNotNull(hou.situation?.lastPlay)
        val muchLater = replay.scoreboard(t0 + 3_600_000)
        val houFinal = muchLater.games.single { it.id == "401872996" }
        assertEquals(GameState.FINAL, houFinal.state)
        assertEquals(34, houFinal.away.score)
    }

    @Test fun `scores never go down as the replay advances`() {
        var last = -1
        for (sec in 0..800 step 20) {
            val g = replay.scoreboard(t0 + sec * 1000L).games.single { it.id == "401873006" }
            val total = (g.home.score ?: 0) + (g.away.score ?: 0)
            assertTrue(total >= last)
            last = total
        }
    }

    @Test fun `upcoming games are shifted into the near future`() {
        val pre = replay.scoreboard(t0).games.filter { it.state == GameState.PRE }
        assertEquals(13, pre.size)
        val first = pre.minOf { it.startEpochMs }
        assertTrue(first > t0 && first - t0 <= FixtureReplay.FIRST_KICKOFF_DELAY_MS + 5_000)
    }

    @Test fun `summary is truncated to the current play`() {
        val s = replay.summary("401872996", t0)!!
        assertEquals(GameState.LIVE, s.game.state)
        val shown = s.drives.sumOf { it.plays.size }
        assertEquals(FixtureReplay.REPLAYS.getValue("401872996") + 1, shown)
        assertTrue(s.winProbability.size <= shown)
    }

    @Test fun `fantasy fixture points grow with progress`() = runTest {
        val p = FixtureFantasyProvider(source, replay)
        val leagues = p.leagues("x")
        assertEquals(2, leagues.size)
        val m1 = p.currentMatchup(leagues[0].id, "x")!!
        val m2 = p.currentMatchup(leagues[1].id, "x")!!
        assertTrue(m1.me.teamName != m2.me.teamName)
        assertTrue(replay.progress(t0) < replay.progress(t0 + 60_000))
    }

    @Test fun `fixture box score stats grow with the replay`() {
        fun stroudYds(t: Long) = replay.summary("401872996", t)!!.playerStats
            .first { it.key == "passing" && it.teamAbbr == "HOU" }.rows.first().stats[1].toInt()
        val early = stroudYds(t0)
        val later = stroudYds(t0 + 200_000)
        assertTrue(early in 1 until 312)
        assertTrue(later >= early)
    }

    @Test fun `the nba replay runs live in the fourth quarter`() {
        val g = replay.scoreboard(com.sofasports.tv.domain.League.NBA, t0)!!.games.single { it.id == "401909861" }
        assertEquals(GameState.LIVE, g.state)
        assertEquals(com.sofasports.tv.domain.League.NBA, g.league)
        assertEquals(null, g.winnerSide)
        val s = replay.summary("401909861", t0)!!
        assertEquals(true, s.plays.isNotEmpty() && s.plays.size < 514)
        val end = replay.scoreboard(com.sofasports.tv.domain.League.NBA, t0 + 3_600_000)!!.games.single { it.id == "401909861" }
        assertEquals(GameState.FINAL, end.state)
    }

    @Test fun `other leagues show their saved scoreboard`() {
        com.sofasports.tv.domain.League.entries.filter { it != com.sofasports.tv.domain.League.NFL }.forEach { l ->
            assertNotNull("${l.id} has no fixture", replay.scoreboard(l, t0))
        }
    }

    @Test fun `nhl, mlb and epl replays are live with their own clock text`() {
        fun live(l: com.sofasports.tv.domain.League, id: String) = replay.scoreboard(l, t0)!!.games.single { it.id == id }
        val nhl = live(com.sofasports.tv.domain.League.NHL, "401892578")
        assertEquals(GameState.LIVE, nhl.state)
        val mlb = live(com.sofasports.tv.domain.League.MLB, "401908043")
        assertEquals(GameState.LIVE, mlb.state)
        assertEquals(true, mlb.shortDetail.startsWith("Top ") || mlb.shortDetail.startsWith("Bot "))
        val epl = live(com.sofasports.tv.domain.League.EPL, "401879254")
        assertEquals(GameState.LIVE, epl.state)
        assertEquals(true, epl.shortDetail.endsWith("'"))
    }

    @Test fun `ufc main event and f1 race are live at first, final later`() {
        val ufc = replay.scoreboard(com.sofasports.tv.domain.League.UFC, t0)!!.games
        assertEquals(1, ufc.count { it.state == GameState.LIVE })
        val f1 = replay.scoreboard(com.sofasports.tv.domain.League.F1, t0)!!.races.single()
        assertEquals(GameState.LIVE, f1.state)
        val later = replay.scoreboard(com.sofasports.tv.domain.League.F1, t0 + FixtureReplay.LIVE_EVENT_MS + 1)!!.races.single()
        assertEquals(GameState.FINAL, later.state)
    }
}
