package com.sofasports.tv.domain

/** One ESPN headline with the teams/players it's tagged with. */
data class NewsItem(
    val id: String,
    val headline: String,
    val publishedMs: Long,
    val teamAbbrs: Set<String> = emptySet(),
    val athleteIds: Set<String> = emptySet(),
    val athleteNames: Set<String> = emptySet(),
    /** ESPN's article type; see [NewsFilter]. */
    val kind: String? = null,
    /** Which league's feed it came from; [teamAbbrs] are that league's abbreviations. */
    val league: League = League.NFL,
) {
    val teamKeys: Set<String> get() = teamAbbrs.mapTo(mutableSetOf()) { TeamKey.of(league, it) }
}

/**
 * The ticker only carries news a bettor or fantasy player would act on: a named player's status
 * (out, questionable, IR, starting, returning, traded…). Feature stories, rankings, betting guides,
 * videos and off-field team news are dropped.
 */
object NewsFilter {
    private val status = Regex(
        "\\b(out|ruled? out|rules out|questionable|doubtful|probable|inactive|limited|game-time|" +
            "injur\\w*|ir|reserve|activat\\w*|return\\w*|debut\\w*|expected to play|will play|won't play|unsure|" +
            "miss(es|ed|ing)?|practice\\w*|to start|will start|starting|starts|benched|bench|demoted|promoted|" +
            "suspen\\w*|trade[sd]?|acquire[sd]?|sign(s|ed)?|releas\\w*|waive[sd]?|cut|carted|elevat\\w*|" +
            "sprain\\w*|ankle|knee|hamstring|groin|calf|concussion|chest|shoulder|foot|hip|illness|surgery)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun isUseful(n: NewsItem): Boolean =
        (n.kind == null || n.kind == "HeadlineNews") &&
            (n.athleteIds.isNotEmpty() || n.athleteNames.isNotEmpty()) &&
            status.containsMatchIn(n.headline)
}

enum class TickerKind(val tag: String) { SCORE_EVENT("SCORE"), LIVE_SCORE("LIVE"), HEADLINE("NEWS") }

data class TickerItem(
    val key: String,
    val kind: TickerKind,
    val text: String,
    /** About my teams, fantasy players or picks: shown first and starred. */
    val mine: Boolean,
    val timeMs: Long,
    val league: League = League.NFL,
)

/**
 * What makes an item "mine". [teams] are [TeamKey]s ("nfl:KC"). Names are matched
 * case-insensitively (Sleeper/ESPN fantasy give names, not ESPN ids).
 */
data class TickerInterests(
    val teams: Set<String> = emptySet(),
    val playerIds: Set<String> = emptySet(),
    val playerNames: Set<String> = emptySet(),
) {
    private val lowerNames = playerNames.map { it.lowercase() }.toSet()
    private val keys = teams.map(TeamKey::normalize).toSet()
    fun matches(n: NewsItem) =
        n.teamKeys.any { it in keys } || n.athleteIds.any { it in playerIds } || n.athleteNames.any { it.lowercase() in lowerNames }
    fun matches(g: Game) = keys.any { g.involves(it) }
}

object TickerBuilder {
    /** The crawl's source tag: the league when everything is from one ("NFL"), else "SPORTS". */
    fun label(items: List<TickerItem>): String = items.map { it.league }.distinct().singleOrNull()?.label ?: "SPORTS"

    const val EVENT_TTL_MS = 15 * 60_000L
    const val MAX_HEADLINES = 15

