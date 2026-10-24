package com.sofasports.tv.data.espn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Minimal DTOs for the public ESPN site API. Only the fields we read; everything has a default
// because ESPN omits fields freely depending on game state.

@Serializable
data class EspnScoreboardDto(
    val season: EspnSeasonDto? = null,
    val week: EspnWeekDto? = null,
    val events: List<EspnEventDto> = emptyList(),
)

@Serializable data class EspnSeasonDto(val year: Int? = null, val type: Int? = null)
@Serializable data class EspnWeekDto(val number: Int? = null)

@Serializable
data class EspnEventDto(
    val id: String,
    val date: String? = null,
    val week: EspnWeekDto? = null,
    val competitions: List<EspnCompetitionDto> = emptyList(),
    val status: EspnStatusDto? = null,
)

@Serializable
data class EspnCompetitionDto(
    val id: String? = null,
    val date: String? = null,
    val competitors: List<EspnCompetitorDto> = emptyList(),
    val status: EspnStatusDto? = null,
    val broadcasts: List<EspnBroadcastDto> = emptyList(),
    val broadcast: String? = null,
    val situation: EspnSituationDto? = null,
    val venue: EspnVenueDto? = null,
)

@Serializable
data class EspnCompetitorDto(
    val id: String? = null,
    val homeAway: String? = null,
    val team: EspnTeamDto,
    /** String in both scoreboard and summary header, but be lenient. */
    val score: JsonElement? = null,
    val records: List<EspnRecordDto> = emptyList(),
    val record: List<EspnRecordDto> = emptyList(),
    val winner: Boolean? = null,
)

@Serializable
data class EspnTeamDto(
    val id: String,
    val abbreviation: String = "",
    val displayName: String = "",
    val shortDisplayName: String = "",
    val name: String? = null,
    val logo: String? = null,
    val logos: List<EspnLogoDto> = emptyList(),
    val color: String? = null,
)

@Serializable data class EspnLogoDto(val href: String? = null)
@Serializable data class EspnRecordDto(val type: String? = null, val summary: String? = null)
/** Scoreboard: {"market":"national","names":["CBS"]}. Summary header: {"market":{...},"media":{"shortName":"CBS"}}. */
@Serializable data class EspnBroadcastDto(val names: List<String> = emptyList(), val media: EspnMediaDto? = null)
@Serializable data class EspnMediaDto(val shortName: String? = null)

@Serializable
data class EspnStatusDto(
    val clock: Double? = null,
    val displayClock: String? = null,
    val period: Int? = null,
    val type: EspnStatusTypeDto? = null,
)

@Serializable
data class EspnStatusTypeDto(
    val name: String? = null,
    val state: String? = null,
    val completed: Boolean? = null,
    val detail: String? = null,
    val shortDetail: String? = null,
)

@Serializable
data class EspnSituationDto(
    val downDistanceText: String? = null,
    val shortDownDistanceText: String? = null,
    val possession: String? = null,
    val isRedZone: Boolean? = null,
    val lastPlay: EspnLastPlayDto? = null,
    val homeTimeouts: Int? = null,
    val awayTimeouts: Int? = null,
)

@Serializable data class EspnLastPlayDto(val text: String? = null)
@Serializable data class EspnVenueDto(val fullName: String? = null)

// ---- summary?event= ----

@Serializable
data class EspnSummaryDto(
    val header: EspnHeaderDto,
    val boxscore: EspnBoxscoreDto? = null,
    val drives: EspnDrivesDto? = null,
    val winprobability: List<EspnWinProbDto> = emptyList(),
    val scoringPlays: List<EspnPlayDto> = emptyList(),
    val gameInfo: EspnGameInfoDto? = null,
)

@Serializable
data class EspnHeaderDto(
    val id: String,
    val week: Int? = null,
    val competitions: List<EspnCompetitionDto> = emptyList(),
)

@Serializable data class EspnGameInfoDto(val venue: EspnVenueDto? = null)

@Serializable
data class EspnWinProbDto(
    val playId: String = "",
    val homeWinPercentage: Double = 0.5,
    val tiePercentage: Double = 0.0,
)

@Serializable
data class EspnBoxscoreDto(
    val teams: List<EspnBoxTeamDto> = emptyList(),
    val players: List<EspnBoxPlayersDto> = emptyList(),
)

@Serializable
data class EspnBoxTeamDto(
    val team: EspnTeamDto,
    val homeAway: String? = null,
    val statistics: List<EspnTeamStatDto> = emptyList(),
)

@Serializable
data class EspnTeamStatDto(
    val name: String? = null,
    val label: String? = null,
    val displayValue: String? = null,
)

@Serializable
data class EspnBoxPlayersDto(
    val team: EspnTeamDto,
    val statistics: List<EspnPlayerCategoryDto> = emptyList(),
)

@Serializable
data class EspnPlayerCategoryDto(
    val name: String? = null,
    val text: String? = null,
    val labels: List<String> = emptyList(),
    val athletes: List<EspnAthleteStatDto> = emptyList(),
)

@Serializable
data class EspnAthleteStatDto(
    val athlete: EspnAthleteDto? = null,
    val stats: List<String> = emptyList(),
)

@Serializable data class EspnAthleteDto(val displayName: String? = null, val shortName: String? = null)

@Serializable
data class EspnDrivesDto(
    val previous: List<EspnDriveDto> = emptyList(),
    val current: EspnDriveDto? = null,
)

@Serializable
data class EspnDriveDto(
    val id: String = "",
    val description: String? = null,
    val team: EspnTeamDto? = null,
    val displayResult: String? = null,
    val isScore: Boolean? = null,
    val plays: List<EspnPlayDto> = emptyList(),
)

@Serializable
data class EspnPlayDto(
    val id: String = "",
    val text: String? = null,
    val type: EspnPlayTypeDto? = null,
    val awayScore: Int? = null,
    val homeScore: Int? = null,
    val period: EspnPeriodDto? = null,
    val clock: EspnClockDto? = null,
    val scoringPlay: Boolean? = null,
)

@Serializable data class EspnPlayTypeDto(val text: String? = null)
@Serializable data class EspnPeriodDto(val number: Int? = null)
@Serializable data class EspnClockDto(@SerialName("displayValue") val display: String? = null)
