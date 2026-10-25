package com.sofasports.tv.data.sleeper

import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.domain.FantasyLeague
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyPlayer
import com.sofasports.tv.domain.FantasyStarter
import com.sofasports.tv.domain.FantasyTeam
import com.sofasports.tv.domain.FantasyUser
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Turns Sleeper API JSON into domain models. Pure: no IO. */
object SleeperParser {

    /** Sleeper returns the literal `null` for an unknown username. */
    fun parseUser(json: String): FantasyUser? {
        if (json.isBlank() || json.trim() == "null") return null
        val dto = SofaJson.decodeFromString(SleeperUserDto.serializer(), json)
        val id = dto.userId ?: return null
        return FantasyUser(id, dto.displayName ?: dto.username ?: id, dto.avatar)
    }

    fun parseState(json: String): SleeperStateDto = SofaJson.decodeFromString(SleeperStateDto.serializer(), json)

    fun parseLeague(json: String): FantasyLeague = toLeague(parseLeagueDto(json))

    fun parseLeagueDto(json: String): SleeperLeagueDto = SofaJson.decodeFromString(SleeperLeagueDto.serializer(), json)

    /**
     * Player id → this week's projected points in the league's scoring: PPR, half or standard,
     * whichever is closest to the league's points per reception.
     */
    fun parseProjections(json: String, pointsPerReception: Double): Map<String, Double> {
        val key = when {
            pointsPerReception >= 0.75 -> "pts_ppr"
            pointsPerReception >= 0.25 -> "pts_half_ppr"
            else -> "pts_std"
        }
        return nullableList(json, SleeperProjectionDto.serializer())
            .mapNotNull { p -> p.playerId?.let { id -> p.stats?.get(key)?.let { id to it } } }
            .toMap()
    }

    /** Sleeper slot names → what a lineup shows. */
    fun slotLabel(slot: String): String = when (slot) {
        "FLEX" -> "FLEX"
        "SUPER_FLEX" -> "SFLX"
        "REC_FLEX" -> "W/T"
        "WRRB_FLEX" -> "W/R"
        "IDP_FLEX" -> "IDP"
        "DEF" -> "D/ST"
        else -> slot
    }

    fun injuryTag(status: String?): String? = when (status?.lowercase()) {
        null, "", "active", "healthy" -> null
        "questionable" -> "Q"
        "doubtful" -> "D"
        "out" -> "O"
        "ir", "injured reserve" -> "IR"
        "pup" -> "PUP"
        "sus", "suspended" -> "SUS"
        else -> status.take(3).uppercase()
    }

    fun headshot(playerId: String): String? =
        if (playerId.all { it.isDigit() }) "https://sleepercdn.com/content/nfl/players/thumb/$playerId.jpg" else null

    fun parseLeagues(json: String): List<FantasyLeague> =
        nullableList(json, SleeperLeagueDto.serializer()).map(::toLeague)

    fun parseLeagueUsers(json: String): List<SleeperLeagueUserDto> = nullableList(json, SleeperLeagueUserDto.serializer())

    fun parseRosters(json: String): List<SleeperRosterDto> = nullableList(json, SleeperRosterDto.serializer())

    fun parseMatchups(json: String): List<SleeperMatchupDto> = nullableList(json, SleeperMatchupDto.serializer())

    /** Accepts either the full /players/nfl dump or our trimmed cache (same shape). */
    fun parsePlayers(json: String): Map<String, FantasyPlayer> =
        SofaJson.decodeFromString(MapSerializer(String.serializer(), SleeperPlayerDto.serializer()), json)
            .mapValues { (id, p) -> toPlayer(id, p) }

    fun toPlayer(id: String, p: SleeperPlayerDto) = FantasyPlayer(
        id = id,
        name = p.fullName ?: listOfNotNull(p.firstName, p.lastName).joinToString(" ").ifBlank { id },
        position = p.position,
        team = p.team,
        headshotUrl = headshot(id),
        injury = injuryTag(p.injuryStatus),
    )

    /**
     * Builds "my" matchup for [myUserId] from the league's users, rosters and the week's matchups.
     * Returns null when the user has no roster in this league or no matchup this week.
     */
    fun buildMatchup(
        league: FantasyLeague,
        week: Int,
        myUserId: String,
        users: List<SleeperLeagueUserDto>,
        rosters: List<SleeperRosterDto>,
        matchups: List<SleeperMatchupDto>,
        players: Map<String, FantasyPlayer>,
        rosterPositions: List<String> = emptyList(),
        projections: Map<String, Double> = emptyMap(),
    ): FantasyMatchup? {
        val slots = rosterPositions.filter { it != "BN" && it != "IR" && it != "TAXI" }
        val myRoster = rosters.firstOrNull { it.ownerId == myUserId || myUserId in it.coOwners.orEmpty() } ?: return null
        val mine = matchups.firstOrNull { it.rosterId == myRoster.rosterId } ?: return null
        val theirs = mine.matchupId?.let { id ->
            matchups.firstOrNull { it.matchupId == id && it.rosterId != mine.rosterId }
        }
        val usersById = users.associateBy { it.userId }
        val rostersById = rosters.associateBy { it.rosterId }
        fun team(m: SleeperMatchupDto): FantasyTeam {
            val ownerId = rostersById[m.rosterId]?.ownerId
            val owner = ownerId?.let { usersById[it] }
            val starters = m.starters.orEmpty()
                .withIndex()
                .filter { it.value != "0" } // "0" marks an empty starting slot
                .map { (i, id) ->
                    FantasyStarter(
                        player = players[id] ?: defenseOrUnknown(id),
                        points = m.startersPoints?.getOrNull(i) ?: m.playersPoints?.get(id) ?: 0.0,
                        projected = projections[id],
                        slot = slots.getOrNull(i)?.let(::slotLabel),
                    )
                }
            val record = rostersById[m.rosterId]?.settings?.let { s ->
                if (s.ties > 0) "${s.wins}-${s.losses}-${s.ties}" else "${s.wins}-${s.losses}"
            }
            return FantasyTeam(
                rosterId = m.rosterId,
                ownerId = ownerId,
                teamName = owner?.metadata?.teamName?.takeIf { it.isNotBlank() }
                    ?: owner?.displayName ?: "Team ${m.rosterId}",
                points = m.customPoints ?: m.points ?: starters.sumOf { it.points },
                starters = starters,
                record = record,
            )
        }
        return FantasyMatchup(
            leagueId = league.id,
            leagueName = league.name,
            week = week,
            me = team(mine),
            opponent = theirs?.let(::team),
        )
    }

    /** Team defenses use the team abbreviation as their player id. */
    private fun defenseOrUnknown(id: String) =
        if (id.all { it.isLetter() }) FantasyPlayer(id, "$id D/ST", "DEF", id) else FantasyPlayer(id, "Player $id", null, null)

    fun toLeague(dto: SleeperLeagueDto) = FantasyLeague(
        id = dto.leagueId,
        name = dto.name,
        season = dto.season,
        totalRosters = dto.totalRosters,
        status = dto.status,
    )

    private fun <T> nullableList(json: String, s: kotlinx.serialization.KSerializer<T>): List<T> {
        if (json.isBlank() || json.trim() == "null") return emptyList()
        return SofaJson.decodeFromString(ListSerializer(s), json)
    }
}
