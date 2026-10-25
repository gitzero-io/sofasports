package com.sofasports.tv.overlay

import android.view.Gravity
import com.sofasports.tv.data.prefs.OverlayCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayoutTest {
    @Test fun `each position maps to its gravity`() {
        assertEquals(Gravity.TOP or Gravity.START, OverlayLayout.gravityFor(OverlayCorner.TOP_LEFT))
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, OverlayLayout.gravityFor(OverlayCorner.TOP_CENTER))
        assertEquals(Gravity.TOP or Gravity.END, OverlayLayout.gravityFor(OverlayCorner.TOP_RIGHT))
        assertEquals(Gravity.BOTTOM or Gravity.START, OverlayLayout.gravityFor(OverlayCorner.BOTTOM_LEFT))
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, OverlayLayout.gravityFor(OverlayCorner.BOTTOM_CENTER))
        assertEquals(Gravity.BOTTOM or Gravity.END, OverlayLayout.gravityFor(OverlayCorner.BOTTOM_RIGHT))
        assertTrue(OverlayLayout.isBottom(OverlayCorner.BOTTOM_CENTER))
        assertFalse(OverlayLayout.isBottom(OverlayCorner.TOP_CENTER))
    }

    @Test fun `margin stays inside overscan, has a floor, and doubles for Extra`() {
        assertEquals(57, OverlayLayout.marginPx(1920))
        assertEquals(115, OverlayLayout.marginPx(1920, 2f))
        assertEquals(24, OverlayLayout.marginPx(320))
    }
}
