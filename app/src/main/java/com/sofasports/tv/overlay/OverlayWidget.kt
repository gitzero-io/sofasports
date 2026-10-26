package com.sofasports.tv.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.sofasports.tv.data.prefs.OverlayPage
import com.sofasports.tv.data.prefs.OverlayRow
import com.sofasports.tv.domain.FantasyStarter
import com.sofasports.tv.domain.TickerBuilder
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sofasports.tv.domain.FantasyProjection
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.domain.BetStatus
import com.sofasports.tv.domain.Betting
import com.sofasports.tv.domain.CloseGame
import com.sofasports.tv.domain.Countdown
import com.sofasports.tv.domain.EventFormat
import com.sofasports.tv.domain.League
import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.Game
import com.sofasports.tv.domain.GameState
import com.sofasports.tv.domain.Perspective
import com.sofasports.tv.domain.PickEvaluator
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.domain.PickType
import com.sofasports.tv.domain.Race
import com.sofasports.tv.ui.picks.bookColor
import com.sofasports.tv.ui.picks.sortForDisplay
import com.sofasports.tv.ui.picks.statusColor
import com.sofasports.tv.ui.components.Badge
import com.sofasports.tv.ui.components.TickerBar
import com.sofasports.tv.ui.components.TickerCrawl
import com.sofasports.tv.ui.components.rememberNowMs
import com.sofasports.tv.ui.components.scorePulse
import com.sofasports.tv.ui.fantasy.formatPoints
import com.sofasports.tv.ui.theme.SofaColors

/** The overlay card: one page at a time (or the minimized pill), scaled and tinted per the user's overlay settings. */
@Composable
fun OverlayWidget(state: OverlayState, display: RenderedDisplay) {
    if (!display.visible) return
    val prefs = state.prefs
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * prefs.size.scale, base.fontScale)) {
        val bg = SofaColors.Background.copy(alpha = prefs.opacity.alpha)
        if (display.page == OverlayPage.MINI && display.moment == null) {
            MiniPill(state, bg)
            return@CompositionLocalProvider
        }
        Column(
            Modifier
                .widthIn(min = 260.dp, max = 360.dp)
                .background(bg, RoundedCornerShape(12.dp))
                .border(1.dp, SofaColors.Outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            display.moment?.let { MomentBanner(it) }
            when (display.page) {
                null -> {
                    Text("SOFA SPORTS", style = MaterialTheme.typography.labelLarge, color = SofaColors.TextSecondary)
                    Text("Pin a game or link a fantasy league", style = MaterialTheme.typography.bodySmall, color = SofaColors.TextMuted)
                    return@Column
                }
                OverlayPage.MATCHUP -> state.matchup?.let { m ->
                    PageHeader(m.leagueName, display)
                    MatchupRows(m, state.games)
                    if (OverlayRow.PLAYING_NOW in prefs.rows) PlayingNowRows(OverlayPages.playingNow(state))
                }
                OverlayPage.MY_GAME -> {
                    state.game?.let { g ->
                        PageHeader(if (state.gamePinned) "Pinned game" else "My game", display)
                        GameRows(g, state)
                    }
                    if (state.game == null) state.race?.let { r ->
                        PageHeader(if (state.gamePinned) "Pinned race" else "My race", display)
                        RaceRows(r, state.favorites)
                    }
                }
                OverlayPage.LEAGUE -> {
                    PageHeader("Around the league", display)
                    LeagueRows(OverlayPages.leagueGames(state, System.currentTimeMillis()), state)
                }
                OverlayPage.PICKS -> {
                    PageHeader("Picks", display)
                    PicksRows(state.picks)
                }
                // A pop over the minimized pill shows the moment's own page; this is just the fallback.
                OverlayPage.MINI -> Text(miniText(state), style = MaterialTheme.typography.labelLarge, color = SofaColors.TextPrimary, maxLines = 1)
            }
            if (state.cornerTicker) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(SofaColors.Outline))
                TickerCrawl(state.ticker, textStyle = MaterialTheme.typography.labelLarge, velocity = 45.dp)
            }
        }
    }
}

