package com.sofasports.tv.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether our own UI is on screen. The overlay hides then, so it never covers the app itself. */
@Singleton
class AppVisibility @Inject constructor() {
    private val _inForeground = MutableStateFlow(false)
    val inForeground: StateFlow<Boolean> = _inForeground.asStateFlow()
    fun set(visible: Boolean) { _inForeground.value = visible }
}
