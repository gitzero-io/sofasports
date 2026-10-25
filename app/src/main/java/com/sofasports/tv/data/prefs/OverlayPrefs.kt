package com.sofasports.tv.data.prefs

/** Pages the overlay can flip through, in display order. */
enum class OverlayPage(val label: String) {
    MATCHUP("My matchup"),
    MY_GAME("My game"),
    LEAGUE("Around the league"),
    PICKS("Picks"),
    MINI("Minimized"),
}

/** Card scale, applied as a density multiplier so every dp and sp grows together. */
enum class OverlaySize(val label: String, val scale: Float) { SMALL("Small", 0.85f), REGULAR("Regular", 1f), LARGE("Large", 1.25f) }

/** Background only; text stays fully opaque so it reads over bright video. */
enum class OverlayOpacity(val label: String, val alpha: Float) { SOLID("Solid", 1f), HIGH("90%", 0.9f), MEDIUM("75%", 0.75f), LOW("60%", 0.6f) }

/** Distance from the screen edge. EXTRA is for TVs that crop the picture (overscan). */
enum class OverlayEdge(val label: String, val factor: Float) { NORMAL("Normal", 1f), EXTRA("Extra", 2f) }

/** Optional rows on the full pages. */
enum class OverlayRow(val label: String) {
    DK_LINE("DraftKings line"),
    DOWN_DISTANCE("Down & distance"),
    LAST_PLAY("Last play"),
    PLAYING_NOW("My players playing now"),
}

/** What counts as a big moment for "pop" (smart mode only). */
enum class MomentKind(val label: String) {
    MY_GAME_SCORE("Score in my game"),
    PLAYER_SCORED("My player scores"),
    LEAD_CHANGE("Fantasy lead change"),
    PICK_RESULT("Pick hits or misses"),
    CLOSE_GAME("Game gets close"),
}

/** Overnight window in which smart mode keeps the overlay out of the way. Hours are local, end exclusive. */
enum class QuietHours(val label: String, val startHour: Int, val endHour: Int) {
    OFF("Off", 0, 0),
    LATE("11 PM – 8 AM", 23, 8),
    OVERNIGHT("1 AM – 9 AM", 1, 9);

    fun contains(hour: Int): Boolean = when {
        this == OFF -> false
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }
}

data class OverlayPrefs(
    val pages: Set<OverlayPage> = OverlayPage.entries.toSet(),
    val size: OverlaySize = OverlaySize.REGULAR,
    val opacity: OverlayOpacity = OverlayOpacity.SOLID,
    val edge: OverlayEdge = OverlayEdge.NORMAL,
    val rows: Set<OverlayRow> = OverlayRow.entries.toSet(),
    /** 0 = off. Rotates through the full pages, never into Minimized. */
    val rotateSec: Int = 0,
    /** Smart automatic mode: the switch for everything the overlay does on its own. */
    val smart: Boolean = false,
    val pop: Boolean = true,
    val popKinds: Set<MomentKind> = MomentKind.entries.toSet(),
    /** 0 = off. Smart mode only. */
    val minimizeSec: Int = 0,
    /** Smart mode only. */
    val liveOnly: Boolean = false,
    /** Smart mode only. */
    val quiet: QuietHours = QuietHours.OFF,
    /** Remote buttons learned through the accessibility service (Android key codes). */
    val toggleKey: Int? = null,
    val nextKey: Int? = null,
) {
    val popActive get() = smart && pop && popKinds.isNotEmpty()

    companion object {
        val ROTATE_CHOICES = listOf(0, 10, 20, 30)
        val MINIMIZE_CHOICES = listOf(0, 30, 60, 120)
    }
}
