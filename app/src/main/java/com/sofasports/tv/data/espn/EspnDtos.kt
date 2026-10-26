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
    /** "UFC 333: Volkanovski vs. Evloev", "United States Grand Prix" */
    val name: String? = null,
    val shortName: String? = null,
    val week: EspnWeekDto? = null,
    val competitions: List<EspnCompetitionDto> = emptyList(),
    val status: EspnStatusDto? = null,
)

@Serializable
data class EspnCompetitionDto(
    val id: String? = null,
    val date: String? = null,
    /** Fights: weight class ("Featherweight"). Racing: session ("FP1", "Qual", "Race"). */
    val type: EspnCompetitionTypeDto? = null,
    val competitors: List<EspnCompetitorDto> = emptyList(),
    val status: EspnStatusDto? = null,
    val broadcasts: List<EspnBroadcastDto> = emptyList(),
    val broadcast: String? = null,
    val situation: EspnSituationDto? = null,
    val venue: EspnVenueDto? = null,
    /** Soccer sends `[null]` when there's no line. */
    val odds: List<EspnOddsDto?> = emptyList(),
)

/**
 * Betting line. ESPN's provider is DraftKings. Scoreboard shape: moneyline.home.close.odds ("+140").
 * Summary (pickcenter) shape: homeTeamOdds.moneyLine (140).
 */
@Serializable
data class EspnOddsDto(
    val provider: EspnOddsProviderDto? = null,
    val details: String? = null,
    val overUnder: Double? = null,
    val spread: Double? = null,
    val moneyline: EspnMoneylineDto? = null,
    val homeTeamOdds: EspnTeamOddsDto? = null,
    val awayTeamOdds: EspnTeamOddsDto? = null,
)

@Serializable data class EspnCompetitionTypeDto(val id: String? = null, val abbreviation: String? = null, val text: String? = null)

@Serializable data class EspnOddsProviderDto(val name: String? = null, val displayName: String? = null)
@Serializable data class EspnMoneylineDto(val home: EspnMoneylineSideDto? = null, val away: EspnMoneylineSideDto? = null)
@Serializable data class EspnMoneylineSideDto(val close: EspnOddsValueDto? = null, val current: EspnOddsValueDto? = null)
@Serializable data class EspnOddsValueDto(val odds: String? = null)
@Serializable data class EspnTeamOddsDto(val moneyLine: Double? = null)

@Serializable
data class EspnCompetitorDto(
    val id: String? = null,
    val homeAway: String? = null,
    /** Team sports. Fights and races have [athlete] instead. */
    val team: EspnTeamDto? = null,
    val athlete: EspnCompetitorAthleteDto? = null,
    /** Fights: 1 and 2. Races: running / finishing position. */
    val order: Int? = null,
    /** String in both scoreboard and summary header, but be lenient. */
    val score: JsonElement? = null,
    val records: List<EspnRecordDto> = emptyList(),
    /** A list in football and basketball, but a plain "0-2" (series record) in MLB and NHL. */
    val record: JsonElement? = null,
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

@Serializable
data class EspnCompetitorAthleteDto(
    val displayName: String? = null,
    val shortName: String? = null,
    val fullName: String? = null,
    val flag: EspnLogoDto? = null,
    val headshot: EspnLogoDto? = null,
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
    // Baseball
    val balls: Int? = null,
    val strikes: Int? = null,
    val outs: Int? = null,
    val onFirst: Boolean? = null,
    val onSecond: Boolean? = null,
    val onThird: Boolean? = null,
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
    val pickcenter: List<EspnOddsDto?> = emptyList(),
    /** Basketball, baseball, hockey: every play, oldest first (football uses [drives]). */
    val plays: List<EspnPlayDto> = emptyList(),
    /** Soccer: goals, cards, subs. */
    val keyEvents: List<EspnPlayDto> = emptyList(),
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
    /** Baseball: "batting" / "pitching" (no [name]). */
    val type: String? = null,
    val labels: List<String> = emptyList(),
    val athletes: List<EspnAthleteStatDto> = emptyList(),
)

@Serializable
data class EspnAthleteStatDto(
    val athlete: EspnAthleteDto? = null,
    val stats: List<String> = emptyList(),
)

@Serializable data class EspnAthleteDto(val id: String? = null, val displayName: String? = null, val shortName: String? = null)

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
    /** Soccer key events say which team; scores come from the running total instead. */
    val team: EspnTeamRefDto? = null,
    val shortText: String? = null,
)

@Serializable data class EspnTeamRefDto(val id: String? = null, val displayName: String? = null)

@Serializable data class EspnPlayTypeDto(val text: String? = null, val type: String? = null)
/** Baseball: type "Top"/"Bottom", displayValue "9th Inning". */
@Serializable data class EspnPeriodDto(val number: Int? = null, val type: String? = null, val displayValue: String? = null)
@Serializable data class EspnClockDto(@SerialName("displayValue") val display: String? = null)
