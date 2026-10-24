package com.sofasports.tv.overlay

import android.view.Gravity
import com.sofasports.tv.data.prefs.OverlayCorner
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayLayoutTest {
    @Test fun `each corner maps to its gravity`() {
        assertEquals(Gravity.TOP or Gravity.START, OverlayLayout.gravityFor(OverlayCorner.TOP_LEFT))
        assertEquals(Gravity.TOP or Gravity.END, OverlayLayout.gravityFor(OverlayCorner.TOP_RIGHT))
        assertEquals(Gravity.BOTTOM or Gravity.START, OverlayLayout.gravityFor(OverlayCorner.BOTTOM_LEFT))
        assertEquals(Gravity.BOTTOM or Gravity.END, OverlayLayout.gravityFor(OverlayCorner.BOTTOM_RIGHT))
    }

    @Test fun `margin stays inside overscan and has a floor`() {
        assertEquals(57, OverlayLayout.marginPx(1920))
        assertEquals(24, OverlayLayout.marginPx(320))
    }
}
