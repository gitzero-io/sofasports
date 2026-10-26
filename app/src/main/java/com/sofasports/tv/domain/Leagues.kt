package com.sofasports.tv.domain

/** How a sport's events are shaped, which decides the card, detail screen and rules used. */
enum class EventFormat {
    /** Two teams with a score (football, basketball, baseball, hockey, soccer). */
    TEAMS,
    /** Two athletes, a winner and no score; several bouts make one card (UFC, boxing). */
    FIGHT,
    /** A field of drivers finishing in order (F1, NASCAR, IndyCar). */
    RACE,
}

enum class Sport(val label: String, val format: EventFormat) {
    FOOTBALL("Football", EventFormat.TEAMS),
    BASKETBALL("Basketball", EventFormat.TEAMS),
    BASEBALL("Baseball", EventFormat.TEAMS),
    HOCKEY("Hockey", EventFormat.TEAMS),
    SOCCER("Soccer", EventFormat.TEAMS),
    MMA("MMA", EventFormat.FIGHT),
    BOXING("Boxing", EventFormat.FIGHT),
    RACING("Racing", EventFormat.RACE),
}

/** Where a league's data comes from. */
enum class DataSource { ESPN, THE_SPORTS_DB }

/**
 * Every league the app can show. [id] is stored in settings and favorites ("nba:LAL"), so it must
 * never change. [espnPath] is "<sport>/<league>" under ESPN's site API.
 */
enum class League(
    val id: String,
    val label: String,
    val fullName: String,
    val sport: Sport,
    val espnPath: String?,
    /** Periods in regulation (quarters, halves, periods, innings); 0 when the sport has none. */
    val regulationPeriods: Int,
    val source: DataSource = DataSource.ESPN,
    /** ESPN numbers this league's season in weeks (football). */
    val weekly: Boolean = false,
    /**
     * Extra scoreboard query. College defaults to a "featured" handful of games; asking for the whole
     * division means a favorite outside the Top 25 still shows up.
     */
    val scoreboardQuery: String = "",
) {
    NFL("nfl", "NFL", "NFL", Sport.FOOTBALL, "football/nfl", 4, weekly = true),
    CFB("cfb", "NCAAF", "College Football", Sport.FOOTBALL, "football/college-football", 4, weekly = true, scoreboardQuery = "groups=80&limit=300"),
    NBA("nba", "NBA", "NBA", Sport.BASKETBALL, "basketball/nba", 4),
    CBB("cbb", "NCAAM", "Men's College Basketball", Sport.BASKETBALL, "basketball/mens-college-basketball", 2, scoreboardQuery = "groups=50&limit=400"),
    MLB("mlb", "MLB", "MLB", Sport.BASEBALL, "baseball/mlb", 9),
    NHL("nhl", "NHL", "NHL", Sport.HOCKEY, "hockey/nhl", 3),
    EPL("epl", "EPL", "Premier League", Sport.SOCCER, "soccer/eng.1", 2),
    MLS("mls", "MLS", "MLS", Sport.SOCCER, "soccer/usa.1", 2),
    UCL("ucl", "UCL", "Champions League", Sport.SOCCER, "soccer/uefa.champions", 2),
    LALIGA("laliga", "LaLiga", "LaLiga", Sport.SOCCER, "soccer/esp.1", 2),
    SERIEA("seriea", "Serie A", "Serie A", Sport.SOCCER, "soccer/ita.1", 2),
    BUNDESLIGA("bundesliga", "Bundesliga", "Bundesliga", Sport.SOCCER, "soccer/ger.1", 2),
    LIGUE1("ligue1", "Ligue 1", "Ligue 1", Sport.SOCCER, "soccer/fra.1", 2),
    LIGAMX("ligamx", "Liga MX", "Liga MX", Sport.SOCCER, "soccer/mex.1", 2),
    UFC("ufc", "UFC", "UFC", Sport.MMA, "mma/ufc", 0),
    BOXING("boxing", "Boxing", "Boxing", Sport.BOXING, null, 0, source = DataSource.THE_SPORTS_DB),
    F1("f1", "F1", "Formula 1", Sport.RACING, "racing/f1", 0),
    NASCAR("nascar", "NASCAR", "NASCAR Cup Series", Sport.RACING, "racing/nascar-premier", 0),
    INDYCAR("indycar", "IndyCar", "IndyCar", Sport.RACING, "racing/irl", 0),
    ;

    val format: EventFormat get() = sport.format

    /** Team sports have teams to follow; fights and races don't (yet). */
    val hasTeams: Boolean get() = format == EventFormat.TEAMS

    /**
     * Team logo by abbreviation, where ESPN's CDN names files that way (pro leagues). College and
     * soccer logos are by team id, so callers get null and show the abbreviation alone.
     */
    fun logoUrl(abbr: String): String? = when (this) {
        NFL, NBA, MLB, NHL -> "https://a.espncdn.com/i/teamlogos/$id/500/${abbr.lowercase()}.png"
        else -> null
    }

    /** Whether the game summary (box score, plays) endpoint is worth opening. */
    val hasGameCenter: Boolean get() = format == EventFormat.TEAMS && source == DataSource.ESPN

    companion object {
        private val byId = entries.associateBy { it.id }
        fun fromId(id: String?): League? = id?.let { byId[it.lowercase()] }

        /** What a new install follows until the user picks; also what upgrades from NFL-only builds keep. */
        val DEFAULT_FOLLOWED: List<League> = listOf(NFL)

        /** Picker order: grouped by sport. */
        val bySport: List<Pair<Sport, List<League>>> = entries.groupBy { it.sport }.toList()
    }
}

