package com.sofasports.tv.overlay

import com.sofasports.tv.data.prefs.MomentKind
import com.sofasports.tv.data.prefs.OverlayPage
import com.sofasports.tv.data.prefs.OverlayPrefs
import com.sofasports.tv.data.prefs.QuietHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayDisplayLogicTest {
    private val pages = listOf(OverlayPage.MATCHUP, OverlayPage.MY_GAME, OverlayPage.PICKS, OverlayPage.MINI)
    private fun ctx(prefs: OverlayPrefs = OverlayPrefs(), now: Long = T.NOW, anyLive: Boolean = true, hour: Int = 14) =
        DisplayContext(prefs, pages, pages, anyLive, hour, now)
    private val start = DisplayState(lastActionMs = T.NOW, lastRotateMs = T.NOW)
    private val td = Moment(MomentKind.MY_GAME_SCORE, "TD", OverlayPage.MY_GAME, MomentTone.MINE, "k1")
    private val smart = OverlayPrefs(smart = true)

    @Test fun `toggle hides and shows`() {
        val c = ctx()
        assertTrue(OverlayDisplayLogic.render(start, c).visible)
        val hidden = OverlayDisplayLogic.toggle(start, c)
        assertFalse(OverlayDisplayLogic.render(hidden, c).visible)
        assertTrue(OverlayDisplayLogic.render(OverlayDisplayLogic.toggle(hidden, c), c).visible)
    }

    @Test fun `step flips pages, and a press while hidden just shows it`() {
        val c = ctx()
        val s1 = OverlayDisplayLogic.step(start, c, forward = true)
        assertEquals(OverlayPage.MY_GAME, OverlayDisplayLogic.render(s1, c).page)
        assertEquals(1, OverlayDisplayLogic.render(s1, c).index)
        val hidden = OverlayDisplayLogic.toggle(s1, c)
        val shown = OverlayDisplayLogic.step(hidden, c, forward = true)
        assertTrue(OverlayDisplayLogic.render(shown, c).visible)
        assertEquals(OverlayPage.MY_GAME, OverlayDisplayLogic.render(shown, c).page)
    }

    @Test fun `no pops without smart mode`() {
        val queued = OverlayDisplayLogic.onMoments(start, listOf(td), ctx())
        assertNull(OverlayDisplayLogic.tick(queued, ctx()).pop)
    }

    @Test fun `smart mode pops over a hidden overlay, then goes back`() {
        val c = ctx(smart)
        val hidden = OverlayDisplayLogic.toggle(start, c)
        val popped = OverlayDisplayLogic.tick(OverlayDisplayLogic.onMoments(hidden, listOf(td), c), c)
        val r = OverlayDisplayLogic.render(popped, c)
        assertTrue(r.visible); assertEquals(OverlayPage.MY_GAME, r.page); assertEquals(td, r.moment)
        val later = ctx(smart, now = T.NOW + OverlayDisplayLogic.POP_MS)
        val done = OverlayDisplayLogic.tick(popped, later)
        assertFalse(OverlayDisplayLogic.render(done, later).visible)
        // The same moment never pops twice.
        assertTrue(OverlayDisplayLogic.onMoments(done, listOf(td), later).queue.isEmpty())
    }

    @Test fun `pop respects the chosen kinds and quiet hours`() {
        val onlyPicks = smart.copy(popKinds = setOf(MomentKind.PICK_RESULT))
        assertTrue(OverlayDisplayLogic.onMoments(start, listOf(td), ctx(onlyPicks)).queue.isEmpty())
        val quiet = smart.copy(quiet = QuietHours.LATE)
        assertTrue(OverlayDisplayLogic.onMoments(start, listOf(td), ctx(quiet, hour = 2)).queue.isEmpty())
        assertFalse(OverlayDisplayLogic.render(start, ctx(quiet, hour = 2)).visible)
    }

    @Test fun `live-only hides between games, a press overrides until games start`() {
        val liveOnly = smart.copy(liveOnly = true)
        val idle = ctx(liveOnly, anyLive = false)
        assertFalse(OverlayDisplayLogic.render(start, idle).visible)
        val forced = OverlayDisplayLogic.toggle(start, idle)
        assertTrue(OverlayDisplayLogic.render(forced, idle).visible)
        // Games start: auto rules say "show", so the override is dropped and it stays shown.
        val live = ctx(liveOnly, anyLive = true)
        val after = OverlayDisplayLogic.tick(forced, live)
        assertNull(after.userHidden)
        assertTrue(OverlayDisplayLogic.render(after, live).visible)
    }

    @Test fun `auto-rotate skips minimized, auto-minimize needs smart mode`() {
        val rotate = OverlayPrefs(rotateSec = 10)
        val s = OverlayDisplayLogic.tick(start, ctx(rotate, now = T.NOW + 10_000))
        assertEquals(OverlayPage.MY_GAME, s.page)
        val wrap = OverlayDisplayLogic.tick(s.copy(page = OverlayPage.PICKS), ctx(rotate, now = T.NOW + 20_000))
        assertEquals(OverlayPage.MATCHUP, wrap.page)

        assertEquals(OverlayPage.MATCHUP, OverlayDisplayLogic.tick(start, ctx(OverlayPrefs(minimizeSec = 30), now = T.NOW + 31_000)).page)
        assertEquals(OverlayPage.MINI, OverlayDisplayLogic.tick(start, ctx(smart.copy(minimizeSec = 30), now = T.NOW + 31_000)).page)
    }
}
