package com.sofasports.tv.domain

/** One ESPN headline with the teams/players it's tagged with. */
data class NewsItem(
    val id: String,
    val headline: String,
    val publishedMs: Long,
    val teamAbbrs: Set<String> = emptySet(),
    val athleteIds: Set<String> = emptySet(),
    val athleteNames: Set<String> = emptySet(),
)

enum class TickerKind(val tag: String) { SCORE_EVENT("SCORE"), LIVE_SCORE("LIVE"), HEADLINE("NEWS") }

data class TickerItem(
    val key: String,
    val kind: TickerKind,
    val text: String,
    /** About my teams, fantasy players or picks: shown first and starred. */
    val mine: Boolean,
    val timeMs: Long,
)

/** What makes an item "mine". Names are matched case-insensitively (Sleeper/ESPN fantasy give names, not ESPN ids). */
data class TickerInterests(
    val teams: Set<String> = emptySet(),
    val playerIds: Set<String> = emptySet(),
    val playerNames: Set<String> = emptySet(),
) {
    private val lowerNames = playerNames.map { it.lowercase() }.toSet()
    fun matches(n: NewsItem) =
        n.teamAbbrs.any { it in teams } || n.athleteIds.any { it in playerIds } || n.athleteNames.any { it.lowercase() in lowerNames }
    fun matches(g: Game) = g.teams.any { it.abbr in teams }
}

object TickerBuilder {
    const val EVENT_TTL_MS = 15 * 60_000L
    const val MAX_HEADLINES = 15

    /** Scoring between two scoreboard polls: "TD HOU · <last play> · HOU 24–14". */
    fun scoreEvents(previous: List<Game>, current: List<Game>, nowMs: Long): List<TickerItem> {
        val before = previous.associateBy { it.id }
        return current.mapNotNull { g ->
            val old = before[g.id] ?: return@mapNotNull null
            if (g.state != GameState.LIVE && g.state != GameState.FINAL) return@mapNotNull null
            val scorer = g.teams.firstOrNull { t ->
                val was = old.side(t.abbr)?.score ?: return@firstOrNull false
                (t.score ?: 0) > was
            } ?: return@mapNotNull null
            val delta = (scorer.score ?: 0) - (old.side(scorer.abbr)?.score ?: 0)
            val what = scoreLabel(delta)
            val score = "${g.away.abbr} ${g.away.score ?: 0}–${g.home.score ?: 0} ${g.home.abbr}"
            val play = scoringPlayText(what, g.situation?.lastPlay)
            TickerItem(
                key = "ev-${g.id}-${g.away.score}-${g.home.score}",
                kind = TickerKind.SCORE_EVENT,
                text = listOfNotNull("$what ${scorer.abbr}", play, score).joinToString(" · "),
                mine = false,
                timeMs = nowMs,
            )
        }
    }

    /** Points scored between two polls → "TD", "FG", … */
    fun scoreLabel(delta: Int): String = when (delta) {
        6, 7, 8 -> "TD"
        3 -> "FG"
        2 -> "2 PTS"
        1 -> "XP"
        else -> "+$delta"
    }

    /**
     * The scoreboard's "last play" is often already the kickoff or the other team's next snap by the
     * time we poll, so it's only shown when it reads like the score that just happened.
     */
    internal fun scoringPlayText(label: String, text: String?): String? {
        val t = text?.takeIf { it.isNotBlank() } ?: return null
        val lower = t.lowercase()
        val good = "no good" !in lower
        val matches = when (label) {
            "TD" -> "touchdown" in lower
            "FG" -> "field goal" in lower && good
            "XP" -> "extra point" in lower && good
            "2 PTS" -> "safety" in lower || ("two-point" in lower && "succeeds" in lower)
            else -> false
        }
        return if (matches) trimPlay(t) else null
    }

    /** ESPN play text can run long; keep the first sentence. */
    internal fun trimPlay(text: String): String {
        val first = text.substringBefore(". ").trim()
        return if (first.length > 90) first.take(87) + "…" else first
    }

    fun liveScore(g: Game) = TickerItem(
        key = "live-${g.id}",
        kind = TickerKind.LIVE_SCORE,
        text = "${g.away.abbr} ${g.away.score ?: 0}  ${g.home.abbr} ${g.home.score ?: 0} · ${g.shortDetail}",
        mine = false,
        timeMs = g.startEpochMs,
    )

    fun build(
        news: List<NewsItem>,
        games: List<Game>,
        events: List<TickerItem>,
        interests: TickerInterests,
        nowMs: Long,
    ): List<TickerItem> {
        val byId = games.associateBy { it.id }
        val freshEvents = events.filter { nowMs - it.timeMs < EVENT_TTL_MS }.map { e ->
            val gameId = e.key.removePrefix("ev-").substringBefore('-')
            e.copy(mine = byId[gameId]?.let(interests::matches) == true)
        }
        val live = games.filter { it.state == GameState.LIVE }.map { g -> liveScore(g).copy(mine = interests.matches(g)) }
        val headlines = news.sortedByDescending { it.publishedMs }.take(MAX_HEADLINES).map { n ->
            TickerItem("news-${n.id}", TickerKind.HEADLINE, n.headline, interests.matches(n), n.publishedMs)
        }
        val all = freshEvents.sortedByDescending { it.timeMs } + live + headlines
        // Mine first (keeping each group's order), then the rest.
        return all.filter { it.mine } + all.filterNot { it.mine }
    }
}
