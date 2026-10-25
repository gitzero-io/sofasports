package com.sofasports.tv.remote

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.sofasports.tv.MainActivity
import com.sofasports.tv.data.prefs.OverlayPrefs
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.di.AppScope
import com.sofasports.tv.overlay.OverlayController
import com.sofasports.tv.overlay.OverlayDisplay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Listens for the one or two remote buttons the user picked and turns them into overlay actions.
 * It only filters key events: it never reads the screen, and every other button passes straight through.
 *
 * Show/hide button: press = show/hide, double press = next page, hold = open Sofa Sports.
 * Next-page button (optional): press = next page, double press = previous page, hold = show/hide.
 */
@AndroidEntryPoint
class RemoteKeysService : AccessibilityService() {

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var remoteKeys: RemoteKeys
    @Inject lateinit var overlayDisplay: OverlayDisplay
    @Inject lateinit var overlay: OverlayController
    @Inject @AppScope lateinit var scope: CoroutineScope

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var prefs = OverlayPrefs()
    private var prefsJob: Job? = null
    /** After learning a button, swallow its key-up too. */
    private var swallowUp: Int? = null

    private val gestures = KeyGestures(
        schedule = { delay, action ->
            val r = Runnable(action)
            handler.postDelayed(r, delay)
            KeyGestures.Cancel { handler.removeCallbacks(r) }
        },
        onGesture = ::onGesture,
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefsJob = scope.launch { settings.settings.collect { prefs = it.overlay } }
        remoteKeys.setConnected(true)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (remoteKeys.learning.value != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) learn(code)
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && swallowUp == code) {
            swallowUp = null
            return true
        }
        if (code != prefs.toggleKey && code != prefs.nextKey) return false
        gestures.onKey(code, event.action, event.repeatCount)
        return true
    }

    private fun learn(code: Int) {
        val slot = remoteKeys.learning.value ?: return
        swallowUp = code
        when {
            code == KeyEvent.KEYCODE_BACK -> remoteKeys.stopLearning(null)
            code in RemoteKeys.RESERVED -> remoteKeys.say("${RemoteKeys.keyName(code)} is needed for watching. Press a different button, or Back to cancel.")
            else -> {
                scope.launch {
                    settings.updateOverlayPrefs { p ->
                        // One button can't do both jobs.
                        when (slot) {
                            RemoteSlot.TOGGLE -> p.copy(toggleKey = code, nextKey = p.nextKey.takeIf { it != code })
                            RemoteSlot.NEXT -> p.copy(nextKey = code, toggleKey = p.toggleKey.takeIf { it != code })
                        }
                    }
                }
                remoteKeys.stopLearning("Saved: ${RemoteKeys.keyName(code)}")
            }
        }
    }

    private fun onGesture(code: Int, gesture: Gesture) {
        val isToggle = code == prefs.toggleKey
        when {
            isToggle && gesture == Gesture.SINGLE -> toggle()
            isToggle && gesture == Gesture.DOUBLE -> step(forward = true)
            isToggle && gesture == Gesture.LONG -> openApp()
            gesture == Gesture.SINGLE -> step(forward = true)
            gesture == Gesture.DOUBLE -> step(forward = false)
            gesture == Gesture.LONG -> toggle()
        }
    }

    private fun toggle() {
        if (!ensureRunning()) return
        overlayDisplay.toggle()
    }

    private fun step(forward: Boolean) {
        if (!ensureRunning()) return
        overlayDisplay.step(forward)
    }

    /** A press while the overlay is off turns it on (and shows it), if Android allows drawing it. */
    private fun ensureRunning(): Boolean {
        if (overlayDisplay.running.value) return true
        if (!overlay.start()) return false
        overlayDisplay.showOnStart()
        scope.launch { settings.setOverlayEnabled(true) }
        return false
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        gestures.reset()
        prefsJob?.cancel()
        remoteKeys.setConnected(false)
    }
}
