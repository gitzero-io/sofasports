package com.sofasports.tv.domain

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.espn.EspnNewsParser
import com.sofasports.tv.data.espn.EspnParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TickerTest {
    private val news = EspnNewsParser.parse(Fixtures.read("espn/news.json"))
    private val game = EspnParser.parseSummary(Fixtures.read("espn/summary_401872996_hou_jax.json")).game
    private val now = 2_000_000_000_000L

    @Test fun `news fixture parses with team and athlete tags`() {
        assertEquals(25, news.size)
        val lamar = news.first { it.headline.contains("Lamar Jackson") }
        assertTrue("BAL" in lamar.teamAbbrs)
        assertTrue("3916387" in lamar.athleteIds)
        assertTrue("Lamar Jackson" in lamar.athleteNames)
        assertTrue(lamar.publishedMs > 0)
    }

    @Test fun `my items come first, matched by team, player id or name`() {
        fun first(i: TickerInterests) = TickerBuilder.build(news, emptyList(), emptyList(), i, now).first()
        assertTrue(first(TickerInterests(teams = setOf("BAL"))).mine)
        assertTrue(first(TickerInterests(playerIds = setOf("3916387"))).text.contains("Lamar"))
        assertTrue(first(TickerInterests(playerNames = setOf("lamar jackson"))).text.contains("Lamar"))
        assertFalse(TickerBuilder.build(news, emptyList(), emptyList(), TickerInterests(), now).any { it.mine })
    }

    @Test fun `headlines are capped and newest first`() {
        val items = TickerBuilder.build(news, emptyList(), emptyList(), TickerInterests(), now)
        assertEquals(TickerBuilder.MAX_HEADLINES, items.size)
        assertTrue(items.zipWithNext().all { (a, b) -> a.timeMs >= b.timeMs })
    }

    @Test fun `touchdown between polls becomes a score event`() {
        val before = game.copy(state = GameState.LIVE, away = game.away.copy(score = 17), home = game.home.copy(score = 14))
        val after = before.copy(away = before.away.copy(score = 24), situation = Situation(null, null, false, "C.Stroud pass short right to N.Collins for 12 yards, TOUCHDOWN. Extra point is GOOD."))
        val ev = TickerBuilder.scoreEvents(listOf(before), listOf(after), now).single()
        assertEquals("TD HOU · C.Stroud pass short right to N.Collins for 12 yards, TOUCHDOWN · HOU 24–14 JAX", ev.text)
        val fg = TickerBuilder.scoreEvents(listOf(before), listOf(before.copy(home = before.home.copy(score = 17))), now).single()
        assertTrue(fg.text.startsWith("FG JAX"))
        assertTrue(TickerBuilder.scoreEvents(listOf(before), listOf(before), now).isEmpty())
    }

    @Test fun `events expire, live scores show, favorite game events are mine`() {
        val live = game.copy(state = GameState.LIVE, shortDetail = "3rd 6:19")
        val ev = TickerItem("ev-${live.id}-24-14", TickerKind.SCORE_EVENT, "TD HOU", false, now - 60_000)
        val old = ev.copy(key = "ev-${live.id}-17-14", timeMs = now - TickerBuilder.EVENT_TTL_MS - 1)
        val items = TickerBuilder.build(emptyList(), listOf(live), listOf(ev, old), TickerInterests(teams = setOf("JAX")), now)
        assertEquals(listOf(TickerKind.SCORE_EVENT, TickerKind.LIVE_SCORE), items.map { it.kind })
        assertTrue(items.all { it.mine })
        assertEquals("HOU 34  JAX 31 · 3rd 6:19", items[1].text)
    }

    @Test fun `long play text is trimmed to its first sentence`() {
        assertEquals("A short play", TickerBuilder.trimPlay("A short play. Penalty on X declined."))
        assertTrue(TickerBuilder.trimPlay("x".repeat(200)).length <= 88)
    }
}
