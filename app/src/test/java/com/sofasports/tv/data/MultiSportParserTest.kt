package com.sofasports.tv.data

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.espn.EspnParser
import com.sofasports.tv.data.espn.EspnSituationDto
import com.sofasports.tv.data.provider.EspnTeamsParser
import com.sofasports.tv.data.sportsdb.SportsDbParser
import com.sofasports.tv.domain.FightCards
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.League
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real ESPN / TheSportsDB responses saved on 2026-10-25/26, one per sport. */
class MultiSportParserTest {

    @Test fun `nba scoreboard is tagged with its league and has winners`() {
        val sb = EspnParser.parseScoreboard(Fixtures.read("espn/nba/scoreboard.json"), League.NBA)
        assertEquals(League.NBA, sb.league)
        assertNull("only football numbers weeks", sb.week)
        val g = sb.games.single { it.id == "401909861" }
        assertEquals(League.NBA, g.league)
        assertEquals("LAL", g.away.abbr)
        assertEquals("UTAH", g.home.abbr)
        assertEquals(132, g.away.score)
        assertEquals(g.away, g.winnerSide)
        assertEquals(true, g.away.winner)
    }

    @Test fun `nba summary has flat plays, all player stats and no scoring list`() {
        val s = EspnParser.parseSummary(Fixtures.read("espn/nba/summary_401909861_lal_utah.json"), League.NBA)
        assertTrue(s.drives.isEmpty())
        assertTrue(s.plays.size > 400)
        assertEquals("End of Game", s.playsNewestFirst.first().text)
        assertTrue("every basket is a scoring play; not worth a list", s.scoringPlays.isEmpty())
        val table = s.playerStats.first()
        assertEquals("Players", table.category)
        assertTrue("PTS" in table.labels)
        assertTrue(s.winProbability.isNotEmpty())
    }

    @Test fun `baseball plays say top or bottom of the inning and box score splits batting and pitching`() {
        val s = EspnParser.parseSummary(Fixtures.read("espn/mlb/summary_401908043.json"), League.MLB)
        val first = s.scoringPlays.first()
        assertEquals("Top 1st", first.periodLabel)
        assertEquals(1, first.awayScore)
        assertEquals(listOf("Batting", "Pitching"), s.playerStats.map { it.category }.distinct())
        assertTrue("grouping rows without values are dropped", s.teamStats.none { it.away.isEmpty() && it.home.isEmpty() })
    }

    @Test fun `baseball situation reads count, outs and runners`() {
        val sit = EspnSituationDto(balls = 2, strikes = 1, outs = 1, onFirst = true, onThird = true)
        assertEquals("2-1, 1 out · 1st & 3rd", EspnParser.baseballSituation(sit))
        assertEquals("2 outs · Bases loaded", EspnParser.baseballSituation(EspnSituationDto(outs = 2, onFirst = true, onSecond = true, onThird = true)))
        assertNull("not baseball", EspnParser.baseballSituation(EspnSituationDto(downDistanceText = "3rd & 4")))
    }

    @Test fun `hockey goals are the scoring plays`() {
        val s = EspnParser.parseSummary(Fixtures.read("espn/nhl/summary_401892578.json"), League.NHL)
        assertTrue(s.scoringPlays.isNotEmpty())
        assertTrue(s.scoringPlays.all { it.isScoring })
        assertEquals(5, s.scoringPlays.size)
    }

    @Test fun `soccer key events keep a running score and the minute`() {
        val s = EspnParser.parseSummary(Fixtures.read("espn/epl/summary_401879254.json"), League.EPL)
        val goals = s.scoringPlays
        assertEquals(2, goals.size)
        val first = goals.first()
        assertEquals("47'", first.periodLabel)
        // Newcastle (away) scored first at Crystal Palace.
        assertEquals(1, first.awayScore)
        assertEquals(0, first.homeScore)
        assertEquals(s.game.away.score, goals.last().awayScore)
        assertEquals(s.game.home.score, goals.last().homeScore)
    }

    @Test fun `ufc card is one game per bout, main event last in running order`() {
        val sb = EspnParser.parseScoreboard(Fixtures.read("espn/ufc/scoreboard.json"), League.UFC)
        assertEquals(14, sb.games.size)
        val card = FightCards.group(sb.games).single()
        assertEquals("UFC 333: Volkanovski vs. Evloev", card.name)
        val main = card.mainEvent
        assertEquals("Alexander Volkanovski", main.away.name)
        assertEquals("Movsar Evloev", main.home.name)
        assertEquals(main.away, main.winnerSide)
        assertNull("fights have no score", main.away.score)
        assertEquals("Featherweight", main.note)
        assertEquals("Final · R2 4:54", main.shortDetail)
        assertEquals(GameState.FINAL, card.state)
    }

    @Test fun `f1 weekend shows the race once every session is done`() {
        val sb = EspnParser.parseScoreboard(Fixtures.read("espn/f1/scoreboard.json"), League.F1)
        val race = sb.races.single()
        assertEquals("MSC Cruises United States Grand Prix", race.name)
        assertEquals("Race", race.session)
        assertEquals(GameState.FINAL, race.state)
        assertEquals(listOf("Practice 1", "Practice 2", "Practice 3", "Qualifying", "Race"), race.sessions.map { it.name })
        assertEquals("Kimi Antonelli", race.winner?.name)
        assertEquals(listOf(1, 2, 3), race.results.take(3).map { it.position })
    }