/** Page title on the left, one dot per page on the right (the current one bright). */
@Composable
private fun PageHeader(title: String, display: RenderedDisplay) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (display.count > 1 && display.moment == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(display.count) { i ->
                    Box(Modifier.size(6.dp).background(if (i == display.index) SofaColors.TextPrimary else SofaColors.Outline, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun MomentBanner(m: Moment) {
    val color = when (m.tone) {
        MomentTone.MINE -> SofaColors.Blue
        MomentTone.OPPONENT -> SofaColors.Red
        MomentTone.NEUTRAL -> SofaColors.Close
    }
    Text(
        m.text,
        style = MaterialTheme.typography.titleSmall,
        color = if (m.tone == MomentTone.NEUTRAL) SofaColors.Background else SofaColors.TextPrimary,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().background(color, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** One line: my matchup, my game and picks, whichever exist. */
internal fun miniText(s: OverlayState): String = listOfNotNull(
    s.matchup?.let { m -> "★ " + formatPoints(m.me.points) + (m.opponent?.let { "–" + formatPoints(it.points) } ?: "") },
    s.game?.let { g -> "${g.away.abbr} ${g.away.score ?: 0}–${g.home.score ?: 0} ${g.home.abbr}" + if (g.state == GameState.LIVE) " · ${g.shortDetail}" else "" },
    s.race?.takeIf { s.game == null }?.let { r -> raceMini(r, s.favorites) },
    s.picks.takeIf { it.isNotEmpty() }?.let { "Picks ${PickEvaluator.hitting(it)}/${it.size}" },
).joinToString("  ·  ").ifEmpty { "SOFA SPORTS" }

@Composable
private fun MiniPill(state: OverlayState, bg: Color) {
    val m = state.matchup
    val leading = m?.opponent?.let { m.me.points >= it.points }
    Row(
        Modifier
            .background(bg, RoundedCornerShape(50))
            .border(1.dp, SofaColors.Outline, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Blue dot when I'm winning my matchup, red when I'm behind.
        if (leading != null) Box(Modifier.size(8.dp).background(if (leading) SofaColors.Blue else SofaColors.Red, CircleShape))
        Text(miniText(state), style = MaterialTheme.typography.labelLarge, color = SofaColors.TextPrimary, maxLines = 1)
    }
}

@Composable
private fun MatchupRows(m: FantasyMatchup, games: List<Game>) {
    OverlayScoreRow(m.me.teamName, formatPoints(m.me.points), SofaColors.Blue, m.opponent != null && m.me.points >= m.opponent.points, m.me.points, onlyIncrease = false)
    if (m.opponent != null) {
        OverlayScoreRow(m.opponent.teamName, formatPoints(m.opponent.points), SofaColors.Red, m.opponent.points > m.me.points, m.opponent.points, onlyIncrease = false)
    }
    // "Proj 118.4–105.2" and a thin win-probability bar, when the site gives projections.
    val live = remember(m, games) { FantasyProjection.live(m, games) }
    val mine = live.me.projected
    val theirs = live.opponent?.projected
    val win = live.winProbability
    if (mine != null && theirs != null && win != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Proj ${String.format(java.util.Locale.US, "%.1f–%.1f", mine, theirs)}",
                style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, modifier = Modifier.weight(1f),
            )
            Text("${Math.round(win * 100)}% win", style = MaterialTheme.typography.labelLarge, color = if (win >= 0.5) SofaColors.BlueBright else SofaColors.RedBright)
        }
        Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
            Box(Modifier.weight(win.toFloat().coerceIn(0.01f, 0.99f)).fillMaxHeight().background(SofaColors.Blue))
            Box(Modifier.weight((1 - win).toFloat().coerceIn(0.01f, 0.99f)).fillMaxHeight().background(SofaColors.Red))
        }
    }
}

@Composable
private fun PlayingNowRows(players: List<FantasyStarter>) {
    if (players.isEmpty()) return
    Text("PLAYING NOW", style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted)
    players.forEach { st ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(st.player.name, st.player.position).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                formatPoints(st.points), style = MaterialTheme.typography.labelLarge, color = SofaColors.TextPrimary,
                modifier = Modifier.padding(start = 6.dp).scorePulse(st.points, SofaColors.Blue, onlyIncrease = false).padding(horizontal = 4.dp),
            )
        }
    }
}

/** Compact scoreboard: "HOU 24  21 JAX   Q4 2:13", favorites' side in blue; other leagues are labelled. */
@Composable
private fun LeagueRows(games: List<Game>, state: OverlayState) {
    games.forEach { g ->
        val mine = state.favorites.any { g.involves(it) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(4.dp).height(16.dp).background(if (mine) SofaColors.Blue else SofaColors.Outline, RoundedCornerShape(2.dp)))
            val score = (if (g.league == League.NFL) "" else "${g.league.label}  ") +
                if (g.state == GameState.PRE) "${g.away.abbr} @ ${g.home.abbr}" else "${g.away.abbr} ${g.away.score ?: 0}  ${g.home.score ?: 0} ${g.home.abbr}"
            Text(score, style = MaterialTheme.typography.labelLarge, color = if (mine) SofaColors.BlueBright else SofaColors.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f).scorePulse(g.teams.sumOf { it.score ?: 0 }.toDouble(), if (mine) SofaColors.Blue else SofaColors.TextSecondary))
            val detail = if (g.state == GameState.PRE) {
                Countdown.format(System.currentTimeMillis(), g.startEpochMs) ?: g.shortDetail
            } else g.shortDetail
            Text(detail, style = MaterialTheme.typography.labelSmall, color = SofaColors.TextSecondary, maxLines = 1)
            if (CloseGame.isClose(g, state.closeRule)) Badge("CLOSE", SofaColors.Close, outlined = true)
        }
    }
}

@Composable
private fun GameRows(g: Game, state: OverlayState) {
    val me = Perspective.mySide(g, state.favorites)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (g.state) {
            GameState.LIVE -> {
                Badge("LIVE", SofaColors.Live)
                Text(g.shortDetail, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextPrimary, maxLines = 1)
                if (CloseGame.isClose(g, state.closeRule)) Badge("CLOSE", SofaColors.Close, outlined = true)
            }
            GameState.PRE -> {
                val now = rememberNowMs(Countdown.tickMs(System.currentTimeMillis(), g.startEpochMs))
                val word = Countdown.startWord(g.league)
                Text(Countdown.format(now, g.startEpochMs, word)?.let { "$word $it" } ?: g.shortDetail, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1)
            }
            else -> Text(g.shortDetail.ifBlank { "Final" }, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1)
        }
    }
    if (state.showOdds && OverlayRow.DK_LINE in state.prefs.rows) g.odds?.let { o ->
        val spread = Betting.spreadFor(o, g, me.abbr)
        val status = spread?.let { Betting.status(Betting.coverMargin(g, me.abbr, it), g.state) }
        Text(
            listOfNotNull("DK ${Betting.summary(o)}", status?.let { coverLabel(it) }).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
    if (g.league.format == EventFormat.FIGHT) {
        g.eventName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    g.teams.forEach { t ->
        val other = if (t.id == g.home.id) g.away else g.home
        val fight = g.league.format == EventFormat.FIGHT
        OverlayScoreRow(
            if (fight) t.name else t.abbr,
            if (fight) (if (t.winner == true) "W" else "") else t.score?.toString() ?: "–",
            if (t.id == me.id) SofaColors.Blue else SofaColors.Red,
            leading = if (fight) t.winner == true else (t.score ?: 0) > (other.score ?: 0),
            pulseValue = t.score?.toDouble(),
        )
    }
    val sit = g.situation?.takeIf { g.state == GameState.LIVE }
    if (sit != null && OverlayRow.DOWN_DISTANCE in state.prefs.rows) {
        val ball = g.teams.firstOrNull { it.id == sit.possessionTeamId }
        listOfNotNull(ball?.let { "${it.abbr} ball" }, sit.detail, if (sit.isRedZone) "RED ZONE" else null)
            .takeIf { it.isNotEmpty() }
            ?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = if (sit.isRedZone) SofaColors.Live else SofaColors.TextSecondary, maxLines = 1) }
    }
    if (OverlayRow.LAST_PLAY in state.prefs.rows) g.situation?.lastPlay?.takeIf { it.isNotBlank() && g.state == GameState.LIVE }?.let {
        Text(TickerBuilder.trimPlay(it), style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "F1 · P1 Antonelli · P5 Norris" (favorites after the leader), or the start time. */
internal fun raceMini(r: Race, favorites: List<String>): String {
    if (r.results.isEmpty()) return "${r.league.label} · ${r.session} ${r.shortDetail}"
    val leader = r.results.first()
    val mine = r.favorites(favorites).mapNotNull { e -> r.positionOf(e)?.takeIf { it != 1 }?.let { "P$it ${e.name.substringAfterLast(' ')}" } }
    return (listOf(r.league.label, "P1 ${leader.name.substringAfterLast(' ')}") + mine).joinToString(" · ")
}

/** Race status, then the top three and any favorite drivers further back, favorites in blue. */
@Composable
private fun RaceRows(r: Race, favorites: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (r.state) {
            GameState.LIVE -> {
                Badge("LIVE", SofaColors.Live)
                Text(r.shortDetail, style = MaterialTheme.typography.labelMedium, color = SofaColors.TextPrimary, maxLines = 1)
            }
            GameState.PRE -> {
                val now = rememberNowMs(Countdown.tickMs(System.currentTimeMillis(), r.startEpochMs))
                Text("${r.session} ${Countdown.format(now, r.startEpochMs, "Starts") ?: r.shortDetail}", style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1)
            }
            else -> Text("${r.session} · Final", style = MaterialTheme.typography.labelMedium, color = SofaColors.TextSecondary, maxLines = 1)
        }
    }
    Text(r.name, style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    val mine = r.favorites(favorites).map { it.driverId }.toSet()
    val top = r.results.take(3)
    (top + r.results.filter { it.driverId in mine && it !in top }).forEach { e ->
        val isMine = e.driverId in mine
        OverlayScoreRow(
            e.name, "P${e.position}",
            if (isMine) SofaColors.Blue else SofaColors.Outline,
            leading = e.position == 1,
            pulseValue = null,
        )
    }
    if (r.results.isEmpty() && mine.isNotEmpty()) {
        Text("Racing: ${r.favorites(favorites).joinToString(", ") { it.name }}", style = MaterialTheme.typography.labelMedium, color = SofaColors.BlueBright, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Colour bar + name on the left, score on the right; the leader's score is brighter. */
@Composable
internal fun OverlayScoreRow(
    name: String,
    score: String,
    sideColor: Color,
    leading: Boolean,
    pulseValue: Double?,
    onlyIncrease: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(22.dp).background(sideColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = SofaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            score, style = MaterialTheme.typography.headlineSmall, color = if (leading) SofaColors.TextPrimary else SofaColors.TextSecondary,
            modifier = Modifier.padding(start = 8.dp).scorePulse(pulseValue, sideColor, onlyIncrease).padding(horizontal = 4.dp),
        )
    }
}

/** Short cover label for my side, for the overlay. */
internal fun coverLabel(s: BetStatus): String? = when (s) {
    BetStatus.WINNING -> "covering"
    BetStatus.LOSING -> "not covering"
    BetStatus.PUSHING -> "on the number"
    BetStatus.WON -> "covered"
    BetStatus.LOST -> "didn't cover"
    BetStatus.PUSH -> "push"
    BetStatus.PENDING -> null
}

/** "PICKS 3/5 hitting" + up to 5 picks that are live (or next up). */
@Composable
private fun PicksRows(picks: List<PickProgress>) {
    Text("PICKS ${PickEvaluator.hitting(picks)}/${picks.size} HITTING", style = MaterialTheme.typography.labelSmall, color = SofaColors.TextMuted)
    sortForDisplay(picks).filterNot { it.settled }.take(5).forEach { p ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(18.dp).background(bookColor(p.pick.book), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(p.pick.describe(p.game), style = MaterialTheme.typography.labelMedium, color = SofaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                p.current?.let { if (p.pick.type == PickType.SPREAD || p.pick.type == PickType.MONEYLINE) Betting.formatLine(it) else Betting.formatNumber(it) } ?: "–",
                style = MaterialTheme.typography.labelLarge, color = statusColor(p.status),
                modifier = Modifier.padding(start = 6.dp).scorePulse(p.current, statusColor(p.status), onlyIncrease = false).padding(horizontal = 4.dp),
            )
        }
    }
}

/** Full-width headline bar along the bottom of the screen, over the video. */
@Composable
fun OverlayTickerBar(state: OverlayState, display: RenderedDisplay) {
    if (state.barTicker && display.visible) TickerBar(state.ticker, Modifier.background(SofaColors.Background))
}
