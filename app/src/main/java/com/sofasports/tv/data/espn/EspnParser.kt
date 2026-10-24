package com.sofasports.tv.data.espn

import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.domain.Drive
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.Play
import com.sofasports.tv.domain.PlayerStatRow
import com.sofasports.tv.domain.PlayerStatTable
import com.sofasports.tv.domain.Scoreboard
import com.sofasports.tv.domain.Situation
import com.sofasports.tv.domain.TeamSide
import com.sofasports.tv.domain.TeamStatRow
import com.sofasports.tv.domain.WinProbPoint
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.OffsetDateTime

/** Turns ESPN site-API JSON into domain models. Pure: no IO, safe to unit test against fixtures. */
object EspnParser {

    /** Player stat categories shown in the box score, in display order. */
    private val boxCategories = listOf("passing", "rushing", "receiving")

    fun parseScoreboard(json: String): Scoreboard {
        val dto = SofaJson.decodeFromString(EspnScoreboardDto.serializer(), json)
        return Scoreboard(
            season = dto.season?.year,
            week = dto.week?.number,
            games = dto.events.mapNotNull { event -> toGame(event, dto.week?.number) },
        )
    }

    fun parseSummary(json: String): GameSummary {
        val dto = SofaJson.decodeFromString(EspnSummaryDto.serializer(), json)
        val comp = dto.header.competitions.firstOrNull()
            ?: error("summary ${dto.header.id} has no competition")
        val game = toGame(dto.header.id, comp.date, comp, comp.status, dto.header.week)
            ?: error("summary ${dto.header.id} is missing teams")

        val teamStats = parseTeamStats(dto.boxscore, game)
        val playerStats = dto.boxscore?.players.orEmpty().flatMap { team ->
            team.statistics
                .filter { it.name in boxCategories }
                .sortedBy { boxCategories.indexOf(it.name) }
                .map { cat ->
                    PlayerStatTable(
                        teamAbbr = team.team.abbreviation,
                        category = cat.text ?: cat.name.orEmpty().replaceFirstChar { it.uppercase() },
                        labels = cat.labels,
                        rows = cat.athletes.map { a ->
                            PlayerStatRow(a.athlete?.displayName ?: a.athlete?.shortName ?: "—", a.stats)
                        },
                    )
                }
        }
        val allDrives = dto.drives?.previous.orEmpty() + listOfNotNull(dto.drives?.current)
        val drives = allDrives
            .distinctBy { it.id }
            .map { d ->
                Drive(
                    id = d.id,
                    teamAbbr = d.team?.abbreviation,
                    description = d.description,
                    result = d.displayResult,
                    isScore = d.isScore == true,
                    plays = d.plays.map(::toPlay),
                )
            }
        return GameSummary(
            game = game,
            winProbability = dto.winprobability.map { WinProbPoint(it.playId, it.homeWinPercentage, it.tiePercentage) },
            teamStats = teamStats,
            playerStats = playerStats,
            drives = drives,
            scoringPlays = dto.scoringPlays.map(::toPlay),
            venue = dto.gameInfo?.venue?.fullName ?: comp.venue?.fullName,
        )
    }

    private fun parseTeamStats(box: EspnBoxscoreDto?, game: Game): List<TeamStatRow> {
        val teams = box?.teams.orEmpty()
        val away = teams.firstOrNull { it.team.id == game.away.id } ?: return emptyList()
        val home = teams.firstOrNull { it.team.id == game.home.id } ?: return emptyList()
        val homeByName = home.statistics.associateBy { it.name }
        return away.statistics.mapNotNull { a ->
            val h = homeByName[a.name] ?: return@mapNotNull null
            TeamStatRow(label = a.label ?: a.name.orEmpty(), away = a.displayValue.orEmpty(), home = h.displayValue.orEmpty())
        }
    }

    private fun toPlay(p: EspnPlayDto) = Play(
        id = p.id,
        period = p.period?.number ?: 0,
        clock = p.clock?.display.orEmpty(),
        text = p.text.orEmpty(),
        type = p.type?.text,
        awayScore = p.awayScore ?: 0,
        homeScore = p.homeScore ?: 0,
        isScoring = p.scoringPlay == true,
    )

    private fun toGame(event: EspnEventDto, fallbackWeek: Int?): Game? {
        val comp = event.competitions.firstOrNull() ?: return null
        return toGame(event.id, event.date ?: comp.date, comp, event.status ?: comp.status, event.week?.number ?: fallbackWeek)
    }

    private fun toGame(id: String, date: String?, comp: EspnCompetitionDto, status: EspnStatusDto?, week: Int?): Game? {
        val homeDto = comp.competitors.firstOrNull { it.homeAway == "home" } ?: return null
        val awayDto = comp.competitors.firstOrNull { it.homeAway == "away" } ?: return null
        val state = stateOf(status)
        val sit = comp.situation
        return Game(
            id = id,
            startEpochMs = parseDate(date),
            state = state,
            home = toSide(homeDto, isHome = true, state, sit?.homeTimeouts),
            away = toSide(awayDto, isHome = false, state, sit?.awayTimeouts),
            period = status?.period ?: 0,
            clock = status?.displayClock.orEmpty(),
            shortDetail = status?.type?.shortDetail ?: status?.type?.detail.orEmpty(),
            broadcast = comp.broadcast?.takeIf { it.isNotBlank() }
                ?: comp.broadcasts.firstNotNullOfOrNull { it.names.firstOrNull() ?: it.media?.shortName },
            situation = sit?.let {
                Situation(
                    downDistance = it.shortDownDistanceText ?: it.downDistanceText,
                    possessionTeamId = it.possession,
                    isRedZone = it.isRedZone == true,
                    lastPlay = it.lastPlay?.text,
                )
            },
            week = week,
        )
    }

    private fun toSide(c: EspnCompetitorDto, isHome: Boolean, state: GameState, timeouts: Int?): TeamSide {
        val records = c.records.ifEmpty { c.record }
        return TeamSide(
            id = c.team.id,
            abbr = c.team.abbreviation,
            name = c.team.displayName,
            shortName = c.team.shortDisplayName.ifEmpty { c.team.name.orEmpty() },
            logoUrl = c.team.logo ?: c.team.logos.firstOrNull()?.href,
            // ESPN reports "0" for games that haven't started; hide it.
            score = if (state == GameState.PRE) null else (c.score as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            record = records.firstOrNull { it.type == "total" }?.summary ?: records.firstOrNull()?.summary,
            isHome = isHome,
            timeouts = timeouts,
        )
    }

    internal fun stateOf(status: EspnStatusDto?): GameState = when (status?.type?.state) {
        "pre" -> GameState.PRE
        "in" -> GameState.LIVE
        "post" -> if (status.type.completed == false) GameState.OTHER else GameState.FINAL
        else -> GameState.OTHER
    }

    /** ESPN dates look like "2026-10-25T17:00Z" (no seconds), which Instant.parse rejects on older JDKs. */
    internal fun parseDate(date: String?): Long =
        date?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
}
