package com.sofasports.tv.data

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.espn.EspnParser
import com.sofasports.tv.data.espnff.EspnFantasyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EspnFantasyParserTest {
    private val league = EspnFantasyParser.parseLeague(Fixtures.read("espn_fantasy/league_teams.json"))

    @Test fun `league and teams parse`() {
        assertEquals("Fixture ESPN League", league.name)
        assertEquals(2026, league.season)
        assertEquals(7, league.currentMatchupPeriod)
        assertEquals(12, league.teams.size)
        assertEquals("Team 1", league.teams.first { it.id == 1 }.name)
    }

    @Test fun `week 6 final matchup for team 3`() {
        val m = EspnFantasyParser.buildMatchup(
            league.copy(currentMatchupPeriod = 6), Fixtures.read("espn_fantasy/league_box_w6.json"), 3, "espn:1:3",
        )!!
        assertEquals(6, m.week)
        assertEquals(112.9, m.me.points, 0.001)
        assertEquals(10, m.opponent!!.rosterId)
        assertEquals(87.98, m.opponent!!.points, 0.001)
        // Bench (slot 20) excluded, QB first, K/D-ST last.
        assertEquals("QB", m.me.starters.first().player.position)
        assertEquals("Drake Maye", m.me.starters.first().player.name)
        assertEquals("NE", m.me.starters.first().player.team)
        assertTrue(m.me.starters.none { it.player.name == "Chase Brown" })
        assertEquals("K", m.me.starters.last().player.position)
    }

    @Test fun `week 7 in progress uses live points`() {
        val m = EspnFantasyParser.buildMatchup(league, Fixtures.read("espn_fantasy/league_box_w7.json"), 5, "espn:1:5")!!
        assertEquals(43.6, m.me.points, 0.001)
    }

    @Test fun `unknown team has no matchup`() =
        assertNull(EspnFantasyParser.buildMatchup(league, Fixtures.read("espn_fantasy/league_box_w6.json"), 999, "x"))

    @Test fun `SWID picks the owner's team, braces optional`() {
        val owner = league.teams.first { it.id == 4 }.ownerIds.first()
        assertEquals(4, EspnFantasyParser.teamForSwid(league, owner)?.id)
        assertEquals(4, EspnFantasyParser.teamForSwid(league, owner.trim('{', '}').lowercase())?.id)
        assertNull(EspnFantasyParser.teamForSwid(league, null))
    }

    @Test fun `season rolls over in March`() {
        assertEquals(2026, EspnFantasyParser.seasonFor(LocalDate.of(2026, 10, 24)))
        assertEquals(2025, EspnFantasyParser.seasonFor(LocalDate.of(2026, 1, 15)))
        assertEquals(2026, EspnFantasyParser.seasonFor(LocalDate.of(2026, 3, 1)))
    }

    @Test fun `pro team ids match the ESPN scoreboard ids`() {
        val sb = EspnParser.parseScoreboard(Fixtures.read("espn/scoreboard_2026_w6_final.json"))
        sb.games.flatMap { it.teams }.forEach { t -> assertEquals(t.abbr, EspnFantasyParser.proTeams[t.id.toInt()]) }
    }
}
