package com.sofasports.tv.data.fixture

import com.sofasports.tv.data.provider.FantasyProvider
import com.sofasports.tv.data.provider.GameStatsProvider
import com.sofasports.tv.data.provider.ScoresProvider
import com.sofasports.tv.data.sleeper.SleeperParser
import com.sofasports.tv.domain.FantasyLeague
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.FantasyStarter
import com.sofasports.tv.domain.FantasyTeam
import com.sofasports.tv.domain.FantasyUser
import com.sofasports.tv.domain.GameSummary
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.Scoreboard
import javax.inject.Inject
import javax.inject.Singleton

class FixtureUnavailableException(what: String) : IllegalStateException("$what is not in the fixture set")

@Singleton
class FixtureScoresProvider @Inject constructor(private val replay: FixtureReplay) : ScoresProvider {
    override suspend fun scoreboard(league: League): Scoreboard =
        replay.scoreboard(league) ?: throw FixtureUnavailableException("${league.label} scores")
}

@Singleton
class FixtureGameStatsProvider @Inject constructor(
    private val replay: FixtureReplay,
) : GameStatsProvider {
    override suspend fun summary(league: League, gameId: String): GameSummary =
        replay.summary(gameId) ?: throw FixtureUnavailableException("Game detail for this game")
}

/**
 * Sleeper fixture: any username "logs in" as the owner of roster 1 in Sleeper's example league.
 * A second league is synthesised (same data, a different team) so the league switcher can be tested.
 * Points scale with replay progress so the overlay's score-change pulse fires.
 */
@Singleton
class FixtureFantasyProvider @Inject constructor(
    private val source: FixtureSource,
    private val replay: FixtureReplay,
) : FantasyProvider {

    private val league by lazy { source.read("sleeper/league.json")?.let(SleeperParser::parseLeague) }

    override suspend fun findUser(username: String): FantasyUser? =
        FantasyUser(FIXTURE_USER_ID, username.ifBlank { "fixture" }, null)

    override suspend fun leagues(userId: String): List<FantasyLeague> {
        val l = league ?: return emptyList()
        return listOf(l, l.copy(id = SECOND_LEAGUE_ID, name = "${l.name} (2nd team)"))
    }

    override suspend fun currentMatchup(leagueId: String, userId: String): FantasyMatchup? {
        val l = league ?: throw FixtureUnavailableException("Sleeper league")
        val asUser = if (leagueId == SECOND_LEAGUE_ID) SECOND_LEAGUE_USER_ID else FIXTURE_USER_ID
        val m = SleeperParser.buildMatchup(
            league = if (leagueId == SECOND_LEAGUE_ID) l.copy(id = SECOND_LEAGUE_ID, name = "${l.name} (2nd team)") else l,
            week = 1,
            myUserId = asUser,
            users = SleeperParser.parseLeagueUsers(source.read("sleeper/league_users.json").orEmpty()),
            rosters = SleeperParser.parseRosters(source.read("sleeper/league_rosters.json").orEmpty()),
            matchups = SleeperParser.parseMatchups(source.read("sleeper/league_matchups_w1.json").orEmpty()),
            players = SleeperParser.parsePlayers(source.read("sleeper/players_nfl_subset.json") ?: "{}"),
        ) ?: return null
        return FixtureFantasy.replayed(m, replay.progress())
    }

    companion object {
        const val FIXTURE_USER_ID = "189140835533586432"
        const val SECOND_LEAGUE_ID = "fixture-second-league"
        /** Owner of roster 5 in the example league. */
        const val SECOND_LEAGUE_USER_ID = "720638189125632"
    }
}

/**
 * Fixture leagues are from old seasons: their players aren't in the replayed games and the files
 * carry no projections. So, for fixtures only, each starter is placed on one of the replayed teams
 * and given a projection near his final score, and points grow with replay progress.
 */
object FixtureFantasy {
    private val replayTeams = listOf("HOU", "JAX", "LAC", "KC")

    fun replayed(m: FantasyMatchup, progress: Double): FantasyMatchup {
        fun FantasyTeam.scaled(): FantasyTeam {
            val s = starters.map { st ->
                val h = (st.player.id.hashCode() and 0x7fffffff)
                val team = if (st.player.position == "DEF" || st.player.position == "D/ST") st.player.team else replayTeams[h % replayTeams.size]
                st.copy(
                    player = st.player.copy(team = team),
                    points = Math.round(st.points * progress * 100) / 100.0,
                    projected = Math.round(st.points * (0.75 + (h % 50) / 100.0) * 10) / 10.0,
                )
            }
            return copy(points = Math.round(points * progress * 100) / 100.0, starters = s)
        }
        return m.copy(me = m.me.scaled(), opponent = m.opponent?.scaled())
    }
}
