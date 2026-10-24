package com.sofasports.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sofasports.tv.data.prefs.DataMode
import com.sofasports.tv.data.prefs.OverlayCorner
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.prefs.UserSettings
import com.sofasports.tv.data.repo.FantasyRepository
import com.sofasports.tv.data.repo.LinkResult
import com.sofasports.tv.overlay.OverlayController
import com.sofasports.tv.telemetry.UsageEvent
import com.sofasports.tv.telemetry.UsageEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val overlay: OverlayController,
    private val fantasy: FantasyRepository,
    private val usage: UsageEvents,
) : ViewModel() {

    val settings: StateFlow<UserSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _canDrawOverlay = MutableStateFlow(overlay.canDraw())
    val canDrawOverlay: StateFlow<Boolean> = _canDrawOverlay.asStateFlow()

    val adbGrantCommand: String get() = overlay.adbGrantCommand

    /** Called on resume: the user may have just run the adb command. */
    fun recheckPermission() {
        _canDrawOverlay.value = overlay.canDraw()
    }

    fun setOverlayEnabled(enabled: Boolean) = viewModelScope.launch {
        recheckPermission()
        if (enabled && !overlay.start()) return@launch
        if (!enabled) overlay.stop()
        store.setOverlayEnabled(enabled)
        usage.track(UsageEvent.OverlayToggled(enabled))
    }

    fun setOverlayCorner(corner: OverlayCorner) = viewModelScope.launch { store.setOverlayCorner(corner) }

    fun setDataMode(mode: DataMode) = viewModelScope.launch { store.setDataMode(mode) }

    fun setShowOdds(show: Boolean) = viewModelScope.launch { store.setShowOdds(show) }

    fun setUsageOptOut(optOut: Boolean) = viewModelScope.launch { store.setUsageEventsOptOut(optOut) }

    suspend fun linkSleeper(username: String): LinkResult = fantasy.link(username)

    fun unlinkSleeper() = viewModelScope.launch { fantasy.unlink() }

    fun clearPinnedGame() = viewModelScope.launch { store.setPinnedGame(null) }
}
