package com.sofasports.tv.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sofasports.tv.BuildConfig
import com.sofasports.tv.R
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.prefs.OverlayCorner
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.ui.theme.SofaSportsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that draws the corner widget over whatever app is playing.
 * The window is not focusable, so the remote keeps controlling the video app.
 */
@AndroidEntryPoint
class OverlayService : LifecycleService(), SavedStateRegistryOwner {

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var feed: OverlayFeed
    @Inject lateinit var config: ConfigRepository
    @Inject lateinit var appVisibility: AppVisibility

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var view: ComposeView? = null
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        startAsForeground()
        lifecycleScope.launch {
            combine(settings.settings.map { it.overlayCorner }, appVisibility.inForeground) { corner, appShown -> corner to appShown }
                .distinctUntilChanged()
                .collect { (corner, appShown) -> if (appShown) removeView() else show(corner) }
        }
        feed.start(lifecycleScope)
        // Remote config can switch the overlay off (feature flag) or kill the whole app.
        lifecycleScope.launch {
            config.config.collect { cfg ->
                if (!cfg.features.overlay || cfg.killSwitch.appliesTo(BuildConfig.VERSION_CODE)) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        removeView()
        super.onDestroy()
    }

    private fun show(corner: OverlayCorner) {
        removeView()
        val margin = OverlayLayout.marginPx(resources.displayMetrics.widthPixels)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE: D-pad keys keep going to the video app. We deliberately don't add
            // NOT_TOUCHABLE: Android caps such windows at 80% opacity, and TVs have no touch anyway.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = OverlayLayout.gravityFor(corner)
            x = margin
            y = margin
        }
        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                SofaSportsTheme {
                    val state by feed.state.collectAsStateWithLifecycle()
                    OverlayWidget(state)
                }
            }
        }
        try {
            windowManager.addView(compose, params)
            view = compose
        } catch (e: Exception) {
            // Permission revoked while running, or the TV's WindowManager refused the overlay type.
            Log.w(TAG, "overlay addView failed", e)
            stopSelf()
        }
    }

    private fun removeView() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Score overlay", NotificationManager.IMPORTANCE_MIN)
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sofa Sports overlay is on")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private companion object {
        const val TAG = "SofaOverlay"
        const val CHANNEL_ID = "overlay"
        const val NOTIFICATION_ID = 42
    }
}
