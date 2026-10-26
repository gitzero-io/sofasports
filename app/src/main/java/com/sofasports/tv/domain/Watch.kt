package com.sofasports.tv.domain

/**
 * Services that stream some NFL games themselves, so they open directly instead of the user's
 * live TV app. [networks] are ESPN's broadcast names (lowercase) the service carries.
 */
enum class Streamer(val label: String, val pkg: String, val networks: Set<String>) {
    PRIME("Prime Video", "com.amazon.amazonvideo.livingroom", setOf("prime video", "amazon prime video", "prime")),
    PEACOCK("Peacock", "com.peacocktv.peacockandroid", setOf("peacock", "nbc")),
    NETFLIX("Netflix", "com.netflix.ninja", setOf("netflix")),
    YOUTUBE("YouTube", "com.google.android.youtube.tv", setOf("youtube")),
    DISNEY("Disney+", "com.disney.disneyplus", setOf("disney+")),
    PARAMOUNT("Paramount+", "com.cbs.ott", setOf("paramount+", "cbs")),
    NFL_PLUS("NFL+", "com.gotv.nflgamecenter.us.lite", setOf("nfl+")),
}

/** Live TV apps we recognise, listed first in Settings. Any installed app can be chosen. */
object LiveTvApps {
    val known = mapOf(
        "com.google.android.youtube.tvunplugged" to "YouTube TV",
        "com.hulu.livingroomplus" to "Hulu + Live TV",
        "com.sling" to "Sling TV",
    )
}

data class InstalledApp(val pkg: String, val label: String)

/** One way to watch: open [pkg]. */
data class WatchOption(val label: String, val pkg: String)

object WatchPlanner {
    /** Streaming-only names: a game on just these doesn't need a live TV app. */
    private val streamingOnly = setOf("prime video", "amazon prime video", "prime", "peacock", "netflix", "youtube", "disney+", "espn+", "paramount+", "nfl+")

    /**
     * The user's live TV app first when the game is on TV (or we don't know the network), then any
     * installed streamer that carries it. Empty when there's nothing installed to open.
     */
    fun options(networks: List<String>, liveTv: InstalledApp?, installed: Set<String>): List<WatchOption> {
        val names = networks.map { it.lowercase().trim() }
        val onTv = names.isEmpty() || names.any { it !in streamingOnly }
        val streamers = Streamer.entries.filter { s -> s.pkg in installed && names.any { it in s.networks } }
            .map { WatchOption(it.label, it.pkg) }
        val tv = liveTv?.takeIf { onTv && it.pkg in installed }?.let { WatchOption(it.label, it.pkg) }
        // A streaming exclusive (Prime, Netflix) goes first; otherwise the live TV app does.
        val ordered = if (onTv) listOfNotNull(tv) + streamers else streamers + listOfNotNull(tv)
        return ordered.distinctBy { it.pkg }
    }

    /** What Google TV search is asked for: "Houston Texans vs Jacksonville Jaguars". */
    /** Fights search by card ("UFC 333"), since the apps list the event, not the bout. */
    fun searchQuery(game: Game) = game.eventName?.takeIf { game.league.format == EventFormat.FIGHT } ?: "${game.away.name} vs ${game.home.name}"
}
