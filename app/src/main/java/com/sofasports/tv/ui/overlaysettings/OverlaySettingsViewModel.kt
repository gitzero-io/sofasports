package com.sofasports.tv.ui.overlaysettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sofasports.tv.data.prefs.OverlayPrefs
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.data.prefs.UserSettings
import com.sofasports.tv.remote.RemoteKeys
import com.sofasports.tv.remote.RemoteSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OverlaySettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val remoteKeys: RemoteKeys,
) : ViewModel() {

    val settings: StateFlow<UserSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val connected: StateFlow<Boolean> = remoteKeys.connected
    val learning: StateFlow<RemoteSlot?> = remoteKeys.learning
    val message: StateFlow<String?> = remoteKeys.message
    val adbCommands: List<String> get() = remoteKeys.adbCommands

    private val _enabledInSettings = MutableStateFlow(remoteKeys.enabledInSettings())
    /** Switched on in Android settings (it may take a moment to connect). */
    val enabledInSettings: StateFlow<Boolean> = _enabledInSettings.asStateFlow()

    fun recheck() { _enabledInSettings.value = remoteKeys.enabledInSettings() }

    fun update(change: (OverlayPrefs) -> OverlayPrefs) = viewModelScope.launch { store.updateOverlayPrefs(change) }

    fun learn(slot: RemoteSlot) = remoteKeys.startLearning(slot)

    fun clear(slot: RemoteSlot) = update {
        when (slot) {
            RemoteSlot.TOGGLE -> it.copy(toggleKey = null)
            RemoteSlot.NEXT -> it.copy(nextKey = null)
        }
    }

    override fun onCleared() {
        remoteKeys.stopLearning(null)
        remoteKeys.clearMessage()
    }
}

/** Adds or removes [item], but never leaves a required set empty. */
internal fun <T> Set<T>.toggled(item: T, keepOne: Boolean = false): Set<T> =
    if (item in this) (this - item).takeUnless { keepOne && it.isEmpty() } ?: this else this + item
