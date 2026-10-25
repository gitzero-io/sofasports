package com.sofasports.tv.data.sleeper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleeperUserDto(
    @SerialName("user_id") val userId: String? = null,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val avatar: String? = null,
)

@Serializable
data class SleeperStateDto(
    val week: Int = 1,
    val season: String = "",
    @SerialName("season_type") val seasonType: String? = null,
    @SerialName("display_week") val displayWeek: Int? = null,
)

@Serializable
data class SleeperLeagueDto(
    @SerialName("league_id") val leagueId: String,
    val name: String = "League",
    val season: String = "",
    val status: String? = null,
    val sport: String? = null,
    @SerialName("total_rosters") val totalRosters: Int = 0,
    /** "rec" is points per reception: 1 = PPR, 0.5 = half, 0 = standard. */
    @SerialName("scoring_settings") val scoringSettings: Map<String, Double>? = null,
    /** Lineup slots in order, including bench ("BN"), IR and taxi slots at the end. */
    @SerialName("roster_positions") val rosterPositions: List<String>? = null,
)

@Serializable
data class SleeperLeagueUserDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val avatar: String? = null,
    val metadata: SleeperUserMetadataDto? = null,
)

@Serializable
data class SleeperUserMetadataDto(@SerialName("team_name") val teamName: String? = null)

@Serializable
data class SleeperRosterDto(
    @SerialName("roster_id") val rosterId: Int,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("co_owners") val coOwners: List<String>? = null,
    val starters: List<String>? = null,
    val players: List<String>? = null,
    val settings: SleeperRosterSettingsDto? = null,
)

@Serializable
data class SleeperRosterSettingsDto(val wins: Int = 0, val losses: Int = 0, val ties: Int = 0)

/** One row of api.sleeper.app/projections (unversioned, undocumented). */
@Serializable
data class SleeperProjectionDto(
    @SerialName("player_id") val playerId: String? = null,
    val stats: Map<String, Double>? = null,
)

@Serializable
data class SleeperMatchupDto(
    @SerialName("roster_id") val rosterId: Int,
    @SerialName("matchup_id") val matchupId: Int? = null,
    val points: Double? = null,
    @SerialName("custom_points") val customPoints: Double? = null,
    val starters: List<String>? = null,
    @SerialName("starters_points") val startersPoints: List<Double>? = null,
    @SerialName("players_points") val playersPoints: Map<String, Double>? = null,
)

@Serializable
data class SleeperPlayerDto(
    @SerialName("player_id") val playerId: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val position: String? = null,
    val team: String? = null,
    @SerialName("injury_status") val injuryStatus: String? = null,
)
