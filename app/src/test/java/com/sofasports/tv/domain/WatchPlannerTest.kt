package com.sofasports.tv.domain

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.espn.EspnParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPlannerTest {
    private val ytv = InstalledApp("com.google.android.youtube.tvunplugged", "YouTube TV")
    private val installed = setOf(ytv.pkg, Streamer.PRIME.pkg, Streamer.PEACOCK.pkg, Streamer.DISNEY.pkg)
    private fun labels(networks: List<String>, liveTv: InstalledApp? = ytv, apps: Set<String> = installed) =
        WatchPlanner.options(networks, liveTv, apps).map { it.label }

    @Test fun `tv network opens the live TV app`() = assertEquals(listOf("YouTube TV"), labels(listOf("CBS")))

    @Test fun `streaming exclusive opens that app, not live TV`() = assertEquals(listOf("Prime Video"), labels(listOf("Prime Video")))

    @Test fun `NBC offers live TV first, then Peacock`() = assertEquals(listOf("YouTube TV", "Peacock"), labels(listOf("NBC")))

    @Test fun `ESPN simulcast on Disney+ offers both`() = assertEquals(listOf("YouTube TV", "Disney+"), labels(listOf("ESPN", "ABC", "Disney+")))

    @Test fun `no live TV app chosen still finds streamers`() {
        assertEquals(listOf("Peacock"), labels(listOf("NBC"), liveTv = null))
        assertTrue(labels(listOf("Fox"), liveTv = null).isEmpty())
    }

    @Test fun `apps that aren't installed are skipped`() {
        assertTrue(labels(listOf("Netflix")).isEmpty())
        assertTrue(labels(listOf("CBS"), apps = emptySet()).isEmpty())
    }

    @Test fun `unknown network falls back to live TV`() = assertEquals(listOf("YouTube TV"), labels(emptyList()))

    @Test fun `parser keeps every network`() {
        val games = listOf("espn/scoreboard_2026_w6_final.json", "espn/scoreboard_2026_w7_upcoming.json")
            .flatMap { EspnParser.parseScoreboard(Fixtures.read(it)).games }
        assertTrue(games.all { it.networks.isNotEmpty() })
        assertTrue(games.any { it.networks == listOf("ESPN", "ABC", "Disney+") })
    }
}
