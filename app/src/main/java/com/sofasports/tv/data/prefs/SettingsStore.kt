package com.sofasports.tv.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class OverlayCorner { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }

enum class DataMode { LIVE, FIXTURES }

/** News ticker over other apps: a line in the corner card, a full-width bottom bar, both, or neither. */
enum class OverlayTicker(val label: String) { OFF("Off"), CORNER("Corner card line"), BAR("Bottom bar"), BOTH("Both") }

data class UserSettings(
    val onboardingDone: Boolean = false,
    /** Favorite team abbreviations in the order the user picked them. */
    val favoriteTeams: List<String> = emptyList(),
    val sleeperUsername: String? = null,
    val sleeperUserId: String? = null,
    val selectedLeagueId: String? = null,
    val overlayEnabled: Boolean = false,
    val overlayCorner: OverlayCorner = OverlayCorner.TOP_RIGHT,
    val pinnedGameId: String? = null,
    val usageEventsOptOut: Boolean = false,
    /** User chose "Skip for now" on the Home link prompt; linking is still available in Settings. */
    val fantasyPromptSkipped: Boolean = false,
    val showOdds: Boolean = true,
    val tickerInApp: Boolean = true,
    val overlayTicker: OverlayTicker = OverlayTicker.CORNER,
    val dataMode: DataMode = DataMode.LIVE,
    val overlay: OverlayPrefs = OverlayPrefs(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.dataStore

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val favorites = stringPreferencesKey("favorite_teams")
        val sleeperUsername = stringPreferencesKey("sleeper_username")
        val sleeperUserId = stringPreferencesKey("sleeper_user_id")
        val selectedLeague = stringPreferencesKey("selected_league_id")
        val overlayEnabled = booleanPreferencesKey("overlay_enabled")
        val overlayCorner = stringPreferencesKey("overlay_corner")
        val pinnedGame = stringPreferencesKey("pinned_game_id")
        val usageOptOut = booleanPreferencesKey("usage_events_opt_out")
        val fantasySkipped = booleanPreferencesKey("fantasy_prompt_skipped")
        val showOdds = booleanPreferencesKey("show_odds")
        val tickerInApp = booleanPreferencesKey("ticker_in_app")
        val overlayTicker = stringPreferencesKey("overlay_ticker")
        val dataMode = stringPreferencesKey("data_mode")
        val ovPages = stringPreferencesKey("overlay_pages")
        val ovSize = stringPreferencesKey("overlay_size")
        val ovOpacity = stringPreferencesKey("overlay_opacity")
        val ovEdge = stringPreferencesKey("overlay_edge")
        val ovRows = stringPreferencesKey("overlay_rows")
        val ovRotate = intPreferencesKey("overlay_rotate_sec")
        val ovSmart = booleanPreferencesKey("overlay_smart")
        val ovPop = booleanPreferencesKey("overlay_pop")
        val ovPopKinds = stringPreferencesKey("overlay_pop_kinds")
        val ovMinimize = intPreferencesKey("overlay_minimize_sec")
        val ovLiveOnly = booleanPreferencesKey("overlay_live_only")
        val ovQuiet = stringPreferencesKey("overlay_quiet")
        val ovToggleKey = intPreferencesKey("remote_toggle_key")
        val ovNextKey = intPreferencesKey("remote_next_key")
    }

    private inline fun <reified E : Enum<E>> enumOf(v: String?, default: E): E =
        v?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    /** Stored as comma-joined names; unknown names (from a newer build) are dropped. */
    private inline fun <reified E : Enum<E>> enumSet(v: String?, default: Set<E>): Set<E> =
        v?.split(',')?.mapNotNull { n -> enumValues<E>().firstOrNull { it.name == n } }?.toSet() ?: default

    private fun overlayPrefs(p: Preferences) = OverlayPrefs().let { d ->
        OverlayPrefs(
            pages = enumSet(p[Keys.ovPages], d.pages).ifEmpty { d.pages },
            size = enumOf(p[Keys.ovSize], d.size),
            opacity = enumOf(p[Keys.ovOpacity], d.opacity),
            edge = enumOf(p[Keys.ovEdge], d.edge),
            rows = enumSet(p[Keys.ovRows], d.rows),
            rotateSec = p[Keys.ovRotate] ?: d.rotateSec,
            smart = p[Keys.ovSmart] ?: d.smart,
            pop = p[Keys.ovPop] ?: d.pop,
            popKinds = enumSet(p[Keys.ovPopKinds], d.popKinds),
            minimizeSec = p[Keys.ovMinimize] ?: d.minimizeSec,
            liveOnly = p[Keys.ovLiveOnly] ?: d.liveOnly,
            quiet = enumOf(p[Keys.ovQuiet], d.quiet),
            toggleKey = p[Keys.ovToggleKey],
            nextKey = p[Keys.ovNextKey],
        )
    }

    val settings: Flow<UserSettings> = store.data.map { p ->
        UserSettings(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            favoriteTeams = p[Keys.favorites]?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            sleeperUsername = p[Keys.sleeperUsername],
            sleeperUserId = p[Keys.sleeperUserId],
            selectedLeagueId = p[Keys.selectedLeague],
            overlayEnabled = p[Keys.overlayEnabled] ?: false,
            overlayCorner = p[Keys.overlayCorner]?.let { runCatching { OverlayCorner.valueOf(it) }.getOrNull() } ?: OverlayCorner.TOP_RIGHT,
            pinnedGameId = p[Keys.pinnedGame],
            usageEventsOptOut = p[Keys.usageOptOut] ?: false,
            fantasyPromptSkipped = p[Keys.fantasySkipped] ?: false,
            showOdds = p[Keys.showOdds] ?: true,
            tickerInApp = p[Keys.tickerInApp] ?: true,
            overlayTicker = p[Keys.overlayTicker]?.let { runCatching { OverlayTicker.valueOf(it) }.getOrNull() } ?: OverlayTicker.CORNER,
            dataMode = p[Keys.dataMode]?.let { runCatching { DataMode.valueOf(it) }.getOrNull() } ?: DataMode.LIVE,
            overlay = overlayPrefs(p),
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setFavorites(teams: List<String>) = store.edit {
        it[Keys.favorites] = teams.joinToString(",")
        it[Keys.onboardingDone] = true
    }

    suspend fun setSleeper(username: String?, userId: String?) = store.edit {
        if (username.isNullOrBlank() || userId == null) {
            it.remove(Keys.sleeperUsername); it.remove(Keys.sleeperUserId); it.remove(Keys.selectedLeague)
        } else {
            if (it[Keys.sleeperUserId] != userId) it.remove(Keys.selectedLeague)
            it[Keys.sleeperUsername] = username
            it[Keys.sleeperUserId] = userId
        }
    }

    suspend fun setSelectedLeague(leagueId: String?) = store.edit {
        if (leagueId == null) it.remove(Keys.selectedLeague) else it[Keys.selectedLeague] = leagueId
    }

    suspend fun setOverlayEnabled(enabled: Boolean) = store.edit { it[Keys.overlayEnabled] = enabled }
    suspend fun setOverlayCorner(corner: OverlayCorner) = store.edit { it[Keys.overlayCorner] = corner.name }
    suspend fun setPinnedGame(gameId: String?) = store.edit {
        if (gameId == null) it.remove(Keys.pinnedGame) else it[Keys.pinnedGame] = gameId
    }
    suspend fun setUsageEventsOptOut(optOut: Boolean) = store.edit { it[Keys.usageOptOut] = optOut }
    suspend fun setFantasyPromptSkipped(skipped: Boolean) = store.edit { it[Keys.fantasySkipped] = skipped }
    suspend fun setShowOdds(show: Boolean) = store.edit { it[Keys.showOdds] = show }
    suspend fun setTickerInApp(on: Boolean) = store.edit { it[Keys.tickerInApp] = on }
    suspend fun setOverlayTicker(mode: OverlayTicker) = store.edit { it[Keys.overlayTicker] = mode.name }
    suspend fun setDataMode(mode: DataMode) = store.edit { it[Keys.dataMode] = mode.name }

    /** Read-modify-write inside one edit, so quick remote presses can't lose each other's changes. */
    suspend fun updateOverlayPrefs(change: (OverlayPrefs) -> OverlayPrefs) = store.edit { writeOverlay(it, change(overlayPrefs(it))) }

    private fun writeOverlay(it: MutablePreferences, o: OverlayPrefs) {
        it[Keys.ovPages] = o.pages.joinToString(",") { e -> e.name }
        it[Keys.ovSize] = o.size.name
        it[Keys.ovOpacity] = o.opacity.name
        it[Keys.ovEdge] = o.edge.name
        it[Keys.ovRows] = o.rows.joinToString(",") { e -> e.name }
        it[Keys.ovRotate] = o.rotateSec
        it[Keys.ovSmart] = o.smart
        it[Keys.ovPop] = o.pop
        it[Keys.ovPopKinds] = o.popKinds.joinToString(",") { e -> e.name }
        it[Keys.ovMinimize] = o.minimizeSec
        it[Keys.ovLiveOnly] = o.liveOnly
        it[Keys.ovQuiet] = o.quiet.name
        if (o.toggleKey == null) it.remove(Keys.ovToggleKey) else it[Keys.ovToggleKey] = o.toggleKey
        if (o.nextKey == null) it.remove(Keys.ovNextKey) else it[Keys.ovNextKey] = o.nextKey
    }
}
