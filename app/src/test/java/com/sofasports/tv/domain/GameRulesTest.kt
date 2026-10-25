package com.sofasports.tv.domain

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.config.CloseGameRule
import com.sofasports.tv.data.espn.EspnParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun side(abbr: String, score: Int?, home: Boolean) =
    TeamSide(id = abbr, abbr = abbr, name = abbr, shortName = abbr, logoUrl = null, score = score, record = null, isHome = home)

private fun game(
    id: String = "g", away: String = "AWY", home: String = "HOM", awayScore: Int? = 0, homeScore: Int? = 0,
    state: GameState = GameState.LIVE, period: Int = 4, start: Long = 0L,
) = Game(id, start, state, side(home, homeScore, true), side(away, awayScore, false), period, "2:00", "", null)

class CloseGameTest {
    @Test fun `one-score game in the 4th is close`() {
        assertTrue(CloseGame.isClose(game(awayScore = 20, homeScore = 27, period = 4)))
        assertTrue(CloseGame.isClose(game(awayScore = 20, homeScore = 28, period = 4)))
    }

    @Test fun `nine points is not close`() = assertFalse(CloseGame.isClose(game(awayScore = 20, homeScore = 29, period = 4)))

    @Test fun `tight game before the 4th is not close yet`() = assertFalse(CloseGame.isClose(game(awayScore = 7, homeScore = 7, period = 3)))

    @Test fun `overtime is always close`() = assertTrue(CloseGame.isClose(game(awayScore = 30, homeScore = 30, period = 5)))

    @Test fun `only live games can be close`() {
        assertFalse(CloseGame.isClose(game(awayScore = 34, homeScore = 31, state = GameState.FINAL)))
        assertFalse(CloseGame.isClose(game(awayScore = null, homeScore = null, state = GameState.PRE, period = 0)))
    }

    @Test fun `rule comes from remote config`() {
        val g = game(awayScore = 10, homeScore = 13, period = 3)
        assertTrue(CloseGame.isClose(g, CloseGameRule(maxPointDiff = 3, minPeriod = 3)))
        assertFalse(CloseGame.isClose(g, CloseGameRule(maxPointDiff = 2, minPeriod = 3)))
    }
}

class GameSortingTest {
    private val sb = EspnParser.parseScoreboard(Fixtures.read("espn/scoreboard_2026_w7_upcoming.json"))

    @Test fun `favorites come first, in pick order`() {
        val sorted = GameSorting.sorted(sb.games, listOf("SEA", "NE"))
        assertTrue(sorted[0].involves("SEA"))
        assertTrue(sorted[1].involves("NE"))
        assertEquals(sb.games.size, sorted.size)
    }

    @Test fun `sections are favorites, live, upcoming, final and skip empty ones`() {
        val games = listOf(
            game("f1", state = GameState.FINAL, start = 1),
            game("p1", state = GameState.PRE, start = 50, awayScore = null, homeScore = null),
            game("p0", state = GameState.PRE, start = 10, awayScore = null, homeScore = null),
            game("l1", state = GameState.LIVE, period = 2, start = 5),
            game("l2", state = GameState.LIVE, period = 4, start = 6),
            game("fav", away = "KC", state = GameState.PRE, start = 99, awayScore = null, homeScore = null),
        )
        val s = GameSorting.sections(games, listOf("KC"))
        assertEquals(listOf(ScoreSection.FAVORITES, ScoreSection.LIVE, ScoreSection.UPCOMING, ScoreSection.FINAL), s.map { it.first })
        assertEquals(listOf("fav"), s[0].second.map { it.id })
        assertEquals(listOf("l2", "l1"), s[1].second.map { it.id }) // later in the game first
        assertEquals(listOf("p0", "p1"), s[2].second.map { it.id })
        assertTrue(GameSorting.sections(games, emptyList()).none { it.first == ScoreSection.FAVORITES })
    }

    @Test fun `live favorite beats upcoming favorite of the same priority`() {
        val games = listOf(
            game("a", home = "KC", state = GameState.PRE, start = 1, awayScore = null, homeScore = null),
            game("b", away = "KC", state = GameState.LIVE, start = 2),
        )
        assertEquals(listOf("b", "a"), GameSorting.sorted(games, listOf("KC")).map { it.id })
    }

    @Test fun `around the league is live games, close first, without my teams`() {
        val games = listOf(
            game("blowout", awayScore = 0, homeScore = 28, period = 4),
            game("close", awayScore = 20, homeScore = 21, period = 4),
            game("mine", away = "KC", period = 4),
            game("next", state = GameState.PRE, start = 9, awayScore = null, homeScore = null),
        )
        val a = GameSorting.aroundLeague(games, listOf("KC"), CloseGameRule())
        assertTrue(a.live)
        assertEquals(listOf("close", "blowout"), a.games.map { it.id })
    }

