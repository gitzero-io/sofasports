package com.sofasports.tv.data.sportsdb

import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.Scoreboard
import com.sofasports.tv.domain.TeamSide
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.ZoneOffset

@Serializable private data class SportsDbEventsDto(val events: List<SportsDbEventDto>? = null)

@Serializable
private data class SportsDbEventDto(
    val idEvent: String,
    /** "Canelo Álvarez vs Christian Mbilli" */
    val strEvent: String? = null,
    /** UTC, no zone: "2026-10-31T15:00:00" */
    val strTimestamp: String? = null,
    /** "O'Shaquie Foster def. Emanuel Navarrete by unanimous decision (118-108, …), San Antonio, October 24, 2026" */
    val strResult: String? = null,
    /** "FT" once over; empty or "NS" before. */
    val strStatus: String? = null,
    val strVenue: String? = null,
    val strCity: String? = null,
    val strPostponed: String? = null,
)

/**
 * Boxing from TheSportsDB (ESPN has no boxing feed). Fighters are read from the event title
 * ("A vs B"), the winner and method from the result line. Pure: safe to unit test.
 */
object SportsDbParser {
    /** Merges the "next" and "past" lists into one scoreboard; duplicates (same id) keep the newer copy. */
    fun parseBoxing(vararg jsons: String?, nowMs: Long = System.currentTimeMillis()): Scoreboard {
        val events = jsons.filterNotNull().flatMap { json ->
            runCatching { SofaJson.decodeFromString(SportsDbEventsDto.serializer(), json).events.orEmpty() }.getOrDefault(emptyList())
        }
        val games = events.distinctBy { it.idEvent }.mapNotNull { toFight(it, nowMs) }
        return Scoreboard(season = null, week = null, games = games, league = League.BOXING)
    }

    private fun toFight(e: SportsDbEventDto, nowMs: Long): Game? {
        val title = e.strEvent?.trim() ?: return null
        val names = title.split(Regex("\\s+vs\\.?\\s+", RegexOption.IGNORE_CASE))
        if (names.size != 2) return null
        val start = parseUtc(e.strTimestamp)
        val result = e.strResult?.trim()?.takeIf { it.isNotEmpty() }
        val winner = result?.substringBefore(" def. ", "")?.trim()?.takeIf { it.isNotEmpty() }
        val done = result != null || e.strStatus.equals("FT", ignoreCase = true)
        val state = when {
            e.strPostponed.equals("yes", ignoreCase = true) -> GameState.OTHER
            done -> GameState.FINAL
            else -> GameState.PRE
        }
        fun side(name: String, isHome: Boolean) = TeamSide(
            id = "${e.idEvent}-${if (isHome) 2 else 1}",
            abbr = shortName(name),
            name = name,
            shortName = name.substringAfterLast(' '),
            logoUrl = null,
            score = null,
            record = null,
            isHome = isHome,
            winner = winner?.let { w -> sameFighter(w, name) },
        )
        return Game(
            id = e.idEvent,
            league = League.BOXING,
            startEpochMs = start,
            state = state,
            away = side(names[0].trim(), isHome = false),
            home = side(names[1].trim(), isHome = true),
            period = 0,
            clock = "",
            shortDetail = when (state) {
                GameState.FINAL -> "Final"
                GameState.OTHER -> "Postponed"
                else -> ""
            },
            broadcast = null,
            eventId = e.idEvent,
            eventName = title,
            note = result?.let(::method),
        )
    }

    /** "… def. … by unanimous decision (118-108, …), San Antonio, …" → "Unanimous decision (118-108, …)". */
    internal fun method(result: String): String? {
        val by = result.substringAfter(" by ", "").takeIf { it.isNotEmpty() } ?: return null
        // The location and date follow the last closing parenthesis, or the first comma when there is none.
        val end = by.lastIndexOf(')').takeIf { it >= 0 }?.plus(1) ?: by.indexOf(',').takeIf { it >= 0 } ?: by.length
        return by.substring(0, end).trim().replaceFirstChar { it.uppercase() }
    }

    /** "Canelo Álvarez" → "C. Álvarez", matching ESPN's fighter short names. */
    internal fun shortName(name: String): String {
        val parts = name.trim().split(' ').filter { it.isNotEmpty() }
        if (parts.size < 2) return name.trim()
        return "${parts.first().first()}. ${parts.drop(1).joinToString(" ")}"
    }

    private fun sameFighter(a: String, b: String) = a.equals(b, ignoreCase = true) || a.substringAfterLast(' ').equals(b.substringAfterLast(' '), ignoreCase = true)

    private fun parseUtc(ts: String?): Long =
        ts?.let { runCatching { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() } ?: 0L
}
