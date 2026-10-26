package com.sofasports.tv.data

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.espn.EspnParser
import com.sofasports.tv.data.sleeper.SleeperParser
import com.sofasports.tv.domain.FantasyProjection
import com.sofasports.tv.domain.FantasyTeams
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.PlayerGamePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sleeper NBA (experimental). Players and week-2 projections are real Sleeper responses saved on
 * 2026-10-26; the league, rosters and matchups around them are made up (no NBA league to copy).
 */
class SleeperNbaTest {
    private val leagueDto = SleeperParser.parseLeagueDto(Fixtures.read("sleeper/nba/league.json"))
    private val league = SleeperParser.toLeague(leagueDto)
    private val players = SleeperParser.parsePlayers(Fixtures.read("sleeper/nba/players_nba_subset.json"), League.NBA)
    private val projJson = Fixtures.read("sleeper/nba/projections_nba_2026_w2.json")

    private fun matchup(today: String) = SleeperParser.buildMatchup(
        league, 2, "u1",
        SleeperParser.parseLeagueUsers(Fixtures.read("sleeper/nba/league_users.json")),
        SleeperParser.parseRosters(Fixtures.read("sleeper/nba/league_rosters.json")),
        SleeperParser.parseMatchups(Fixtures.read("sleeper/nba/league_matchups_w2.json")),
        players,
        rosterPositions = leagueDto.rosterPositions.orEmpty(),
        weekProjections = SleeperParser.parseGameProjections(projJson, leagueDto.scoringSettings.orEmpty(), today),
    )!!

    @Test fun `league sport comes from Sleeper`() {
        assertEquals(League.NBA, league.sport)
        assertEquals("Sleeper NBA · Fixture Hoops League", league.switcherLabel)
        assertEquals(League.NFL, SleeperParser.parseLeague(Fixtures.read("sleeper/league.json")).sport)
    }

    @Test fun `players get basketball headshots and slots`() {
        val m = matchup("2026-10-26")
        assertEquals(League.NBA, m.sport)
        val first = m.me.starters.first()
        assertEquals("PG", first.slot)
        assertTrue(first.player.headshotUrl!!.contains("/nba/players/"))
        assertEquals("UTIL", m.me.starters.last().slot)
    }

    @Test fun `projections are scored per game with the league's weights and split at today`() {
        // One game's stat line: 10 pts, 5 reb, 4 ast, 1 stl, 2 to → 10 + 6 + 6 + 3 − 2 = 23.
        val one = """[{"player_id":"1","date":"2026-10-27","stats":{"pts":10,"reb":5,"ast":4,"stl":1,"to":2,"gp":1}},
            {"player_id":"1","date":"2026-10-29","stats":{"pts":20}},{"player_id":"1","date":"2026-10-25","stats":{"pts":7}}]"""
        val p = SleeperParser.parseGameProjections(one, leagueDto.scoringSettings!!, "2026-10-27").getValue("1")
        assertEquals(50.0, p.total, 0.001)
        assertEquals(23.0, p.today, 0.001)
        assertEquals(20.0, p.later, 0.001)
    }

    @Test fun `a week with games left projects more than points so far`() {
        val m = matchup("2026-10-26")
        val starter = m.me.starters.first()
        assertNotNull(starter.projectedLater)
        val live = FantasyProjection.live(m, emptyList())
        // No scoreboard: nobody plays today, so live = points + the later games.
        assertTrue(live.me.starters.all { it.phase == PlayerGamePhase.NO_GAME })
        val s = live.me.starters.first()
        assertEquals(s.starter.points + s.starter.projectedLater!!, s.liveProjected!!, 0.001)
        assertTrue(live.me.projected!! > m.me.points)
        assertTrue("later games count as yet to play", live.me.yetToPlay > 0)
        assertNotNull(live.winProbability)
    }

    @Test fun `after the last day the projection is just the points`() {
        val m = matchup("2026-11-02")
        val live = FantasyProjection.live(m, emptyList())
        assertEquals(m.me.starters.sumOf { it.points }, live.me.projected!!, 0.001)
        // Nothing left to play: settled for whoever leads (my 162.3 vs their 150).
        assertEquals(1.0, live.winProbability!!, 0.001)
    }

    @Test fun `a player in a live game counts what's left of it, by NBA quarters`() {
        val nba = EspnParser.parseScoreboard(Fixtures.read("espn/nba/scoreboard.json"), League.NBA)
        val g = nba.games.single { it.id == "401909861" }.copy(state = GameState.LIVE, period = 4, clock = "6:00")
        assertEquals(0.125, FantasyProjection.remaining(g), 0.001)
        val m = matchup("2026-10-26")
        val st = m.me.starters.first().copy(player = m.me.starters.first().player.copy(team = "UTA"), projectedToday = 40.0, projectedLater = 10.0, points = 20.0)
        val s = FantasyProjection.starter(st, g)
        assertEquals(PlayerGamePhase.LIVE, s.phase)
        assertEquals(20.0 + 10.0 + 40.0 * 0.125, s.liveProjected!!, 0.001)
    }

    @Test fun `sleeper team abbreviations map to espn's`() {
        assertEquals("UTAH", FantasyTeams.scoreboardAbbr(League.NBA, "UTA"))
        assertEquals("GS", FantasyTeams.scoreboardAbbr(League.NBA, "GSW"))
        assertEquals("LAL", FantasyTeams.scoreboardAbbr(League.NBA, "LAL"))
        assertEquals("WSH", FantasyTeams.scoreboardAbbr(League.NFL, "WAS"))
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/ny.png", FantasyTeams.logoUrl(League.NBA, "NYK"))
        assertNull(FantasyTeams.logoUrl(League.NFL, "XYZ"))
    }

    @Test fun `nfl live projection is unchanged by the basketball path`() {
        val m = matchup("2026-10-26")
        val st = m.me.starters.first().copy(projected = 20.0, projectedToday = null, projectedLater = null, points = 5.0)
        val nfl = EspnParser.parseScoreboard(Fixtures.read("espn/scoreboard_2026_w6_final.json")).games.first()
            .copy(state = GameState.LIVE, period = 3, clock = "15:00")
        assertEquals(5.0 + 20.0 * 0.5, FantasyProjection.starter(st, nfl).liveProjected!!, 0.001)
    }

    @Test fun `unknown scoring keys fall back to sleeper's defaults`() {
        val one = """[{"player_id":"1","date":"2026-10-27","stats":{"pts":10,"reb":5}}]"""
        val p = SleeperParser.parseGameProjections(one, mapOf("points_scored" to 1.0), "2026-10-27").getValue("1")
        assertEquals(16.0, p.total, 0.001)
    }
}
