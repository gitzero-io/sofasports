package com.sofasports.tv.data.espnff

import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyPlayer
import com.sofasports.tv.domain.FantasyStarter
import com.sofasports.tv.domain.FantasyTeam
import java.time.LocalDate

data class EspnFfTeam(val id: Int, val name: String, val abbrev: String?, val ownerIds: List<String>, val record: String? = null)

data class EspnFfLeague(
    val id: String,
    val name: String,
    val season: Int,
    val currentMatchupPeriod: Int,
    val teams: List<EspnFfTeam>,
)

object EspnFantasyParser {

    /** ESPN lineup slot ids → label, in the order a lineup is usually read. 20 = bench, 21 = IR. */
    private val slotOrder = listOf(0 to "QB", 2 to "RB", 3 to "RB/WR", 4 to "WR", 5 to "WR/TE", 6 to "TE", 23 to "FLEX", 7 to "OP", 16 to "D/ST", 17 to "K")
    private val benchSlots = setOf(20, 21)

    /** ESPN pro team id → abbreviation (same ids as the ESPN scoreboard). */
    val proTeams: Map<Int, String> = mapOf(
        1 to "ATL", 2 to "BUF", 3 to "CHI", 4 to "CIN", 5 to "CLE", 6 to "DAL", 7 to "DEN", 8 to "DET",
        9 to "GB", 10 to "TEN", 11 to "IND", 12 to "KC", 13 to "LV", 14 to "LAR", 15 to "MIA", 16 to "MIN",
        17 to "NE", 18 to "NO", 19 to "NYG", 20 to "NYJ", 21 to "PHI", 22 to "ARI", 23 to "PIT", 24 to "LAC",
        25 to "SF", 26 to "SEA", 27 to "TB", 28 to "WSH", 29 to "CAR", 30 to "JAX", 33 to "BAL", 34 to "HOU",
    )

    /** The NFL season a date belongs to (fantasy seasons are named by their September year). */
    fun seasonFor(date: LocalDate): Int = if (date.monthValue >= 3) date.year else date.year - 1

    fun parseLeague(json: String): EspnFfLeague {
        val d = SofaJson.decodeFromString(EspnFfLeagueDto.serializer(), json)
        return EspnFfLeague(
            id = d.id.toString(),
            name = d.settings?.name?.takeIf { it.isNotBlank() } ?: "ESPN league ${d.id}",
            season = d.seasonId,
            currentMatchupPeriod = d.status?.currentMatchupPeriod ?: d.scoringPeriodId,
            teams = d.teams.map { t ->
                EspnFfTeam(
                    id = t.id,
                    name = t.name?.takeIf { it.isNotBlank() }
                        ?: listOfNotNull(t.location, t.nickname).joinToString(" ").ifBlank { "Team ${t.id}" },
                    abbrev = t.abbrev,
                    ownerIds = t.owners,
                    record = t.record?.overall?.let { r -> if (r.ties > 0) "${r.wins}-${r.losses}-${r.ties}" else "${r.wins}-${r.losses}" },
                )
            },
        )
    }

    /** Team owned by the ESPN account whose SWID cookie was given (SWID is the member id in braces). */
    fun teamForSwid(league: EspnFfLeague, swid: String?): EspnFfTeam? {
        val id = swid?.trim()?.uppercase()?.let { if (it.startsWith("{")) it else "{$it}" } ?: return null
        return league.teams.firstOrNull { t -> t.ownerIds.any { it.uppercase() == id } }
    }

    /** This week's projection from a player's stats list; null when ESPN sent none. */
    fun projection(player: EspnFfPlayerDto?, season: Int, period: Int): Double? =
        player?.stats?.firstOrNull { it.statSourceId == 1 && it.scoringPeriodId == period && (season == 0 || it.seasonId == season) }?.appliedTotal

    /** Player id → ESPN's default projection, from the public leaguedefaults feed. */
    fun parseDefaultProjections(json: String, season: Int, period: Int): Map<String, Double> =
        SofaJson.decodeFromString(EspnFfPlayersDto.serializer(), json).players
            .mapNotNull { e -> e.player?.let { p -> projection(p, season, period)?.let { p.id.toString() to it } } }
            .toMap()

    fun injuryTag(status: String?): String? = when (status?.uppercase()) {
        null, "", "ACTIVE", "NORMAL" -> null
        "QUESTIONABLE" -> "Q"
        "DOUBTFUL" -> "D"
        "OUT" -> "O"
        "INJURY_RESERVE" -> "IR"
        "SUSPENSION" -> "SUS"
        else -> status.take(3)
    }

    /** ESPN headshots by athlete id; team defenses have negative ids and use the team logo instead. */
    fun headshot(playerId: Long): String? = if (playerId > 0) "https://a.espncdn.com/i/headshots/nfl/players/full/$playerId.png" else null

    fun buildMatchup(league: EspnFfLeague, boxJson: String, myTeamId: Int, leagueKey: String): FantasyMatchup? {
        val box = SofaJson.decodeFromString(EspnFfLeagueDto.serializer(), boxJson)
        // The period we asked for wins: ESPN's status block reports the *current* period even for past weeks.
        val period = league.currentMatchupPeriod
        val m = box.schedule.firstOrNull { it.matchupPeriodId == period && (it.home?.teamId == myTeamId || it.away?.teamId == myTeamId) }
            ?: return null
        val mine = if (m.home?.teamId == myTeamId) m.home else m.away ?: return null
        val theirs = if (mine === m.home) m.away else m.home
        val teamsById = league.teams.associateBy { it.id }
        fun team(side: EspnFfSideDto): FantasyTeam {
            val starters = side.rosterForCurrentScoringPeriod?.entries.orEmpty()
                .filter { it.lineupSlotId !in benchSlots }
                .sortedBy { e -> slotOrder.indexOfFirst { it.first == e.lineupSlotId }.let { if (it < 0) 99 else it } }
                .map { e ->
                    val p = e.playerPoolEntry?.player
                    val slot = slotOrder.firstOrNull { it.first == e.lineupSlotId }?.second
                    FantasyStarter(
                        player = FantasyPlayer(
                            id = e.playerId.toString(),
                            name = p?.fullName ?: "Player ${e.playerId}",
                            position = slot,
                            team = p?.proTeamId?.let { proTeams[it] },
                            headshotUrl = headshot(e.playerId),
                            injury = injuryTag(p?.injuryStatus),
                        ),
                        points = e.playerPoolEntry?.appliedStatTotal ?: 0.0,
                        projected = projection(p, league.season, period),
                        slot = slot,
                    )
                }
            val live = side.totalPointsLive
            return FantasyTeam(
                rosterId = side.teamId,
                ownerId = teamsById[side.teamId]?.ownerIds?.firstOrNull(),
                teamName = teamsById[side.teamId]?.name ?: "Team ${side.teamId}",
                // totalPoints is 0 until the week is scored; totalPointsLive has the running total.
                points = if (live != null && live > 0.0) live else side.totalPoints ?: starters.sumOf { it.points },
                starters = starters,
                record = teamsById[side.teamId]?.record,
            )
        }
        return FantasyMatchup(
            leagueId = leagueKey,
            leagueName = league.name,
            week = period,
            me = team(mine),
            opponent = theirs?.let(::team),
        )
    }
}
