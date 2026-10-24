package com.sofasports.tv.data.picks

import android.content.Context
import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.domain.TrackedPick
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Hand-entered PrizePicks / DraftKings picks, saved on the TV only. */
@Singleton
class PicksStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "picks.json")
    private val serializer = ListSerializer(TrackedPick.serializer())
    private val _picks = MutableStateFlow(load())
    val picks: StateFlow<List<TrackedPick>> = _picks.asStateFlow()

    suspend fun add(pick: TrackedPick) = save(_picks.value + pick)
    suspend fun remove(id: String) = save(_picks.value.filterNot { it.id == id })
    suspend fun removeAll(ids: Set<String>) = save(_picks.value.filterNot { it.id in ids })
    suspend fun clear() = save(emptyList())

    private suspend fun save(list: List<TrackedPick>) {
        _picks.value = list
        withContext(Dispatchers.IO) { runCatching { file.writeText(SofaJson.encodeToString(serializer, list)) } }
    }

    private fun load(): List<TrackedPick> =
        runCatching { SofaJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
}
