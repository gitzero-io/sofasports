package com.sofasports.tv.data.picks

import android.content.Context
import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.domain.BetTicket
import com.sofasports.tv.domain.TrackedPick
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
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

/** Hand-entered picks and bet slips, saved on the TV only. Tickets live in their own file so old picks.json stays readable. */
@Singleton
class PicksStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "picks.json")
    private val ticketFile = File(context.filesDir, "tickets.json")
    private val serializer = ListSerializer(TrackedPick.serializer())
    private val ticketSerializer = ListSerializer(BetTicket.serializer())
    private val _picks = MutableStateFlow(load(file, serializer))
    val picks: StateFlow<List<TrackedPick>> = _picks.asStateFlow()
    private val _tickets = MutableStateFlow(load(ticketFile, ticketSerializer))
    val tickets: StateFlow<List<BetTicket>> = _tickets.asStateFlow()
    private val lock = Mutex()

    suspend fun add(pick: TrackedPick) = lock.withLock { save(_picks.value + pick) }

    /** Saves a slip and its legs together; each leg gets the ticket's id. */
    suspend fun addTicket(ticket: BetTicket, legs: List<TrackedPick>) = lock.withLock {
        saveTickets(_tickets.value + ticket)
        save(_picks.value + legs.map { it.copy(ticketId = ticket.id, book = ticket.book) })
    }

    suspend fun remove(id: String) = removeAll(setOf(id))

    /** Removes picks and/or tickets by id; removing a ticket removes its legs. */
    suspend fun removeAll(ids: Set<String>) = lock.withLock {
        val ticketIds = _tickets.value.map { it.id }.filter { it in ids }.toSet()
        if (ticketIds.isNotEmpty()) saveTickets(_tickets.value.filterNot { it.id in ticketIds })
        save(_picks.value.filterNot { it.id in ids || it.ticketId in ticketIds })
    }

    suspend fun clear() = lock.withLock { saveTickets(emptyList()); save(emptyList()) }

    private suspend fun save(list: List<TrackedPick>) {
        _picks.value = list
        withContext(Dispatchers.IO) { runCatching { file.writeText(SofaJson.encodeToString(serializer, list)) } }
    }

    private suspend fun saveTickets(list: List<BetTicket>) {
        _tickets.value = list
        withContext(Dispatchers.IO) { runCatching { ticketFile.writeText(SofaJson.encodeToString(ticketSerializer, list)) } }
    }

    private fun <T> load(f: File, s: KSerializer<List<T>>): List<T> =
        runCatching { SofaJson.decodeFromString(s, f.readText()) }.getOrDefault(emptyList())
}
