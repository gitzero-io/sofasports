package com.sofasports.tv

import android.app.Application
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.update.UpdateChecker
import com.sofasports.tv.di.AppScope
import com.sofasports.tv.telemetry.CrashReporting
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import com.sofasports.tv.overlay.OverlayController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SofaApp : Application() {

    @Inject lateinit var config: ConfigRepository
    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var overlay: OverlayController
    @Inject lateinit var updates: UpdateChecker
    @Inject lateinit var usage: UsageEvents

    override fun onCreate() {
        super.onCreate()
        CrashReporting.init(this, BuildConfig.SENTRY_DSN, "sofasports@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}", BuildConfig.DEBUG)
        usage.track(UsageEvent.AppOpen)
        appScope.launch {
            // Bring the overlay back after the process was killed (it's a user setting, not a one-off).
            if (settings.current().overlayEnabled && !config.killed) overlay.start()
        }
        appScope.launch {
            while (true) {
                config.refresh()
                updates.checkIfDue()
                delay(CONFIG_REFRESH_MS)
            }
        }
    }

    private companion object {
        const val CONFIG_REFRESH_MS = 60 * 60 * 1000L
    }
}
