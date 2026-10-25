package com.sofasports.tv.domain

import com.sofasports.tv.data.espnff.EspnFantasyParser
import com.sofasports.tv.data.sleeper.SleeperParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FantasyProjectionTest {
    private fun side(abbr: String, home: Boolean) = TeamSide(abbr, abbr, abbr, abbr, null, 0, null, home)
    private fun game(home: String, away: String, state: GameState, period: Int = 0, clock: String = "15:00") =
        Game("$away@$home", 0, state, side(home, true), side(away, false), period, clock, "", null)
    private fun st(team: String, points: Double, proj: Double?) = FantasyStarter(FantasyPlayer(team + points, "P", "WR", team), points, proj)
    private fun team(vararg s: FantasyStarter) = FantasyTeam(1, null, "T", s.sumOf { it.points }, s.toList())

    @Test fun `live projection is points plus projection for the time left`() {
        val halftime = game("HOU", "JAX", GameState.LIVE, period = 2, clock = "0:00")
        assertEquals(0.5, FantasyProjection.remaining(halftime), 1e-9)
        assertEquals(8.0 + 10.0 * 0.5, FantasyProjection.starter(st("HOU", 8.0, 10.0), halftime).liveProjected!!, 1e-9)
        assertEquals(10.0, FantasyProjection.starter(st("HOU", 0.0, 10.0), game("HOU", "JAX", GameState.PRE)).liveProjected!!, 1e-9)
        assertEquals(3.0, FantasyProjection.starter(st("HOU", 3.0, 10.0), game("HOU", "JAX", GameState.FINAL)).liveProjected!!, 1e-9)
        assertEquals(0.25, FantasyProjection.remaining(game("A", "B", GameState.LIVE, period = 4, clock = "15:00")), 1e-9)
    }

    @Test fun `team projection and win probability`() {
        val games = listOf(game("HOU", "JAX", GameState.PRE), game("KC", "LAC", GameState.FINAL))
        val m = FantasyMatchup("l", "L", 7, team(st("HOU", 0.0, 20.0), st("KC", 12.0, 10.0)), team(st("JAX", 0.0, 15.0), st("LAC", 9.0, 10.0)))
        val live = FantasyProjection.live(m, games)
        assertEquals(32.0, live.me.projected!!, 1e-9)
        assertEquals(24.0, live.opponent!!.projected!!, 1e-9)
        val p = live.winProbability!!
        assertTrue("favoured but not certain: $p", p in 0.6..0.95)
        assertEquals(1, live.me.count(PlayerGamePhase.PRE))
        assertEquals(1, live.me.count(PlayerGamePhase.DONE))
    }

    @Test fun `all games over decides it`() {
        val games = listOf(game("HOU", "JAX", GameState.FINAL))
        val m = FantasyMatchup("l", "L", 7, team(st("HOU", 20.0, 15.0)), team(st("JAX", 18.0, 25.0)))
        assertEquals(1.0, FantasyProjection.live(m, games).winProbability!!, 0.0)
    }

    @Test fun `no projections means no projection or win probability`() {
        val m = FantasyMatchup("l", "L", 7, team(st("HOU", 5.0, null)), team(st("JAX", 3.0, null)))
        val live = FantasyProjection.live(m, emptyList())
        assertNull(live.me.projected)
        assertNull(live.winProbability)
    }

    @Test fun `fantasy abbreviations find their scoreboard game`() {
        val m = FantasyMatchup("l", "L", 7, team(st("WAS", 1.0, 5.0)), null)
        assertEquals(PlayerGamePhase.LIVE, FantasyProjection.live(m, listOf(game("WSH", "DAL", GameState.LIVE, 1))).me.starters.single().phase)
    }

    @Test fun `normal cdf`() {
        assertEquals(0.5, FantasyProjection.normalCdf(0.0), 1e-6)
        assertEquals(0.8413, FantasyProjection.normalCdf(1.0), 1e-3)
        assertEquals(0.0228, FantasyProjection.normalCdf(-2.0), 1e-3)
    }

    @Test fun `sleeper projections follow league scoring`() {
        val json = """[{"player_id":"1","stats":{"pts_ppr":10.0,"pts_half_ppr":8.5,"pts_std":7.0}},{"player_id":"2","stats":{}}]"""
        assertEquals(mapOf("1" to 10.0), SleeperParser.parseProjections(json, 1.0))
        assertEquals(mapOf("1" to 8.5), SleeperParser.parseProjections(json, 0.5))
        assertEquals(mapOf("1" to 7.0), SleeperParser.parseProjections(json, 0.0))
        assertEquals("SFLX", SleeperParser.slotLabel("SUPER_FLEX"))
        assertEquals("Q", SleeperParser.injuryTag("Questionable"))
        assertNull(SleeperParser.headshot("KC"))
    }

    @Test fun `espn projections come from this season's projected stat`() {
        val json = """{"players":[{"player":{"id":42,"fullName":"A","stats":[
            {"seasonId":2025,"scoringPeriodId":7,"statSourceId":1,"appliedTotal":18.2},
            {"seasonId":2026,"scoringPeriodId":7,"statSourceId":0,"appliedTotal":30.0},
            {"seasonId":2026,"scoringPeriodId":7,"statSourceId":1,"appliedTotal":26.0}]}}]}"""
        assertEquals(mapOf("42" to 26.0), EspnFantasyParser.parseDefaultProjections(json, 2026, 7))
        assertEquals("IR", EspnFantasyParser.injuryTag("INJURY_RESERVE"))
        assertNull(EspnFantasyParser.headshot(-16025))
    }
}
