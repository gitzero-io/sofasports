package com.sofasports.tv.overlay

import android.view.Gravity
import com.sofasports.tv.data.prefs.OverlayCorner

/** Pure mapping so it can be unit tested without a WindowManager. */
object OverlayLayout {
    fun gravityFor(corner: OverlayCorner): Int = when (corner) {
        OverlayCorner.TOP_LEFT -> Gravity.TOP or Gravity.START
        OverlayCorner.TOP_CENTER -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        OverlayCorner.TOP_RIGHT -> Gravity.TOP or Gravity.END
        OverlayCorner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
        OverlayCorner.BOTTOM_CENTER -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        OverlayCorner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
    }

    fun isBottom(corner: OverlayCorner) =
        corner == OverlayCorner.BOTTOM_LEFT || corner == OverlayCorner.BOTTOM_CENTER || corner == OverlayCorner.BOTTOM_RIGHT

    /** Inset from the screen edge, in px, inside the TV overscan-safe area (~5%); [factor] 2 for TVs that crop more. */
    fun marginPx(screenWidthPx: Int, factor: Float = 1f): Int = (screenWidthPx * 0.03f * factor).toInt().coerceAtLeast(24)

    /** Height reserved for the bottom ticker bar when lifting a bottom-corner card above it. */
    fun barHeightPx(density: Float): Int = (56 * density).toInt()
}
