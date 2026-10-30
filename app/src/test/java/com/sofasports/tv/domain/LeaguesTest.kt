package com.sofasports.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun side(abbr: String, score: Int?, home: Boolean) =
    TeamSide(id = abbr, abbr = abbr, name = abbr, shortName = abbr, logoUrl = null, score = score, record = null, isHome = home)

private fun game(league: League, away: Int, home: Int, period: Int, state: GameState = GameState.LIVE, id: String = "g") =
    Game(id, 0L, state, side("HOM", home, true), side("AWY", away, false), period, "", "", null, league = league)

class LeaguesTest {
    @Test fun `league ids are stable and unique`() {
        assertEquals(League.entries.size, League.entries.map { it.id }.toSet().size)
        assertEquals(League.NBA, League.fromId("nba"))
        assertEquals(League.NBA, League.fromId("NBA"))
        assertNull(League.fromId("cricket"))
        assertTrue("every league but boxing is on ESPN", League.entries.filter { it.espnPath == null } == listOf(League.BOXING))
    }

    @Test fun `old bare favorites read as NFL`() {
        assertEquals("nfl:KC", TeamKey.normalize("KC"))
        assertEquals("nba:LAL", TeamKey.normalize("NBA:lal"))
        assertEquals(League.NFL, TeamKey.league("KC"))
        assertEquals(League.MLB, TeamKey.league("mlb:NYY"))
        assertEquals("NYY", TeamKey.abbr("mlb:NYY"))
    }

    @Test fun `game refs carry the league and bare ids are NFL`() {
        assertEquals(GameRef(League.NFL, "401872996"), GameRef.parse("401872996"))
        assertEquals(GameRef(League.NBA, "401909861"), GameRef.parse("nba:401909861"))
        assertEquals("nba:401909861", GameRef(League.NBA, "401909861").key)
        assertNull(GameRef.parse("cricket:1"))
        assertNull(GameRef.parse(""))
        assertNull(GameRef.parse("nba:"))
    }

    @Test fun `a favorite key only matches its own league`() {
        // ESPN reuses abbreviations across sports: "DAL" is the Cowboys and the Mavericks.
        val nba = game(League.NBA, 0, 0, 1).copy(home = side("DAL", 0, true))
        assertTrue(nba.involves("nba:DAL"))
        assertFalse(nba.involves("nfl:DAL"))
        assertEquals(nba.home, Perspective.mySide(nba, listOf("nfl:DAL", "nba:DAL")))
        assertTrue("bare abbreviations still match within the game's league", nba.involves("DAL"))
    }

    @Test fun `favorites cap per league and overall, keeping pick order`() {
        var f = emptyList<String>()
        listOf("KC", "BUF", "DET", "PHI", "SF").forEach { f = Favorites.toggle(f, "nfl:$it") }
        assertEquals(listOf("nfl:KC", "nfl:BUF", "nfl:DET", "nfl:PHI"), f)
        f = Favorites.toggle(f, "nba:LAL")
        assertEquals("nba:LAL", f.last())
        f = Favorites.toggle(f, "nfl:BUF")
        assertFalse("nfl:BUF" in f)
        // A fighter saved with different capitalization than ESPN's current name still toggles off.
        assertEquals(emptyList<String>(), Favorites.toggle(listOf("ufc:alexander volkanovski"), "ufc:Alexander Volkanovski"))
        var many = emptyList<String>()
        listOf("nfl", "nba", "mlb", "nhl").forEach { l -> (1..4).forEach { many = Favorites.toggle(many, "$l:T$it") } }
        assertEquals(Favorites.MAX_TOTAL, many.size)
        assertTrue("the fourth league's picks didn't fit", Favorites.inLeague(many, League.NHL).isEmpty())
        assertEquals(listOf("nhl:T1"), Favorites.inLeague(Favorites.toggle(many - "mlb:T4", "nhl:T1"), League.NHL))
    }

    @Test fun `period labels per sport`() {
        assertEquals("Q4", Periods.label(League.NFL, 4))
        assertEquals("OT", Periods.label(League.NFL, 5))
        assertEquals("2OT", Periods.label(League.NBA, 6))
        assertEquals("H2", Periods.label(League.CBB, 2))
        assertEquals("OT", Periods.label(League.CBB, 3))
        assertEquals("P3", Periods.label(League.NHL, 3))
        assertEquals("9th", Periods.label(League.MLB, 9))
        assertEquals("11th", Periods.label(League.MLB, 11))
        assertEquals("2H", Periods.label(League.EPL, 2))
        assertEquals("ET", Periods.label(League.EPL, 3))
        assertEquals("R2", Periods.label(League.UFC, 2))
        assertEquals("", Periods.label(League.NBA, 0))
        assertEquals("21st", Periods.ordinal(21))
        assertEquals("12th", Periods.ordinal(12))
    }

