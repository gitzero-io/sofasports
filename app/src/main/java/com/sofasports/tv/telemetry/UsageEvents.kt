package com.sofasports.tv.telemetry

import android.util.Log
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.data.prefs.SettingsStore
import com.sofasports.tv.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anonymous product events. Deliberately a closed set with no free-form user data:
 * no usernames, league ids, team names typed by users, or device ids can be sent.
 */
sealed class UsageEvent(val name: String, val props: Map<String, String> = emptyMap()) {
    data object AppOpen : UsageEvent("app_open")
    data class ScreenView(val screen: String) : UsageEvent("screen_view", mapOf("screen" to screen))
    data class FavoritesSaved(val count: Int) : UsageEvent("favorites_saved", mapOf("count" to count.toString()))
    data object SleeperLinked : UsageEvent("sleeper_linked")
    data class EspnLeagueAdded(val private: Boolean) : UsageEvent("espn_league_added", mapOf("private" to private.toString()))
    data class LeagueCount(val count: Int) : UsageEvent("sleeper_leagues", mapOf("count" to count.coerceAtMost(20).toString()))
    data class OverlayToggled(val on: Boolean) : UsageEvent("overlay_toggled", mapOf("on" to on.toString()))
    data object GameOpened : UsageEvent("game_opened")
    data object GamePinned : UsageEvent("game_pinned")
    /** Which app "Watch" opened (an app label, or google_tv_search); never the game. */
    data class WatchOpened(val target: String) : UsageEvent("watch_opened", mapOf("target" to target))
    data class PickAdded(val book: String, val type: String) : UsageEvent("pick_added", mapOf("book" to book, "type" to type))
    data object UpdateChecked : UsageEvent("update_checked")
}

/** Where events go. v0.1 ships only [LogcatUsageSink]; a real backend can implement this later. */
interface UsageSink {
    fun send(event: UsageEvent)
}

class LogcatUsageSink @Inject constructor() : UsageSink {
    override fun send(event: UsageEvent) {
        Log.i("SofaUsage", if (event.props.isEmpty()) event.name else "${event.name} ${event.props}")
    }
}

/** What the app calls. Drops everything when the user opted out or remote config disables events. */
interface UsageEvents {
    fun track(event: UsageEvent)
}

/**
 * Gate in front of the sink. [allowed] returns null while the opt-out is still loading;
 * events are held (up to [MAX_PENDING]) until it's known, then sent or dropped.
 */
class GatedUsageEvents(private val sink: UsageSink, private val allowed: () -> Boolean?) : UsageEvents {
    private val pending = ArrayDeque<UsageEvent>()

    @Synchronized override fun track(event: UsageEvent) {
        when (allowed()) {
            null -> if (pending.size < MAX_PENDING) pending.addLast(event)
            true -> { flush(); send(event) }
            false -> pending.clear()
        }
    }

    /** Call when the opt-out becomes known. */
    @Synchronized fun onDecisionKnown() {
        when (allowed()) {
            true -> flush()
            false -> pending.clear()
            null -> Unit
        }
    }

    private fun flush() { while (pending.isNotEmpty()) send(pending.removeFirst()) }
    private fun send(e: UsageEvent) { runCatching { sink.send(e) } }

    private companion object { const val MAX_PENDING = 20 }
}

@Singleton
class AppUsageEvents @Inject constructor(
    sink: UsageSink,
    settings: SettingsStore,
    config: ConfigRepository,
    @AppScope scope: CoroutineScope,
) : UsageEvents {
    // Null until settings are read, so nothing is sent before the opt-out is known.
    private val allowed = combine(settings.settings, config.config) { s, c -> !s.usageEventsOptOut && c.features.usageEvents }
        .stateIn(scope, SharingStarted.Eagerly, null)
    private val gated = GatedUsageEvents(sink) { allowed.value }

    init {
        scope.launch { allowed.collect { gated.onDecisionKnown() } }
    }

    override fun track(event: UsageEvent) = gated.track(event)
}
