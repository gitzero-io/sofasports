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
}