/**
 * A team the user follows: "nba:LAL". Keys without a league are from NFL-only builds and mean NFL,
 * so old favorites keep working after the upgrade.
 */
object TeamKey {
    fun of(league: League, abbr: String) = "${league.id}:${abbr.uppercase()}"

    fun league(key: String): League? =
        if (':' in key) League.fromId(key.substringBefore(':')) else League.NFL

    fun abbr(key: String): String = key.substringAfter(':')

    /** Old bare "KC" → "nfl:KC"; already-qualified keys are kept as they are. */
    fun normalize(key: String): String = if (':' in key) key.lowercase().substringBefore(':') + ":" + abbr(key).uppercase() else of(League.NFL, key)
}

/**
 * Identifies one game across leagues: "nba:401909861". ESPN ids are only unique within a sport,
 * so pins and navigation always carry the league. A bare id (pinned by an NFL-only build) is NFL.
 */
data class GameRef(val league: League, val id: String) {
    val key: String get() = "${league.id}:$id"

    companion object {
        fun parse(key: String?): GameRef? {
            val k = key?.takeIf { it.isNotBlank() } ?: return null
            if (':' !in k) return GameRef(League.NFL, k)
            val league = League.fromId(k.substringBefore(':')) ?: return null
            return GameRef(league, k.substringAfter(':').takeIf { it.isNotBlank() } ?: return null)
        }
    }
}

/** A team in the favorites picker. [group] is the division or conference when known. */
data class TeamInfo(
    val league: League,
    val abbr: String,
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    val group: String? = null,
) {
    val key: String get() = TeamKey.of(league, abbr)
}

/** Favorite teams across leagues: [TeamKey]s in pick order, which is also their priority. */
object Favorites {
    /** Per league, so "favorites first" stays meaningful on each league's scoreboard. */
    const val MAX_PER_LEAGUE = 4
    /** Across all leagues, so the overlay and Home stay focused. */
    const val MAX_TOTAL = 12

    /** Toggle [key] in [current], keeping pick order and both caps. */
    fun toggle(current: List<String>, key: String): List<String> {
        val k = TeamKey.normalize(key)
        if (k in current) return current - k
        val league = TeamKey.league(k)
        if (current.size >= MAX_TOTAL) return current
        if (current.count { TeamKey.league(it) == league } >= MAX_PER_LEAGUE) return current
        return current + k
    }

    fun inLeague(current: List<String>, league: League): List<String> = current.filter { TeamKey.league(it) == league }
}
