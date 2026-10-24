package com.sofasports.tv

import android.app.Application
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.di.AppScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SofaApp : Application() {

    @Inject lateinit var config: ConfigRepository
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
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
