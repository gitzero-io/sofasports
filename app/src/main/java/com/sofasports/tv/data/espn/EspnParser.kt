package com.sofasports.tv.data.espn

import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.domain.Drive
import com.sofasports.tv.domain.EventFormat
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameOdds
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.Periods
import com.sofasports.tv.domain.Play
import com.sofasports.tv.domain.PlayerStatRow
import com.sofasports.tv.domain.PlayerStatTable
import com.sofasports.tv.domain.Race
import com.sofasports.tv.domain.RaceEntry
import com.sofasports.tv.domain.RaceSession
import com.sofasports.tv.domain.Scoreboard
import com.sofasports.tv.domain.Situation
import com.sofasports.tv.domain.Sport
import com.sofasports.tv.domain.TeamSide
import com.sofasports.tv.domain.TeamStatRow
import com.sofasports.tv.domain.WinProbPoint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.OffsetDateTime

/** Turns ESPN site-API JSON into domain models. Pure: no IO, safe to unit test against fixtures. */
object EspnParser {

    /** Football player stat categories shown in the box score, in display order. Other sports show all of theirs. */
    private val footballBoxCategories = listOf("passing", "rushing", "receiving", "kicking")

    fun parseScoreboard(json: String, league: League = League.NFL): Scoreboard {
        val dto = SofaJson.decodeFromString(EspnScoreboardDto.serializer(), json)
        val week = dto.week?.number.takeIf { league.weekly }
        return when (league.format) {
            EventFormat.RACE -> Scoreboard(dto.season?.year, null, emptyList(), league, races = dto.events.mapNotNull { toRace(it, league) })
            EventFormat.FIGHT -> Scoreboard(dto.season?.year, null, dto.events.flatMap { toFights(it, league) }, league)
            EventFormat.TEAMS -> Scoreboard(
                season = dto.season?.year,
                week = week,
                games = dto.events.mapNotNull { event -> toGame(event, week, league) },
                league = league,
            )
        }
    }

