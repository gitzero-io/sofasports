package com.sofasports.tv.data.espnff

import android.content.Context
import com.sofasports.tv.data.SofaJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One ESPN league + the team that's "mine". Cookies only for private leagues. */
@Serializable
data class EspnLeagueLink(
    val leagueId: String,
    val season: Int,
    val teamId: Int,
    val leagueName: String,
    val teamName: String,
    val espnS2: String? = null,
    val swid: String? = null,
) {
    val key: String get() = "espn:$leagueId:$teamId"
    val isPrivate: Boolean get() = !espnS2.isNullOrBlank()
}

/**
 * Linked ESPN leagues. Kept in their own file (not the settings DataStore) because espn_s2 is an
 * ESPN login cookie: the file is excluded from Android backup (see res/xml backup rules).
 */
@Singleton
class EspnLeagueStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val serializer = ListSerializer(EspnLeagueLink.serializer())
    private val _links = MutableStateFlow(load())
    val links: StateFlow<List<EspnLeagueLink>> = _links.asStateFlow()

    suspend fun add(link: EspnLeagueLink) = save(_links.value.filterNot { it.key == link.key } + link)

    suspend fun remove(key: String) = save(_links.value.filterNot { it.key == key })

    suspend fun clear() = save(emptyList())

    private suspend fun save(list: List<EspnLeagueLink>) {
        _links.value = list
        withContext(Dispatchers.IO) { runCatching { file.writeText(SofaJson.encodeToString(serializer, list)) } }
    }

    private fun load(): List<EspnLeagueLink> =
        runCatching { SofaJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    companion object { const val FILE_NAME = "espn_leagues.json" }
}
