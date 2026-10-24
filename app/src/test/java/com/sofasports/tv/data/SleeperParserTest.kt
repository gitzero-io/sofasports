package com.sofasports.tv.data

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.sleeper.SleeperParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleeperParserTest {

    private val league = SleeperParser.parseLeague(Fixtures.read("sleeper/league.json"))
    private val users = SleeperParser.parseLeagueUsers(Fixtures.read("sleeper/league_users.json"))
    private val rosters = SleeperParser.parseRosters(Fixtures.read("sleeper/league_rosters.json"))
    private val matchups = SleeperParser.parseMatchups(Fixtures.read("sleeper/league_matchups_w1.json"))
    private val players = SleeperParser.parsePlayers(Fixtures.read("sleeper/players_nfl_subset.json"))

    @Test fun `league, users, rosters, matchups parse`() {
        assertEquals("289646328504385536", league.id)
        assertEquals("Sleeper Friends League", league.name)
        assertEquals(12, league.totalRosters)
        assertEquals(14, users.size) // includes members without a roster
        assertEquals(12, rosters.size)
        assertEquals(12, matchups.size)
        assertEquals("Matthew Stafford", players["421"]?.name)
        assertEquals("QB", players["421"]?.position)
    }

    @Test fun `builds my matchup with opponent and starter points`() {
        val m = SleeperParser.buildMatchup(league, 1, "189140835533586432", users, rosters, matchups, players)
        assertNotNull(m)
        m!!
        assertEquals("Kamara is the new DJ", m.me.teamName)
        assertEquals(148.04, m.me.points, 0.001)
        assertEquals(9, m.me.starters.size)
        assertEquals("Matthew Stafford", m.me.starters.first().player.name)
        assertEquals(10.04, m.me.starters.first().points, 0.001)
        assertEquals("DEF", m.me.starters.last().player.position) // team defense, id "PHI"
        assertEquals(3, m.opponent?.rosterId)
        assertEquals(146.52, m.opponent!!.points, 0.001)
        assertTrue(m.opponent!!.teamName.contains("Gordon"))
    }

    @Test fun `user not in league gives no matchup`() {
        assertNull(SleeperParser.buildMatchup(league, 1, "nobody", users, rosters, matchups, players))
    }

    @Test fun `unknown username is null, empty lists are empty`() {
        assertNull(SleeperParser.parseUser("null"))
        assertTrue(SleeperParser.parseLeagues("[]").isEmpty())
        assertTrue(SleeperParser.parseMatchups("null").isEmpty())
        val u = SleeperParser.parseUser("""{"user_id":"1","username":"abc","display_name":"Abc","avatar":null}""")
        assertEquals("1", u?.userId)
        assertEquals("Abc", u?.displayName)
    }

    @Test fun `nfl state parses`() {
        val s = SleeperParser.parseState(Fixtures.read("sleeper/state_nfl.json"))
        assertEquals(7, s.week)
        assertEquals("2026", s.season)
    }
}