    @Test fun `close games use each sport's own rule`() {
        assertTrue(CloseGame.isClose(game(League.NBA, 100, 105, 4)))
        assertFalse(CloseGame.isClose(game(League.NBA, 100, 105, 3)))
        assertFalse(CloseGame.isClose(game(League.NBA, 100, 110, 4)))
        assertTrue("college halves", CloseGame.isClose(game(League.CBB, 60, 62, 2)))
        assertTrue(CloseGame.isClose(game(League.NHL, 2, 3, 3)))
        assertFalse(CloseGame.isClose(game(League.NHL, 1, 3, 3)))
        assertTrue("overtime", CloseGame.isClose(game(League.NHL, 3, 3, 4)))
        assertTrue(CloseGame.isClose(game(League.MLB, 3, 4, 8)))
        assertFalse(CloseGame.isClose(game(League.MLB, 3, 4, 6)))
        assertTrue("extra innings", CloseGame.isClose(game(League.MLB, 5, 5, 10)))
        assertTrue(CloseGame.isClose(game(League.EPL, 1, 1, 2)))
        assertFalse(CloseGame.isClose(game(League.EPL, 0, 2, 2)))
        assertFalse("fights are never close", CloseGame.isClose(game(League.UFC, 0, 0, 3)))
    }

    @Test fun `score words per sport`() {
        assertEquals("TD", SportRules.scoreLabel(League.CFB, 7))
        assertEquals("GOAL", SportRules.scoreLabel(League.EPL, 1))
        assertEquals("2 GOALS", SportRules.scoreLabel(League.NHL, 2))
        assertEquals("RUN", SportRules.scoreLabel(League.MLB, 1))
        assertEquals("3 RUNS", SportRules.scoreLabel(League.MLB, 3))
        assertEquals("3PT", SportRules.scoreLabel(League.NBA, 3))
        assertFalse(SportRules.scoresAreNews(League.NBA))
        assertTrue(SportRules.scoresAreNews(League.MLS))
    }

    @Test fun `ticker reports goals and runs, never baskets`() {
        val before = game(League.EPL, 0, 0, 2)
        val goal = before.copy(home = before.home.copy(score = 1), situation = Situation(null, null, false, "Goal! Home 1, Away 0. Header from close range."))
        assertEquals("EPL · GOAL HOM · Goal! Home 1, Away 0 · AWY 0–1 HOM", TickerBuilder.scoreEvents(listOf(before), listOf(goal), 0L).single().text)
        val nba = game(League.NBA, 50, 50, 2)
        assertTrue(TickerBuilder.scoreEvents(listOf(nba), listOf(nba.copy(home = nba.home.copy(score = 53))), 0L).isEmpty())
        val mlb = game(League.MLB, 0, 0, 3)
        val run = mlb.copy(away = mlb.away.copy(score = 2), situation = Situation(null, null, false, "Judge homered to right (402 feet)."))
        assertEquals("MLB · 2 RUNS AWY · Judge homered to right (402 feet). · AWY 2–0 HOM", TickerBuilder.scoreEvents(listOf(mlb), listOf(run), 0L).single().text)
    }

    @Test fun `same game id in two leagues is two games for the ticker`() {
        val nfl = game(League.NFL, 0, 0, 1, id = "1")
        val nhl = game(League.NHL, 0, 0, 1, id = "1")
        val events = TickerBuilder.scoreEvents(listOf(nfl, nhl), listOf(nfl, nhl.copy(home = nhl.home.copy(score = 1))), 0L)
        assertEquals("ev-nhl:1-0-1", events.single().key)
    }

    @Test fun `fight cards group bouts by event and count prelims as started`() {
        fun bout(event: String, order: Int, state: GameState) =
            game(League.UFC, 0, 0, 0, state, id = "$event-$order").copy(eventId = event, eventName = "Card $event", order = order)
        val cards = FightCards.group(
            listOf(bout("a", 0, GameState.FINAL), bout("a", 1, GameState.PRE), bout("b", 0, GameState.PRE), bout("b", 1, GameState.PRE)),
        )
        assertEquals(listOf("a", "b"), cards.map { it.eventId })
        assertEquals(GameState.LIVE, cards[0].state)
        assertEquals("a-1", cards[0].mainEvent.id)
        assertEquals(GameState.PRE, cards[1].state)
    }

    @Test fun `countdown words per sport`() {
        assertEquals("Tip-off", Countdown.startWord(League.NBA))
        assertEquals("First pitch", Countdown.startWord(League.MLB))
        assertEquals("Starts soon", Countdown.format(10, 5, "Starts"))
    }

    @Test fun `stale pre-game starts stop fast polling`() {
        val now = 100 * 3_600_000L
        val stale = listOf(game(League.BOXING, 0, 0, 0, GameState.PRE).copy(startEpochMs = now - 5 * 3_600_000L))
        assertEquals(300_000L, PollPolicy.nextDelayMs(stale, now, 30, 300))
        val recent = listOf(game(League.BOXING, 0, 0, 0, GameState.PRE).copy(startEpochMs = now - 60_000L))
        assertEquals(30_000L, PollPolicy.nextDelayMs(recent, now, 30, 300))
    }

    @Test fun `races count for polling`() {
        val now = 1_000_000L
        val live = Scoreboard(null, null, emptyList(), League.F1, races = listOf(Race("r", League.F1, "GP", "Race", now, GameState.LIVE, "", null, null)))
        assertEquals(30_000L, PollPolicy.nextDelayMs(live, now, 30, 300))
    }
}
