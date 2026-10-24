package com.sofasports.tv.ui

import com.sofasports.tv.ui.components.Qr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTest {
    @Test fun `encodes a url into a square matrix with dark modules`() {
        val m = Qr.matrix("https://example.com/sofasports-feedback", 200)
        assertEquals(200, m.width)
        assertEquals(200, m.height)
        var dark = 0
        for (x in 0 until m.width) for (y in 0 until m.height) if (m[x, y]) dark++
        assertTrue(dark > 1000)
    }
}