    @Test fun `nascar has a single race session`() {
        val race = EspnParser.parseScoreboard(Fixtures.read("espn/nascar/scoreboard.json"), League.NASCAR).races.single()
        assertEquals("Race", race.session)
        assertEquals(40, race.results.size)
    }

    @Test fun `current session is live, else next, else last`() {
        assertEquals(1, EspnParser.currentSession(listOf(GameState.FINAL, GameState.LIVE, GameState.PRE)))
        assertEquals(2, EspnParser.currentSession(listOf(GameState.FINAL, GameState.FINAL, GameState.PRE, GameState.PRE)))
        assertEquals(2, EspnParser.currentSession(listOf(GameState.FINAL, GameState.FINAL, GameState.FINAL)))
    }

    @Test fun `college football asks ESPN for every FBS game`() {
        val sb = EspnParser.parseScoreboard(Fixtures.read("espn/cfb/scoreboard.json"), League.CFB)
        assertTrue(sb.games.size > 40)
        assertEquals(9, sb.week)
    }

    @Test fun `every saved scoreboard parses`() {
        League.entries.filter { it != League.NFL && it != League.BOXING }.forEach { l ->
            val sb = EspnParser.parseScoreboard(Fixtures.read("espn/${l.id}/scoreboard.json"), l)
            assertTrue("${l.id} parsed nothing", sb.games.isNotEmpty() || sb.races.isNotEmpty())
            assertTrue(sb.games.all { it.league == l })
        }
    }

    @Test fun `boxing reads fighters, winner and method from TheSportsDB`() {
        val sb = SportsDbParser.parseBoxing(Fixtures.read("espn/boxing/scoreboard.json"))
        assertEquals(League.BOXING, sb.league)
        val next = sb.games.single { it.state == GameState.PRE }
        assertEquals("Canelo Álvarez", next.away.name)
        assertEquals("C. Álvarez", next.away.abbr)
        assertEquals("Christian Mbilli", next.home.name)
        val done = sb.games.single { it.state == GameState.FINAL }
        assertEquals("O'Shaquie Foster", done.winnerSide?.name)
        assertEquals("Unanimous decision (118-108, 118-108, 115-111)", done.note)
        assertEquals(done.eventId, done.id)
    }

    @Test fun `boxing method without scorecards stops at the location`() {
        assertEquals("KO in round 3", SportsDbParser.method("A def. B by KO in round 3, Las Vegas, May 1, 2026"))
        assertNull(SportsDbParser.method("Result pending"))
    }

    @Test fun `teams endpoint becomes a sorted picker list`() {
        val teams = EspnTeamsParser.parse(Fixtures.read("espn/nba/teams.json"), League.NBA)
        assertEquals(30, teams.size)
        assertEquals("Atlanta Hawks", teams.first().name)
        assertEquals("nba:ATL", teams.first().key)
        assertTrue(teams.all { it.logoUrl != null })
    }

    @Test fun `fighters are followed by name, any case`() {
        val main = FightCards.group(EspnParser.parseScoreboard(Fixtures.read("espn/ufc/scoreboard.json"), League.UFC).games).single().mainEvent
        assertEquals("ufc:Alexander Volkanovski", main.keyOf(main.away))
        assertTrue(main.involves("ufc:alexander volkanovski"))
        assertTrue(!main.involves("boxing:Alexander Volkanovski"))
        val card = FightCards.group(EspnParser.parseScoreboard(Fixtures.read("espn/ufc/scoreboard.json"), League.UFC).games).single()
        val undercard = card.bouts.last()
        assertEquals(undercard, card.favoriteBout(listOf(undercard.keyOf(undercard.home))))
    }

    @Test fun `favorite drivers are found in the race and its sessions`() {
        val race = EspnParser.parseScoreboard(Fixtures.read("espn/f1/scoreboard.json"), League.F1).races.single()
        val mine = race.favorites(listOf("f1:Lando Norris", "nba:LAL", "f1:Nobody"))
        assertEquals(listOf("Lando Norris"), mine.map { it.name })
        assertEquals(5, race.positionOf(mine.single()))
        assertTrue(race.involvesAny(listOf("f1:lando norris")))
        val upcoming = race.copy(state = GameState.PRE, results = emptyList())
        assertTrue("the entry list still knows who's racing", upcoming.involvesAny(listOf("f1:Lando Norris")))
    }

    @Test fun `overlay picks a favorite driver's race when it's live or soon`() {
        val race = EspnParser.parseScoreboard(Fixtures.read("espn/f1/scoreboard.json"), League.F1).races.single()
        val now = race.startEpochMs
        val live = race.copy(state = GameState.LIVE)
        assertEquals(live, com.sofasports.tv.overlay.OverlayFeed.pickRace(listOf(live), listOf("f1:Lando Norris"), now))
        assertNull(com.sofasports.tv.overlay.OverlayFeed.pickRace(listOf(live), listOf("f1:Nobody"), now))
        assertNull("finished races don't take the overlay", com.sofasports.tv.overlay.OverlayFeed.pickRace(listOf(race), listOf("f1:Lando Norris"), now))
    }
}