    fun parseSummary(json: String, league: League = League.NFL): GameSummary {
        val dto = SofaJson.decodeFromString(EspnSummaryDto.serializer(), json)
        val comp = dto.header.competitions.firstOrNull()
            ?: error("summary ${dto.header.id} has no competition")
        val parsed = toGame(dto.header.id, comp.date, comp, comp.status, dto.header.week.takeIf { league.weekly }, league)
            ?: error("summary ${dto.header.id} is missing teams")
        val game = if (parsed.odds == null) parsed.copy(odds = toOdds(dto.pickcenter, parsed)) else parsed

        val teamStats = parseTeamStats(dto.boxscore, game)
        val playerStats = dto.boxscore?.players.orEmpty().flatMap { team ->
            val categories = if (league.sport == Sport.FOOTBALL) {
                team.statistics.filter { it.name in footballBoxCategories }.sortedBy { footballBoxCategories.indexOf(it.name) }
            } else {
                team.statistics.filter { it.athletes.isNotEmpty() }
            }
            categories.map { cat ->
                val key = cat.name ?: cat.type ?: ""
                PlayerStatTable(
                    teamAbbr = team.team.abbreviation,
                    key = key,
                    category = cat.text ?: key.replaceFirstChar { it.uppercase() }.ifEmpty { "Players" },
                    labels = cat.labels,
                    rows = cat.athletes.map { a ->
                        PlayerStatRow(a.athlete?.displayName ?: a.athlete?.shortName ?: "—", a.stats, a.athlete?.id)
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
                    plays = d.plays.map { toPlay(it, league) },
                )
            }
        val plays = when {
            drives.isNotEmpty() -> emptyList()
            league.sport == Sport.SOCCER -> soccerEvents(dto.keyEvents, game)
            else -> dto.plays.filter { !it.text.isNullOrBlank() }.map { toPlay(it, league) }
        }
        val scoring = when {
            dto.scoringPlays.isNotEmpty() -> dto.scoringPlays.map { toPlay(it, league) }
            // Every basket is a "scoring play"; listing them all says nothing.
            league.sport == Sport.BASKETBALL -> emptyList()
            else -> plays.filter { it.isScoring }
        }
        return GameSummary(
            game = game,
            winProbability = dto.winprobability.map { WinProbPoint(it.playId, it.homeWinPercentage, it.tiePercentage) },
            teamStats = teamStats,
            playerStats = playerStats,
            drives = drives,
            scoringPlays = scoring,
            venue = dto.gameInfo?.venue?.fullName ?: comp.venue?.fullName,
            plays = plays,
        )
    }

    private fun parseTeamStats(box: EspnBoxscoreDto?, game: Game): List<TeamStatRow> {
        val teams = box?.teams.orEmpty()
        val away = teams.firstOrNull { it.team.id == game.away.id } ?: return emptyList()
        val home = teams.firstOrNull { it.team.id == game.home.id } ?: return emptyList()
        val homeByName = home.statistics.associateBy { it.name }
        return away.statistics.mapNotNull { a ->
            val h = homeByName[a.name] ?: return@mapNotNull null
            // Baseball nests its stats in groups with no value of their own; skip those.
            if (a.displayValue == null && h.displayValue == null) return@mapNotNull null
            TeamStatRow(label = a.label ?: a.name.orEmpty(), away = a.displayValue.orEmpty(), home = h.displayValue.orEmpty())
        }
    }

    private fun toPlay(p: EspnPlayDto, league: League): Play {
        val period = p.period?.number ?: 0
        val label = when (league.sport) {
            Sport.BASEBALL -> p.period?.type?.let { half -> "${if (half.startsWith("B")) "Bot" else "Top"} ${Periods.ordinal(period)}" }
            else -> null
        }
        return Play(
            id = p.id,
            period = period,
            clock = p.clock?.display.orEmpty(),
            text = p.text.orEmpty(),
            type = p.type?.text,
            awayScore = p.awayScore ?: 0,
            homeScore = p.homeScore ?: 0,
            isScoring = p.scoringPlay == true,
            periodLabel = label,
        )
    }

    /** Soccer key events carry the minute ("47'") and the team, but not the score; keep a running total. */
    private fun soccerEvents(events: List<EspnPlayDto>, game: Game): List<Play> {
        var home = 0
        var away = 0
        return events.filter { !it.text.isNullOrBlank() }.map { e ->
            val scoring = e.scoringPlay == true
            if (scoring) {
                when (e.team?.id) {
                    game.home.id -> home++
                    game.away.id -> away++
                }
            }
            Play(
                id = e.id,
                period = e.period?.number ?: 0,
                clock = "",
                text = e.text.orEmpty(),
                type = e.type?.text,
                awayScore = away,
                homeScore = home,
                isScoring = scoring,
                periodLabel = e.clock?.display?.takeIf { it.isNotBlank() } ?: Periods.label(game.league, e.period?.number ?: 0),
            )
        }
    }

    private fun toGame(event: EspnEventDto, fallbackWeek: Int?, league: League): Game? {
        val comp = event.competitions.firstOrNull() ?: return null
        return toGame(event.id, event.date ?: comp.date, comp, event.status ?: comp.status, event.week?.number ?: fallbackWeek, league)
    }

    private fun toGame(id: String, date: String?, comp: EspnCompetitionDto, status: EspnStatusDto?, week: Int?, league: League): Game? {
        val homeDto = comp.competitors.firstOrNull { it.homeAway == "home" && it.team != null } ?: return null
        val awayDto = comp.competitors.firstOrNull { it.homeAway == "away" && it.team != null } ?: return null
        val state = stateOf(status)
        val sit = comp.situation
        val base = Game(
            id = id,
            league = league,
            startEpochMs = parseDate(date),
            state = state,
            home = toSide(homeDto, isHome = true, state, sit?.homeTimeouts),
            away = toSide(awayDto, isHome = false, state, sit?.awayTimeouts),
            period = status?.period ?: 0,
            clock = status?.displayClock.orEmpty(),
            shortDetail = status?.type?.shortDetail ?: status?.type?.detail.orEmpty(),
            broadcast = broadcastOf(comp),
            networks = networksOf(comp),
            situation = sit?.let {
                Situation(
                    detail = it.shortDownDistanceText ?: it.downDistanceText ?: baseballSituation(it),
                    possessionTeamId = it.possession,
                    isRedZone = it.isRedZone == true,
                    lastPlay = it.lastPlay?.text,
                )
            },
            week = week,
        )
        return base.copy(odds = toOdds(comp.odds, base))
    }

    /** "2-1, 1 out · 1st & 3rd" from a live baseball situation; null for other sports. */
    internal fun baseballSituation(s: EspnSituationDto): String? {
        val outs = s.outs ?: return null
        val count = if (s.balls != null && s.strikes != null) "${s.balls}-${s.strikes}, " else ""
        val bases = listOfNotNull("1st".takeIf { s.onFirst == true }, "2nd".takeIf { s.onSecond == true }, "3rd".takeIf { s.onThird == true })
        val runners = when (bases.size) {
            0 -> null
            3 -> "Bases loaded"
            else -> bases.joinToString(" & ")
        }
        return listOfNotNull("$count$outs out${if (outs == 1) "" else "s"}", runners).joinToString(" · ")
    }

    private fun broadcastOf(comp: EspnCompetitionDto): String? =
        comp.broadcast?.takeIf { it.isNotBlank() }
            ?: comp.broadcasts.firstNotNullOfOrNull { it.names.firstOrNull() ?: it.media?.shortName }

    /** The broadcasts list when there is one; else the combined "ESPN/ABC" text, split. */
    private fun networksOf(comp: EspnCompetitionDto): List<String> =
        comp.broadcasts.flatMap { b -> b.names.ifEmpty { listOfNotNull(b.media?.shortName) } }
            .ifEmpty { comp.broadcast?.split('/').orEmpty() }
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    // ---- Fights ----

    /**
     * One UFC card → one [Game] per bout, fighter 1 on top ("away") and fighter 2 below ("home").
     * ESPN lists bouts in running order, so the main event is last and gets the highest [Game.order].
     */
    private fun toFights(event: EspnEventDto, league: League): List<Game> =
        event.competitions.mapIndexedNotNull { i, comp ->
            val fighters = comp.competitors.filter { it.athlete != null }.sortedBy { it.order ?: 0 }
            if (fighters.size != 2) return@mapIndexedNotNull null
            val status = comp.status ?: event.status
            val state = stateOf(status)
            val round = status?.period ?: 0
            val clock = status?.displayClock.orEmpty()
            Game(
                id = comp.id ?: "${event.id}-$i",
                league = league,
                startEpochMs = parseDate(comp.date ?: event.date),
                state = state,
                away = toFighter(fighters[0], isHome = false),
                home = toFighter(fighters[1], isHome = true),
                period = round,
                clock = clock,
                shortDetail = when {
                    state == GameState.FINAL && round > 0 -> "Final · R$round $clock".trim()
                    state == GameState.LIVE && round > 0 -> "R$round $clock".trim()
                    else -> status?.type?.shortDetail ?: status?.type?.detail.orEmpty()
                },
                broadcast = broadcastOf(comp),
                networks = networksOf(comp),
                eventId = event.id,
                eventName = event.name,
                note = comp.type?.abbreviation ?: comp.type?.text,
                order = i,
            )
        }

    /** [EspnCompetitorDto.records], else [EspnCompetitorDto.record] when it's the list form. */
    private fun recordsOf(c: EspnCompetitorDto): List<EspnRecordDto> = c.records.ifEmpty {
        (c.record as? JsonArray)?.let { runCatching { SofaJson.decodeFromJsonElement(recordList, it) }.getOrNull() }.orEmpty()
    }

    private val recordList = kotlinx.serialization.builtins.ListSerializer(EspnRecordDto.serializer())

    private fun toFighter(c: EspnCompetitorDto, isHome: Boolean): TeamSide {
        val a = c.athlete!!
        val name = a.displayName ?: a.fullName ?: "TBA"
        val records = recordsOf(c)
        return TeamSide(
            id = c.id ?: name,
            abbr = a.shortName ?: name,
            name = name,
            shortName = name.substringAfterLast(' '),
            logoUrl = a.headshot?.href ?: a.flag?.href,
            score = null,
            record = records.firstOrNull { it.type == "total" }?.summary ?: records.firstOrNull()?.summary,
            isHome = isHome,
            winner = c.winner,
        )
    }

    // ---- Racing ----

    private val sessionNames = mapOf(
        "FP1" to "Practice 1", "FP2" to "Practice 2", "FP3" to "Practice 3",
        "Qual" to "Qualifying", "SS" to "Sprint Qualifying", "SQ" to "Sprint Qualifying",
        "Sprint" to "Sprint", "Race" to "Race",
    )

    private fun toRace(event: EspnEventDto, league: League): Race? {
        val sessions = event.competitions.mapIndexed { i, c ->
            val abbr = c.type?.abbreviation
            RaceSession(
                id = c.id ?: "${event.id}-$i",
                name = abbr?.let { sessionNames[it] ?: it } ?: "Race",
                startEpochMs = parseDate(c.date ?: event.date),
                state = stateOf(c.status ?: event.status),
                results = c.competitors.filter { it.athlete != null }.sortedBy { it.order ?: Int.MAX_VALUE }.mapIndexed { pos, d ->
                    RaceEntry(
                        position = d.order ?: (pos + 1),
                        driverId = d.id ?: "$pos",
                        name = d.athlete?.displayName ?: d.athlete?.fullName ?: "—",
                        shortName = d.athlete?.shortName ?: d.athlete?.displayName ?: "—",
                        flagUrl = d.athlete?.flag?.href,
                        winner = d.winner == true,
                    )
                },
            )
        }
        if (sessions.isEmpty()) return null
        val idx = currentSession(sessions.map { it.state })
        val comp = event.competitions[idx]
        val session = sessions[idx]
        val status = comp.status ?: event.status
        return Race(
            id = event.id,
            league = league,
            name = event.name ?: event.shortName ?: league.fullName,
            session = session.name,
            startEpochMs = session.startEpochMs,
            state = session.state,
            shortDetail = status?.type?.shortDetail ?: status?.type?.detail.orEmpty(),
            venue = comp.venue?.fullName,
            broadcast = broadcastOf(comp),
            networks = networksOf(comp),
            // Before a session starts ESPN lists the entry list in an arbitrary order; it isn't a result.
            results = if (session.state == GameState.PRE) emptyList() else session.results,
            sessions = sessions,
        )
    }

    /** The live session; else the next one to start; else the last one. */
    internal fun currentSession(states: List<GameState>): Int {
        states.indexOfFirst { it == GameState.LIVE }.takeIf { it >= 0 }?.let { return it }
        states.indexOfFirst { it == GameState.PRE }.takeIf { it >= 0 }?.let { return it }
        return states.lastIndex
    }

    // ---- Odds ----

    /** First line, preferring DraftKings. Spread is reported from the home team's side. */
    internal fun toOdds(lines: List<EspnOddsDto?>, game: Game): GameOdds? {
        val list = lines.filterNotNull()
        val o = list.firstOrNull { it.provider?.name?.contains("Draft", ignoreCase = true) == true } ?: list.firstOrNull() ?: return null
        if (o.details.isNullOrBlank() && o.overUnder == null) return null
        fun ml(side: EspnMoneylineSideDto?, team: EspnTeamOddsDto?): String? =
            side?.current?.odds ?: side?.close?.odds ?: team?.moneyLine?.let { v -> (if (v > 0) "+" else "") + v.toInt() }
        return GameOdds(
            provider = (o.provider?.displayName ?: o.provider?.name ?: "Sportsbook").replace("Draft Kings", "DraftKings"),
            details = o.details,
            overUnder = o.overUnder,
            homeSpread = homeSpread(o.details, game),
            homeMoneyline = ml(o.moneyline?.home, o.homeTeamOdds),
            awayMoneyline = ml(o.moneyline?.away, o.awayTeamOdds),
        )
    }

    /** "PIT -3" with PIT away → home spread +3. "EVEN" → 0. ESPN's numeric `spread` sign varies by endpoint, so parse the text. */
    internal fun homeSpread(details: String?, game: Game): Double? {
        val d = details?.trim() ?: return null
        if (d.equals("EVEN", ignoreCase = true) || d.equals("PK", ignoreCase = true)) return 0.0
        val parts = d.split(" ")
        if (parts.size != 2) return null
        val n = parts[1].toDoubleOrNull() ?: return null
        return when (parts[0]) {
            game.home.abbr -> n
            game.away.abbr -> -n
            else -> null
        }
    }

    private fun toSide(c: EspnCompetitorDto, isHome: Boolean, state: GameState, timeouts: Int?): TeamSide {
        val team = c.team!!
        val records = recordsOf(c)
        return TeamSide(
            id = team.id,
            abbr = team.abbreviation,
            name = team.displayName,
            shortName = team.shortDisplayName.ifEmpty { team.name.orEmpty() },
            logoUrl = team.logo ?: team.logos.firstOrNull()?.href,
            // ESPN reports "0" for games that haven't started; hide it.
            score = if (state == GameState.PRE) null else (c.score as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            record = records.firstOrNull { it.type == "total" }?.summary ?: records.firstOrNull()?.summary,
            isHome = isHome,
            timeouts = timeouts,
            winner = c.winner?.takeIf { state == GameState.FINAL },
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
