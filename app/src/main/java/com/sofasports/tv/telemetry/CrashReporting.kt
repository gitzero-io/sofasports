package com.sofasports.tv.telemetry

import android.content.Context
import android.util.Log
import io.sentry.android.core.SentryAndroid

/**
 * Sentry crash reporting. The DSN comes from local.properties (`sentry.dsn`) at build time;
 * with no DSN nothing is initialised and the app runs normally.
 */
object CrashReporting {
    @Volatile var enabled: Boolean = false
        private set

    fun isValidDsn(dsn: String?): Boolean =
        !dsn.isNullOrBlank() && dsn.startsWith("https://") && dsn.contains("@")

    fun init(context: Context, dsn: String, release: String, debug: Boolean) {
        if (!isValidDsn(dsn)) {
            Log.i("SofaCrash", "No Sentry DSN set; crash reporting off")
            return
        }
        runCatching {
            SentryAndroid.init(context) { o ->
                o.dsn = dsn
                o.release = release
                o.environment = if (debug) "debug" else "production"
                o.isSendDefaultPii = false
                o.tracesSampleRate = 0.0
                o.isEnableUserInteractionTracing = false
            }
            enabled = true
        }.onFailure { Log.w("SofaCrash", "Sentry init failed", it) }
    }
}
