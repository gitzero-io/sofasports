package com.sofasports.tv.overlay

import com.sofasports.tv.data.prefs.OverlayPage
import com.sofasports.tv.data.prefs.OverlayPrefs

/**
 * What the overlay is doing right now, driven by the remote (show/hide, flip pages) and, in smart
 * mode, by itself (pop on big moments, minimize, live games only, quiet hours).
 */
data class DisplayState(
    val page: OverlayPage = OverlayPage.MATCHUP,
    /** The user's last show/hide press; null = follow the automatic rules. */
    val userHidden: Boolean? = null,
    /** What the automatic rules said when the user pressed, so the press lasts until the situation changes. */
    val overrideAuto: Boolean? = null,
    val pop: Moment? = null,
    val popUntilMs: Long = 0,
    val queue: List<Moment> = emptyList(),
    /** Recently popped keys, so the same moment never pops twice. */
    val seen: List<String> = emptyList(),
    val lastActionMs: Long = 0,
    val lastRotateMs: Long = 0,
)

/** Everything the rules need that isn't the display state itself. */
data class DisplayContext(
    val prefs: OverlayPrefs,
    /** Enabled pages with content. */
    val available: List<OverlayPage>,
    /** Pages with content, enabled or not (pops may use any). */
    val withContent: List<OverlayPage>,
    val anyLive: Boolean,
    val hour: Int,
    val nowMs: Long,
)

data class RenderedDisplay(
    val visible: Boolean = false,
    /** Null when nothing has content: the card shows a short "how to fill me" hint. */
    val page: OverlayPage? = null,
    val moment: Moment? = null,
    /** Position among the user's pages, for the dots in the header. */
    val index: Int = 0,
    val count: Int = 0,
)

object OverlayDisplayLogic {
    const val POP_MS = 8_000L
    const val QUEUE_MAX = 3
    private const val SEEN_MAX = 50

    private fun quiet(ctx: DisplayContext) = ctx.prefs.smart && ctx.prefs.quiet.contains(ctx.hour)

    /** Smart mode's rules. Without smart mode the overlay is always shown unless the user hides it. */
    fun autoVisible(ctx: DisplayContext): Boolean =
        !ctx.prefs.smart || ((!ctx.prefs.liveOnly || ctx.anyLive) && !quiet(ctx))

    /** A show/hide press holds until the automatic answer changes (e.g. games start or end). */
    fun normalize(st: DisplayState, ctx: DisplayContext): DisplayState =
        if (st.userHidden != null && st.overrideAuto != autoVisible(ctx)) st.copy(userHidden = null, overrideAuto = null) else st

    fun baseVisible(st: DisplayState, ctx: DisplayContext): Boolean = st.userHidden?.not() ?: autoVisible(ctx)

    fun render(st: DisplayState, ctx: DisplayContext): RenderedDisplay {
        val pop = st.pop?.takeIf { it.page in ctx.withContent && !quiet(ctx) }
        val page = pop?.page ?: OverlayPages.resolve(st.page, ctx.available)
        val visible = pop != null || baseVisible(st, ctx)
        val i = ctx.available.indexOf(page)
        return RenderedDisplay(visible, page, pop, if (i < 0) 0 else i, ctx.available.size)
    }

    /** Show/hide: flips what's on screen now. Hiding also clears any pop. */
    fun toggle(st: DisplayState, ctx: DisplayContext): DisplayState {
        val shownNow = render(st, ctx).visible
        return st.copy(
            userHidden = shownNow,
            overrideAuto = autoVisible(ctx),
            pop = null, queue = emptyList(),
            lastActionMs = ctx.nowMs, lastRotateMs = ctx.nowMs,
        )
    }

    /** Flip pages. If the overlay was hidden, the first press just shows it again. */
    fun step(st: DisplayState, ctx: DisplayContext, forward: Boolean): DisplayState {
        val from = OverlayPages.resolve(st.page, ctx.available) ?: st.page
        val shown = st.pop == null && render(st, ctx).visible
        val to = if (shown) OverlayPages.step(from, ctx.available, forward) ?: from else from
        val show = if (baseVisible(st, ctx)) st else st.copy(userHidden = false, overrideAuto = autoVisible(ctx))
        return show.copy(page = to, pop = null, queue = emptyList(), lastActionMs = ctx.nowMs, lastRotateMs = ctx.nowMs)
    }

    /** New big moments are queued only when smart mode's pop is on, and never twice. */
    fun onMoments(st: DisplayState, moments: List<Moment>, ctx: DisplayContext): DisplayState {
        if (!ctx.prefs.popActive || quiet(ctx)) return st
        val fresh = moments.filter { it.kind in ctx.prefs.popKinds && it.key !in st.seen && st.queue.none { q -> q.key == it.key } && st.pop?.key != it.key }
        if (fresh.isEmpty()) return st
        return st.copy(queue = (st.queue + fresh).takeLast(QUEUE_MAX), seen = (st.seen + fresh.map { it.key }).takeLast(SEEN_MAX))
    }

    /** Once a second: end/start pops, auto-rotate, and (smart mode) auto-minimize. */
    fun tick(st0: DisplayState, ctx: DisplayContext): DisplayState {
        var st = normalize(st0, ctx)
        val now = ctx.nowMs
        if (!ctx.prefs.popActive || quiet(ctx)) st = st.copy(pop = null, queue = emptyList())
        if (st.pop != null && now >= st.popUntilMs) {
            st = st.copy(pop = null, lastActionMs = now, lastRotateMs = now)
        }
        if (st.pop == null && st.queue.isNotEmpty()) {
            st = st.copy(pop = st.queue.first(), popUntilMs = now + POP_MS, queue = st.queue.drop(1))
        }
        if (st.pop != null || !baseVisible(st, ctx)) return st
        val current = OverlayPages.resolve(st.page, ctx.available) ?: return st
        val prefs = ctx.prefs
        if (prefs.smart && prefs.minimizeSec > 0 && current != OverlayPage.MINI && OverlayPage.MINI in ctx.available &&
            now - st.lastActionMs >= prefs.minimizeSec * 1000L
        ) {
            return st.copy(page = OverlayPage.MINI)
        }
        if (prefs.rotateSec > 0 && current != OverlayPage.MINI && now - st.lastRotateMs >= prefs.rotateSec * 1000L) {
            val next = OverlayPages.step(current, ctx.available, forward = true, fullOnly = true) ?: current
            return st.copy(page = next, lastRotateMs = now)
        }
        return st
    }
}
