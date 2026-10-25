package com.sofasports.tv.domain

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.espn.EspnNewsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Headlines taken from ESPN's NFL feed on 2026-10-25. */
class NewsFilterTest {
    private fun news(headline: String, kind: String = "HeadlineNews", player: Boolean = true) =
        NewsItem("x", headline, 0, athleteNames = if (player) setOf("Someone") else emptySet(), kind = kind)

    @Test fun `player status news is kept`() = listOf(
        "Seahawks rule out RB Zach Charbonnet vs. Chiefs",
        "Giants LB Tremaine Edmunds (chest) out for year, source says",
        "49ers WR Evans, DE Bosa, WR Samuel out vs. Atlanta; WR Kirk may return",
        "Ravens' Lamar Jackson (high ankle sprain) unsure if he'll play",
        "Browns RBs Quinshon Judkins, Dylan Sampson expected to play",
        "Cam Skattebo set to return to Giants RB mix after being 'taught a lesson'",
        "Rams WR Puka Nacua misses practice, questionable vs. Raiders with calf injury",
        "Vikings activate RB Jordan Mason off injured reserve",
        "Ravens rule out Lamar Jackson (ankle) vs. Bengals; Tyler 'Snoop' Huntley to start",
        "Colts' Tyler Warren to miss second game with groin injury",
    ).forEach { assertTrue(it, NewsFilter.isUseful(news(it))) }

    @Test fun `off-field and fluff headlines are dropped`() = listOf(
        "Cowboys say still time to turn things around after 2-4 start",
        "Lions' Dan Campbell downplays Sam LaPorta's contract comments",
        "Falcons' viral stripes will appear down center of helmets",
    ).forEach { assertFalse(it, NewsFilter.isUseful(news(it))) }

    @Test fun `stories, videos and untagged news are dropped`() {
        assertFalse(NewsFilter.isUseful(news("NFL Week 7 betting guide: the value bets to make on lines, props and anytime TDs", kind = "Story")))
        assertFalse(NewsFilter.isUseful(news("Lamar Jackson 'heading in the right direction' but unsure of return", kind = "Media")))
        assertFalse(NewsFilter.isUseful(news("Roger Goodell says NFL targeting Tokyo game in 2027 or 2028", player = false)))
    }

    @Test fun `fixture feed keeps only the four player updates`() {
        val kept = EspnNewsParser.parse(Fixtures.read("espn/news.json")).filter(NewsFilter::isUseful).map { it.headline }
        assertEquals(4, kept.size)
        assertTrue(kept.none { "uniforms" in it || "rankings" in it })
    }
}
