package com.sofasports.tv.data.provider

import android.content.Context
import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.data.sleeper.SleeperParser
import com.sofasports.tv.data.sleeper.SleeperPlayerDto
import com.sofasports.tv.domain.FantasyLeague
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyPlayer
import com.sofasports.tv.domain.FantasyUser
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleeperFantasyProvider @Inject constructor(
    private val http: HttpClient,
    private val bases: ApiBases,
    private val players: PlayerDirectory,
) : FantasyProvider {

    override suspend fun findUser(username: String): FantasyUser? {
        val res = http.get("${bases.sleeperBase}/user/${username.trim().encodeURLPathPart()}")
        if (!res.status.isSuccess()) return null
        return SleeperParser.parseUser(res.bodyAsText())
    }

    override suspend fun leagues(userId: String): List<FantasyLeague> {
        val state = SleeperParser.parseState(http.get("${bases.sleeperBase}/state/nfl").bodyAsText())
        val season = state.season.ifBlank { null } ?: return emptyList()
        return SleeperParser.parseLeagues(http.get("${bases.sleeperBase}/user/$userId/leagues/nfl/$season").bodyAsText())
    }

    override suspend fun currentMatchup(leagueId: String, userId: String): FantasyMatchup? = coroutineScope {
        val base = bases.sleeperBase
        val state = async { SleeperParser.parseState(http.get("$base/state/nfl").bodyAsText()) }
        val league = async { SleeperParser.parseLeague(http.get("$base/league/$leagueId").bodyAsText()) }
        val users = async { SleeperParser.parseLeagueUsers(http.get("$base/league/$leagueId/users").bodyAsText()) }
        val rosters = async { SleeperParser.parseRosters(http.get("$base/league/$leagueId/rosters").bodyAsText()) }
        val week = state.await().let { it.displayWeek ?: it.week }.coerceAtLeast(1)
        val matchups = SleeperParser.parseMatchups(http.get("$base/league/$leagueId/matchups/$week").bodyAsText())
        SleeperParser.buildMatchup(league.await(), week, userId, users.await(), rosters.await(), matchups, players.get())
    }
}

/** Player id → name/position/team. */
interface PlayerDirectory {
    suspend fun get(): Map<String, FantasyPlayer>
}

/**
 * Sleeper asks clients to fetch the 14 MB /players/nfl dump at most once a day.
 * We stream it to disk, trim it to the four fields we use, and keep that small copy.
 */
@Singleton
class SleeperPlayerCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpClient,
    private val bases: ApiBases,
) : PlayerDirectory {
    private val mutex = Mutex()
    private var memory: Map<String, FantasyPlayer>? = null
    private var lastAttemptMs = 0L
    private val file get() = File(context.filesDir, "sleeper_players_trimmed.json")

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun get(): Map<String, FantasyPlayer> = mutex.withLock {
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < ONE_DAY_MS
        val now = System.currentTimeMillis()
        val mayDownload = !fresh && now - lastAttemptMs > RETRY_MS
        memory?.takeIf { fresh || !mayDownload }?.let { return it }
        withContext(Dispatchers.IO) {
            if (mayDownload) {
                lastAttemptMs = now
                runCatching { download() }
            }
            // Even a stale cache beats "Player 4046".
            if (file.exists()) {
                runCatching { SleeperParser.parsePlayers(file.readText()) }.getOrNull()
            } else null
        }.orEmpty().also { memory = it }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun download() {
        val raw = File(context.cacheDir, "sleeper_players_raw.json")
        http.prepareGet("${bases.sleeperBase}/players/nfl").execute { res ->
            if (!res.status.isSuccess()) return@execute
            res.bodyAsChannel().toInputStream().use { input -> raw.outputStream().use { input.copyTo(it) } }
        }
        if (!raw.exists()) return
        val serializer = MapSerializer(String.serializer(), SleeperPlayerDto.serializer())
        val all = raw.inputStream().use { SofaJson.decodeFromStream(serializer, it) }
        val trimmed = all.mapValues { (_, p) ->
            SleeperPlayerDto(fullName = p.fullName ?: listOfNotNull(p.firstName, p.lastName).joinToString(" "), position = p.position, team = p.team)
        }
        val tmp = File(context.filesDir, "sleeper_players_trimmed.tmp")
        tmp.writeText(SofaJson.encodeToString(serializer, trimmed))
        tmp.renameTo(file)
        raw.delete()
    }

    private companion object {
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        const val RETRY_MS = 60 * 60 * 1000L
    }
}
