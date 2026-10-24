package com.sofasports.tv.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Starts/stops the corner overlay and knows whether Android will let us draw it. */
@Singleton
class OverlayController @Inject constructor(@ApplicationContext private val context: Context) {

    /** Most Android TV builds hide the "Display over other apps" screen, so this is usually granted via adb. */
    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    /** adb command that grants the permission for this exact build (debug has a `.debug` suffix). */
    val adbGrantCommand: String
        get() = "adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow"

    /** Returns false (and does nothing) when the permission is missing. */
    fun start(): Boolean {
        if (!canDraw()) return false
        ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        return true
    }

    fun stop() {
        context.stopService(Intent(context, OverlayService::class.java))
    }
}
