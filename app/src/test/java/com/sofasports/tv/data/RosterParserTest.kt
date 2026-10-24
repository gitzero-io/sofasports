package com.sofasports.tv.data

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.picks.RosterParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterParserTest {
    @Test fun `offense skill players only, QB first`() {
        val r = RosterParser.parse(Fixtures.read("espn/roster_34.json"))
        assertTrue(r.isNotEmpty())
        assertEquals("QB", r.first().position)
        assertTrue(r.all { it.position in setOf("QB", "RB", "WR", "TE") })
        assertTrue(r.any { it.name == "C.J. Stroud" })
    }
}
