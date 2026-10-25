package com.sofasports.tv.data.espnff

import kotlinx.serialization.Serializable

// ESPN Fantasy Football (lm-api-reads.fantasy.espn.com). Unofficial and undocumented: every field is optional.

@Serializable
data class EspnFfLeagueDto(
    val id: Long = 0,
    val seasonId: Int = 0,
    val scoringPeriodId: Int = 0,
    val status: EspnFfStatusDto? = null,
    val settings: EspnFfSettingsDto? = null,
    val teams: List<EspnFfTeamDto> = emptyList(),
    val schedule: List<EspnFfMatchupDto> = emptyList(),
)

@Serializable data class EspnFfStatusDto(val currentMatchupPeriod: Int? = null, val latestScoringPeriod: Int? = null)
@Serializable data class EspnFfSettingsDto(val name: String? = null)

@Serializable
data class EspnFfTeamDto(
    val id: Int,
    val abbrev: String? = null,
    val name: String? = null,
    /** Pre-2024 seasons split the name. */
    val location: String? = null,
    val nickname: String? = null,
    val owners: List<String> = emptyList(),
    val record: EspnFfRecordDto? = null,
)

@Serializable data class EspnFfRecordDto(val overall: EspnFfWinLossDto? = null)
@Serializable data class EspnFfWinLossDto(val wins: Int = 0, val losses: Int = 0, val ties: Int = 0)

@Serializable
data class EspnFfMatchupDto(
    val id: Int = 0,
    val matchupPeriodId: Int = 0,
    val home: EspnFfSideDto? = null,
    val away: EspnFfSideDto? = null,
)

@Serializable
data class EspnFfSideDto(
    val teamId: Int,
    val totalPoints: Double? = null,
    val totalPointsLive: Double? = null,
    val rosterForCurrentScoringPeriod: EspnFfRosterDto? = null,
)

@Serializable data class EspnFfRosterDto(val entries: List<EspnFfEntryDto> = emptyList())

@Serializable
data class EspnFfEntryDto(
    val lineupSlotId: Int = 20,
    val playerId: Long = 0,
    val playerPoolEntry: EspnFfPoolEntryDto? = null,
)

@Serializable data class EspnFfPoolEntryDto(val appliedStatTotal: Double? = null, val player: EspnFfPlayerDto? = null)

@Serializable
data class EspnFfPlayerDto(
    val id: Long = 0,
    val fullName: String? = null,
    val proTeamId: Int? = null,
    val defaultPositionId: Int? = null,
    /** "ACTIVE", "QUESTIONABLE", "OUT", "INJURY_RESERVE"… */
    val injuryStatus: String? = null,
    /** Actual (statSourceId 0) and projected (1) points per scoring period. */
    val stats: List<EspnFfStatDto> = emptyList(),
)

@Serializable
data class EspnFfStatDto(
    val seasonId: Int = 0,
    val scoringPeriodId: Int = 0,
    val statSourceId: Int = 0,
    val appliedTotal: Double? = null,
)

/** leaguedefaults kona_player_info: ESPN's own projections, used when a league's box score has none. */
@Serializable data class EspnFfPlayersDto(val players: List<EspnFfPoolEntryDto> = emptyList())
