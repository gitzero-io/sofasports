package com.sofasports.tv.data.provider

import com.sofasports.tv.domain.League

/**
 * Default API locations. Remote config may override the ESPN/Sleeper bases if an endpoint moves,
 * so a URL change doesn't need a new APK.
 */
object Endpoints {
    const val ESPN_NFL_BASE = "https://site.api.espn.com/apis/site/v2/sports/football/nfl"
    /** Every other ESPN league is "<this>/<League.espnPath>". */
    const val ESPN_SPORTS_BASE = "https://site.api.espn.com/apis/site/v2/sports"
    /** Boxing. "123" is TheSportsDB's public test key: the next and the last event per league. */
    const val SPORTS_DB_BASE = "https://www.thesportsdb.com/api/v1/json/123"
    const val SLEEPER_BASE = "https://api.sleeper.app/v1"
    const val ESPN_FANTASY_BASE = "https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl"
}

/** Where the live providers point. Implemented by remote config; [Default] is the built-in fallback. */
interface ApiBases {
    val espnNflBase: String
    val sleeperBase: String
    val espnFantasyBase: String
    val espnSportsBase: String
    val sportsDbBase: String

    /** ESPN site-API base for [league]. NFL keeps its own override so older remote configs still apply. */
    fun espnBase(league: League): String =
        if (league == League.NFL) espnNflBase else "$espnSportsBase/${league.espnPath ?: error("${league.id} isn't on ESPN")}"

    object Default : ApiBases {
        override val espnNflBase = Endpoints.ESPN_NFL_BASE
        override val sleeperBase = Endpoints.SLEEPER_BASE
        override val espnFantasyBase = Endpoints.ESPN_FANTASY_BASE
        override val espnSportsBase = Endpoints.ESPN_SPORTS_BASE
        override val sportsDbBase = Endpoints.SPORTS_DB_BASE
    }
}
