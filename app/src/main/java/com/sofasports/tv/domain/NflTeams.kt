package com.sofasports.tv.domain

data class NflTeam(val abbr: String, val city: String, val name: String, val division: String) {
    val logoUrl: String get() = "https://a.espncdn.com/i/teamlogos/nfl/500/${abbr.lowercase()}.png"
}

/** All 32 teams, ESPN abbreviations, grouped by division (picker order). */
object NflTeams {
    val all: List<NflTeam> = listOf(
        NflTeam("BUF", "Buffalo", "Bills", "AFC East"),
        NflTeam("MIA", "Miami", "Dolphins", "AFC East"),
        NflTeam("NE", "New England", "Patriots", "AFC East"),
        NflTeam("NYJ", "New York", "Jets", "AFC East"),
        NflTeam("BAL", "Baltimore", "Ravens", "AFC North"),
        NflTeam("CIN", "Cincinnati", "Bengals", "AFC North"),
        NflTeam("CLE", "Cleveland", "Browns", "AFC North"),
        NflTeam("PIT", "Pittsburgh", "Steelers", "AFC North"),
        NflTeam("HOU", "Houston", "Texans", "AFC South"),
        NflTeam("IND", "Indianapolis", "Colts", "AFC South"),
        NflTeam("JAX", "Jacksonville", "Jaguars", "AFC South"),
        NflTeam("TEN", "Tennessee", "Titans", "AFC South"),
        NflTeam("DEN", "Denver", "Broncos", "AFC West"),
        NflTeam("KC", "Kansas City", "Chiefs", "AFC West"),
        NflTeam("LV", "Las Vegas", "Raiders", "AFC West"),
        NflTeam("LAC", "Los Angeles", "Chargers", "AFC West"),
        NflTeam("DAL", "Dallas", "Cowboys", "NFC East"),
        NflTeam("NYG", "New York", "Giants", "NFC East"),
        NflTeam("PHI", "Philadelphia", "Eagles", "NFC East"),
        NflTeam("WSH", "Washington", "Commanders", "NFC East"),
        NflTeam("CHI", "Chicago", "Bears", "NFC North"),
        NflTeam("DET", "Detroit", "Lions", "NFC North"),
        NflTeam("GB", "Green Bay", "Packers", "NFC North"),
        NflTeam("MIN", "Minnesota", "Vikings", "NFC North"),
        NflTeam("ATL", "Atlanta", "Falcons", "NFC South"),
        NflTeam("CAR", "Carolina", "Panthers", "NFC South"),
        NflTeam("NO", "New Orleans", "Saints", "NFC South"),
        NflTeam("TB", "Tampa Bay", "Buccaneers", "NFC South"),
        NflTeam("ARI", "Arizona", "Cardinals", "NFC West"),
        NflTeam("LAR", "Los Angeles", "Rams", "NFC West"),
        NflTeam("SF", "San Francisco", "49ers", "NFC West"),
        NflTeam("SEA", "Seattle", "Seahawks", "NFC West"),
    )

    private val byAbbr = all.associateBy { it.abbr }
    fun find(abbr: String): NflTeam? = byAbbr[abbr]
    val divisions: List<Pair<String, List<NflTeam>>> = all.groupBy { it.division }.toList()

    /** Max favorites: keeps "favorites first" meaningful and the overlay focused. */
    const val MAX_FAVORITES = 4

    /** Toggle [abbr] in [current], keeping pick order and the cap. */
    fun toggle(current: List<String>, abbr: String): List<String> = when {
        abbr in current -> current - abbr
        current.size >= MAX_FAVORITES -> current
        byAbbr.containsKey(abbr) -> current + abbr
        else -> current
    }
}