    @Test fun `around the league falls back to the next kickoffs`() {
        val games = listOf(
            game("late", state = GameState.PRE, start = 50, awayScore = null, homeScore = null),
            game("early", state = GameState.PRE, start = 10, awayScore = null, homeScore = null),
            game("done", state = GameState.FINAL),
        )
        val a = GameSorting.aroundLeague(games, emptyList(), CloseGameRule())
        assertFalse(a.live)
        assertEquals(listOf("early", "late"), a.games.map { it.id })
    }
}

class PerspectiveTest {
    @Test fun `my favorite is my side even when away`() {
        val g = game(away = "KC", home = "SEA")
        assertEquals("KC", Perspective.mySide(g, listOf("KC")).abbr)
        assertEquals("SEA", Perspective.opponent(g, listOf("KC")).abbr)
    }

    @Test fun `higher-priority favorite wins when two favorites meet`() =
        assertEquals("SEA", Perspective.mySide(game(away = "KC", home = "SEA"), listOf("SEA", "KC")).abbr)

    @Test fun `no favorite means home is blue`() =
        assertEquals("HOM", Perspective.mySide(game(), listOf("DAL")).abbr)
}

class CountdownTest {
    private val now = 1_000_000_000L

    @Test fun `far away is null so the date shows`() = assertNull(Countdown.format(now, now + 25 * 3_600_000L))
    @Test fun `hours and padded minutes`() = assertEquals("in 5h 07m", Countdown.format(now, now + 5 * 3_600_000L + 7 * 60_000L + 30_000))
    @Test fun `under an hour shows mm-ss`() = assertEquals("in 42:05", Countdown.format(now, now + 42 * 60_000L + 5_000))
    @Test fun `under a minute`() = assertEquals("in 0:09", Countdown.format(now, now + 9_000))
    @Test fun `past kickoff but not live`() = assertEquals("Kickoff soon", Countdown.format(now, now - 1))
    @Test fun `exactly 24h still shows nothing`() = assertNull(Countdown.format(now, now + 24 * 3_600_000L))
    @Test fun `ticks every second in the last hour`() {
        assertEquals(1_000L, Countdown.tickMs(now, now + 59 * 60_000L))
        assertEquals(30_000L, Countdown.tickMs(now, now + 2 * 3_600_000L))
    }
}

class WinProbabilityTest {
    private fun wp(home: Double, tie: Double = 0.0) = listOf(WinProbPoint("1", home, tie))

    @Test fun `shows my side when I'm favored`() {
        val d = WinProbability.display(game(away = "KC", home = "SEA"), wp(0.27), listOf("KC"))!!
        assertEquals("KC", d.teamAbbr); assertEquals(73, d.percent); assertTrue(d.isMine)
    }

    @Test fun `shows the opponent when they lead`() {
        val d = WinProbability.display(game(away = "KC", home = "SEA"), wp(0.8), listOf("KC"))!!
        assertEquals("SEA", d.teamAbbr); assertEquals(80, d.percent); assertFalse(d.isMine)
    }

    @Test fun `live never shows 100`() =
        assertEquals(99, WinProbability.display(game(), wp(0.9996), emptyList())!!.percent)

    @Test fun `coin flip shows 50 for my side`() {
        val d = WinProbability.display(game(), wp(0.5), emptyList())!!
        assertEquals(50, d.percent); assertTrue(d.isMine)
    }

    @Test fun `ties eat into the away side`() {
        // home 40%, tie 20% → away 40%: level, so my side (home) shows, at its real 40%
        val d = WinProbability.display(game(), wp(0.4, 0.2), emptyList())!!
        assertEquals("HOM", d.teamAbbr); assertEquals(40, d.percent)
    }

    @Test fun `final shows the winner at 100`() {
        val d = WinProbability.display(game(awayScore = 34, homeScore = 31, state = GameState.FINAL), emptyList(), listOf("HOM"))!!
        assertEquals("AWY", d.teamAbbr); assertEquals(100, d.percent); assertFalse(d.isMine)
    }

    @Test fun `no data is null`() = assertNull(WinProbability.display(game(), emptyList(), emptyList()))

    @Test fun `series flips for an away favorite`() {
        val s = WinProbability.mySeries(game(away = "KC"), listOf(WinProbPoint("1", 0.7, 0.0)), listOf("KC"))
        assertEquals(0.3, s.single(), 1e-9)
    }

    @Test fun `real fixture final game`() {
        val s = EspnParser.parseSummary(Fixtures.read("espn/summary_401872996_hou_jax.json"))
        val d = WinProbability.display(s.game, s.winProbability, listOf("JAX"))!!
        assertEquals("HOU won", d.label); assertFalse(d.isMine)
    }
}
