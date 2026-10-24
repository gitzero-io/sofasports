package com.sofasports.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NflTeamsTest {
    @Test fun `32 teams in 8 divisions of 4`() {
        assertEquals(32, NflTeams.all.size)
        assertEquals(32, NflTeams.all.map { it.abbr }.toSet().size)
        assertEquals(8, NflTeams.divisions.size)
        NflTeams.divisions.forEach { assertEquals(4, it.second.size) }
    }

    @Test fun `every team in the week 7 fixture is known`() {
        val sb = com.sofasports.tv.data.espn.EspnParser.parseScoreboard(com.sofasports.tv.Fixtures.read("espn/scoreboard_2026_w7_upcoming.json"))
        sb.games.flatMap { it.teams }.forEach { assertNotNull(it.abbr, NflTeams.find(it.abbr)) }
    }

    @Test fun `toggle keeps pick order, removes, and caps`() {
        var f = listOf<String>()
        f = NflTeams.toggle(f, "SEA"); f = NflTeams.toggle(f, "KC")
        assertEquals(listOf("SEA", "KC"), f)
        f = NflTeams.toggle(f, "SEA")
        assertEquals(listOf("KC"), f)
        listOf("BUF", "DET", "GB", "PHI").forEach { f = NflTeams.toggle(f, it) }
        assertEquals(NflTeams.MAX_FAVORITES, f.size)
        assertEquals(listOf("KC", "BUF", "DET", "GB"), f)
        assertEquals(f, NflTeams.toggle(f, "XYZ".take(2)))
    }
}
