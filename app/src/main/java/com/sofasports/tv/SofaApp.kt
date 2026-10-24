package com.sofasports.tv

import android.app.Application
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.di.AppScope
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

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Bring the overlay back after the process was killed (it's a user setting, not a one-off).
            if (settings.current().overlayEnabled && !config.killed) overlay.start()
        }
        appScope.launch {
            while (true) {
                config.refresh()
                delay(CONFIG_REFRESH_MS)
            }
        }
    }

    private companion object {
        const val CONFIG_REFRESH_MS = 60 * 60 * 1000L
    }
}