    /**
     * Scoring between two scoreboard polls: "TD HOU · <last play> · HOU 24–14", "GOAL ARS · ARS 1–0 CHE".
     * Basketball is skipped: a basket every few seconds isn't news.
     */
    fun scoreEvents(previous: List<Game>, current: List<Game>, nowMs: Long): List<TickerItem> {
        val before = previous.associateBy { it.ref }
        return current.mapNotNull { g ->
            val old = before[g.ref] ?: return@mapNotNull null
            if (!SportRules.scoresAreNews(g.league)) return@mapNotNull null
            if (g.state != GameState.LIVE && g.state != GameState.FINAL) return@mapNotNull null
            val scorer = g.teams.firstOrNull { t ->
                val was = old.side(t.abbr)?.score ?: return@firstOrNull false
                (t.score ?: 0) > was
            } ?: return@mapNotNull null
            val delta = (scorer.score ?: 0) - (old.side(scorer.abbr)?.score ?: 0)
            val what = SportRules.scoreLabel(g.league, delta)
            val score = "${g.away.abbr} ${g.away.score ?: 0}–${g.home.score ?: 0} ${g.home.abbr}"
            val play = scoringPlayText(what, g.situation?.lastPlay)
            TickerItem(
                key = "ev-${g.league.id}:${g.id}-${g.away.score}-${g.home.score}",
                kind = TickerKind.SCORE_EVENT,
                text = listOfNotNull(leaguePrefix(g), "$what ${scorer.abbr}", play, score).joinToString(" · "),
                mine = false,
                timeMs = nowMs,
                league = g.league,
            )
        }
    }

    /** NFL points scored between two polls → "TD", "FG", … See [SportRules.scoreLabel] for other sports. */
    fun scoreLabel(delta: Int): String = SportRules.scoreLabel(League.NFL, delta)

    /** NFL items stay as they were; other leagues say which league they're from. */
    private fun leaguePrefix(g: Game): String? = g.league.label.takeIf { g.league != League.NFL }

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
            else -> when {
                label.contains("GOAL") -> "goal" in lower && "disallowed" !in lower
                label.contains("RUN") -> "scored" in lower || "homer" in lower || "home run" in lower
                else -> false
            }
        }
        return if (matches) trimPlay(t) else null
    }

    /** ESPN play text can run long; keep the first sentence. */
    internal fun trimPlay(text: String): String {
        val first = text.substringBefore(". ").trim()
        return if (first.length > 90) first.take(87) + "…" else first
    }

    fun liveScore(g: Game) = TickerItem(
        key = "live-${g.league.id}:${g.id}",
        kind = TickerKind.LIVE_SCORE,
        text = if (g.league.format == EventFormat.TEAMS) {
            listOfNotNull(leaguePrefix(g), "${g.away.abbr} ${g.away.score ?: 0}  ${g.home.abbr} ${g.home.score ?: 0}", g.shortDetail).joinToString(" · ")
        } else {
            listOfNotNull(g.league.label, "${g.away.name} vs ${g.home.name}", g.shortDetail).joinToString(" · ")
        },
        mine = false,
        timeMs = g.startEpochMs,
        league = g.league,
    )

    fun build(
        news: List<NewsItem>,
        games: List<Game>,
        events: List<TickerItem>,
        interests: TickerInterests,
        nowMs: Long,
    ): List<TickerItem> {
        val byRef = games.associateBy { it.ref.key }
        val freshEvents = events.filter { nowMs - it.timeMs < EVENT_TTL_MS }.map { e ->
            val ref = e.key.removePrefix("ev-").substringBefore('-')
            e.copy(mine = byRef[ref]?.let(interests::matches) == true)
        }
        val live = games.filter { it.state == GameState.LIVE }.map { g -> liveScore(g).copy(mine = interests.matches(g)) }
        val headlines = news.filter(NewsFilter::isUseful).sortedByDescending { it.publishedMs }.take(MAX_HEADLINES).map { n ->
            TickerItem("news-${n.league.id}-${n.id}", TickerKind.HEADLINE, n.headline, interests.matches(n), n.publishedMs, n.league)
        }
        val all = freshEvents.sortedByDescending { it.timeMs } + live + headlines
        // Mine first (keeping each group's order), then the rest.
        return all.filter { it.mine } + all.filterNot { it.mine }
    }
}
