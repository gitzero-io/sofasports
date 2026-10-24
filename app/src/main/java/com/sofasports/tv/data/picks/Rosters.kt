package com.sofasports.tv.data.picks

import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.data.fixture.FixtureSource
import com.sofasports.tv.data.provider.ApiBases
import com.sofasports.tv.data.provider.DataModeSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

data class RosterPlayer(val id: String, val name: String, val position: String?, val jersey: String?)

@Serializable private data class RosterDto(val athletes: List<RosterGroupDto> = emptyList())
@Serializable private data class RosterGroupDto(val position: String? = null, val items: List<RosterAthleteDto> = emptyList())
@Serializable private data class RosterAthleteDto(val id: String, val displayName: String? = null, val jersey: String? = null, val position: RosterPosDto? = null)
@Serializable private data class RosterPosDto(val abbreviation: String? = null)

object RosterParser {
    private val propPositions = listOf("QB", "RB", "WR", "TE")

    /** Offensive skill players only (the ones props exist for), QB → RB → WR → TE. */
    fun parse(json: String): List<RosterPlayer> =
        SofaJson.decodeFromString(RosterDto.serializer(), json).athletes
            .filter { it.position == "offense" }
            .flatMap { it.items }
            .mapNotNull { a ->
                val pos = a.position?.abbreviation
                if (pos !in propPositions) null else RosterPlayer(a.id, a.displayName ?: a.id, pos, a.jersey)
            }
            .sortedBy { propPositions.indexOf(it.position) }
}

interface RosterProvider {
    suspend fun offense(teamId: String): List<RosterPlayer>
}

@Singleton
class EspnRosterProvider @Inject constructor(private val http: HttpClient, private val bases: ApiBases) : RosterProvider {
    private val cache = mutableMapOf<String, List<RosterPlayer>>()
    override suspend fun offense(teamId: String): List<RosterPlayer> = cache[teamId] ?: run {
        val res = http.get("${bases.espnNflBase}/teams/$teamId/roster")
        if (!res.status.isSuccess()) error("ESPN said HTTP ${res.status.value}")
        RosterParser.parse(res.bodyAsText()).also { cache[teamId] = it }
    }
}

/** Fixture rosters exist for the four replayed teams (HOU, JAX, LAC, KC). */
@Singleton
class FixtureRosterProvider @Inject constructor(private val source: FixtureSource) : RosterProvider {
    override suspend fun offense(teamId: String): List<RosterPlayer> =
        source.read("espn/roster_$teamId.json")?.let(RosterParser::parse) ?: emptyList()
}

@Singleton
class SwitchingRosterProvider @Inject constructor(
    private val mode: DataModeSource,
    private val live: EspnRosterProvider,
    private val fixture: FixtureRosterProvider,
) : RosterProvider {
    override suspend fun offense(teamId: String) = (if (mode.useFixtures()) fixture else live).offense(teamId)
}
