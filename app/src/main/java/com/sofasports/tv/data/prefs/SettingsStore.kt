package com.sofasports.tv.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class OverlayCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

enum class DataMode { LIVE, FIXTURES }

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
    val dataMode: DataMode = DataMode.LIVE,
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
        val dataMode = stringPreferencesKey("data_mode")
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
            dataMode = p[Keys.dataMode]?.let { runCatching { DataMode.valueOf(it) }.getOrNull() } ?: DataMode.LIVE,
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
    suspend fun setDataMode(mode: DataMode) = store.edit { it[Keys.dataMode] = mode.name }
}
