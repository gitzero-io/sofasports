package com.sofasports.tv.data.provider

/**
 * Default API locations. Remote config may override the ESPN/Sleeper bases if an endpoint moves,
 * so a URL change doesn't need a new APK.
 */
object Endpoints {
    const val ESPN_NFL_BASE = "https://site.api.espn.com/apis/site/v2/sports/football/nfl"
    const val SLEEPER_BASE = "https://api.sleeper.app/v1"
}

/** Where the live providers point. Implemented by remote config; [Default] is the built-in fallback. */
interface ApiBases {
    val espnNflBase: String
    val sleeperBase: String

    object Default : ApiBases {
        override val espnNflBase = Endpoints.ESPN_NFL_BASE
        override val sleeperBase = Endpoints.SLEEPER_BASE
    }
}
