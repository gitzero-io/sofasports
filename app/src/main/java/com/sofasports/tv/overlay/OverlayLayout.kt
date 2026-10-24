package com.sofasports.tv.overlay

import android.view.Gravity
import com.sofasports.tv.data.prefs.OverlayCorner

/** Pure mapping so it can be unit tested without a WindowManager. */
object OverlayLayout {
    fun gravityFor(corner: OverlayCorner): Int = when (corner) {
        OverlayCorner.TOP_LEFT -> Gravity.TOP or Gravity.START
        OverlayCorner.TOP_RIGHT -> Gravity.TOP or Gravity.END
        OverlayCorner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
        OverlayCorner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
    }

    /** Inset from the screen edge, in px, inside the TV overscan-safe area (~5%). */
    fun marginPx(screenWidthPx: Int): Int = (screenWidthPx * 0.03f).toInt().coerceAtLeast(24)
}
