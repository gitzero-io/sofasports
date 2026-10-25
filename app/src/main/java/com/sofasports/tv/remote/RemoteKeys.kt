package com.sofasports.tv.remote

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import com.sofasports.tv.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Which remote button is being set up. */
enum class RemoteSlot { TOGGLE, NEXT }

/**
 * Shared state between the remote-button accessibility service and the setup screen:
 * whether the service is running, and "press the button you want to use" learning.
 */
@Singleton
class RemoteKeys @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val scope: CoroutineScope,
) {
    private val _connected = MutableStateFlow(false)
    /** The accessibility service is enabled and bound. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _learning = MutableStateFlow<RemoteSlot?>(null)
    val learning: StateFlow<RemoteSlot?> = _learning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var timeout: Job? = null

    internal fun setConnected(on: Boolean) {
        _connected.value = on
        if (!on) stopLearning(null)
    }

    fun startLearning(slot: RemoteSlot) {
        _message.value = null
        _learning.value = slot
        timeout?.cancel()
        timeout = scope.launch {
            delay(LEARN_TIMEOUT_MS)
            if (_learning.value == slot) stopLearning("No button pressed. Try again.")
        }
    }

    fun stopLearning(message: String?) {
        timeout?.cancel()
        _learning.value = null
        _message.value = message
    }

    internal fun say(message: String) { _message.value = message }

    fun clearMessage() { _message.value = null }

    val component: ComponentName get() = ComponentName(context, RemoteKeysService::class.java)

    /** True when Android has the service switched on, even if it hasn't bound yet. */
    fun enabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    /** adb commands for TVs that hide accessibility settings for sideloaded apps. */
    val adbCommands: List<String>
        get() = listOf(
            "adb shell appops set ${context.packageName} ACCESS_RESTRICTED_SETTINGS allow",
            "adb shell settings put secure enabled_accessibility_services ${component.flattenToString()}",
            "adb shell settings put secure accessibility_enabled 1",
        )

    companion object {
        const val LEARN_TIMEOUT_MS = 15_000L

        /** Buttons people need while watching (or that Android reserves): never offered for binding. */
        val RESERVED = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        )

        private val NAMES = mapOf(
            KeyEvent.KEYCODE_CHANNEL_UP to "Channel up",
            KeyEvent.KEYCODE_CHANNEL_DOWN to "Channel down",
            KeyEvent.KEYCODE_PROG_RED to "Red button",
            KeyEvent.KEYCODE_PROG_GREEN to "Green button",
            KeyEvent.KEYCODE_PROG_YELLOW to "Yellow button",
            KeyEvent.KEYCODE_PROG_BLUE to "Blue button",
            KeyEvent.KEYCODE_BOOKMARK to "Bookmark (★)",
            KeyEvent.KEYCODE_GUIDE to "Guide",
            KeyEvent.KEYCODE_INFO to "Info",
            KeyEvent.KEYCODE_MENU to "Menu",
            KeyEvent.KEYCODE_SETTINGS to "Settings",
            KeyEvent.KEYCODE_TV_INPUT to "Input",
            KeyEvent.KEYCODE_ALL_APPS to "All apps",
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "Fast forward",
            KeyEvent.KEYCODE_MEDIA_REWIND to "Rewind",
        )

        /** "KEYCODE_CHANNEL_UP" → "Channel up"; unknown vendor codes → "Button 1234". */
        fun keyName(code: Int): String {
            NAMES[code]?.let { return it }
            val raw: String = KeyEvent.keyCodeToString(code) ?: return "Button $code"
            if (!raw.startsWith("KEYCODE_")) return "Button $code"
            return raw.removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
