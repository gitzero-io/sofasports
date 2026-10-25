package com.sofasports.tv.overlay

import com.sofasports.tv.domain.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the overlay's show/hide/page state while the overlay service runs. The remote-button
 * service calls [toggle] / [step]; [OverlayDisplayLogic] has the rules.
 */
@Singleton
class OverlayDisplay @Inject constructor(private val feed: OverlayFeed) {
    private val state = MutableStateFlow(DisplayState())
    private val _rendered = MutableStateFlow(RenderedDisplay())
    val rendered: StateFlow<RenderedDisplay> = _rendered.asStateFlow()

    private val _running = MutableStateFlow(false)
    /** True while the overlay service is drawing (it may still be hidden). */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var jobs: List<Job> = emptyList()
    @Volatile private var showOnStart = false

    fun start(scope: CoroutineScope) {
        stop()
        val now = System.currentTimeMillis()
        state.value = DisplayState(lastActionMs = now, lastRotateMs = now)
        _running.value = true
        if (showOnStart) {
            showOnStart = false
            forceShow()
        }
        jobs = listOf(
            scope.launch {
                var prev: OverlayState? = null
                feed.state.collect { cur ->
                    prev?.let { p -> apply { st, ctx -> OverlayDisplayLogic.onMoments(st, BigMoments.detect(p, cur, ctx.prefs.popKinds), ctx) } }
                    prev = cur
                    apply { st, ctx -> OverlayDisplayLogic.tick(st, ctx) }
                }
            },
            scope.launch {
                while (true) {
                    delay(1_000)
                    apply { st, ctx -> OverlayDisplayLogic.tick(st, ctx) }
                }
            },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        _running.value = false
        _rendered.value = RenderedDisplay()
    }

    fun toggle() = apply(OverlayDisplayLogic::toggle)
    fun step(forward: Boolean) = apply { st, ctx -> OverlayDisplayLogic.step(st, ctx, forward) }

    /** The overlay was started by a remote press: show it as soon as the service is up, whatever smart mode says. */
    fun showOnStart() { showOnStart = true }

    private fun forceShow() = apply { st, ctx -> st.copy(userHidden = false, overrideAuto = OverlayDisplayLogic.autoVisible(ctx)) }

    private fun context(): DisplayContext {
        val s = feed.state.value
        val now = System.currentTimeMillis()
        return DisplayContext(
            prefs = s.prefs,
            available = OverlayPages.available(s, s.prefs.pages, now),
            withContent = OverlayPages.withContent(s, now),
            anyLive = s.games.any { it.state == GameState.LIVE },
            hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY),
            nowMs = now,
        )
    }

    @Synchronized
    private fun apply(change: (DisplayState, DisplayContext) -> DisplayState) {
        if (!_running.value) return
        val ctx = context()
        state.update { change(it, ctx) }
        _rendered.value = OverlayDisplayLogic.render(state.value, ctx)
    }
}
