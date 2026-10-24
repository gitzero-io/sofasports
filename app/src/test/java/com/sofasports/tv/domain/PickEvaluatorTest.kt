package com.sofasports.tv.domain

import com.sofasports.tv.Fixtures
import com.sofasports.tv.data.espn.EspnParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickEvaluatorTest {
    private val s = EspnParser.parseSummary(Fixtures.read("espn/summary_401872996_hou_jax.json"))
    private val g = s.game // HOU 34 @ JAX 31, final
    private fun id(name: String) = s.playerStats.flatMap { it.rows }.first { it.name == name }.athleteId!!
    private fun prop(name: String, stat: PropStat, line: Double, over: Boolean = true) =
        TrackedPick("p", PickBook.PRIZEPICKS, PickType.PROP, "HOU", line, over, id(name), name, stat)

    @Test fun `reads stats from the real box score`() {
        val stroud = id("C.J. Stroud")
        assertEquals(312.0, PickEvaluator.statValue(s, stroud, PropStat.PASS_YDS)!!, 0.0)
        assertEquals(24.0, PickEvaluator.statValue(s, stroud, PropStat.COMPLETIONS)!!, 0.0)
        assertEquals(34.0, PickEvaluator.statValue(s, stroud, PropStat.PASS_ATTEMPTS)!!, 0.0)
        assertEquals(3.0, PickEvaluator.statValue(s, stroud, PropStat.PASS_TDS)!!, 0.0)
        assertEquals(1.0, PickEvaluator.statValue(s, stroud, PropStat.INTERCEPTIONS)!!, 0.0)
        assertEquals(107.0, PickEvaluator.statValue(s, id("Jaylin Noel"), PropStat.REC_YDS)!!, 0.0)
        assertEquals(4.0, PickEvaluator.statValue(s, id("Jaylin Noel"), PropStat.RECEPTIONS)!!, 0.0)
    }

    @Test fun `player in box score but not that table counts as zero`() =
        assertEquals(0.0, PickEvaluator.statValue(s, id("Jaylin Noel"), PropStat.RUSH_YDS)!!, 0.0)

    @Test fun `unknown player is null`() = assertNull(PickEvaluator.statValue(s, "nope", PropStat.PASS_YDS))

    @Test fun `final props settle`() {
        assertEquals(BetStatus.WON, PickEvaluator.evaluate(prop("C.J. Stroud", PropStat.PASS_YDS, 249.5), g, s).status)
        assertEquals(BetStatus.LOST, PickEvaluator.evaluate(prop("C.J. Stroud", PropStat.PASS_YDS, 249.5, over = false), g, s).status)
        assertEquals(BetStatus.PUSH, PickEvaluator.evaluate(prop("C.J. Stroud", PropStat.PASS_TDS, 3.0), g, s).status)
    }

    @Test fun `monotonic over settles early while live, yardage does not`() {
        val live = g.copy(state = GameState.LIVE)
        assertEquals(BetStatus.WON, PickEvaluator.evaluate(prop("C.J. Stroud", PropStat.PASS_TDS, 1.5), live, s).status)
        assertEquals(BetStatus.LOST, PickEvaluator.evaluate(prop("C.J. Stroud", PropStat.PASS_TDS, 1.5, over = false), live, s).status)
        assertEquals(BetStatus.WINNING, PickEvaluator.evaluate(prop("C.J. Stroud", PropStat.PASS_YDS, 249.5), live, s).status)
    }

    @Test fun `props before kickoff are pending`() =
        assertEquals(BetStatus.PENDING, PickEvaluator.evaluate(prop("C.J. Stroud", PropStat.PASS_YDS, 249.5), g.copy(state = GameState.PRE), null).status)

    @Test fun `spread total and moneyline from the final score`() {
        fun pick(type: PickType, team: String, line: Double = 0.0, over: Boolean = true) =
            TrackedPick("x", PickBook.DRAFTKINGS, type, team, line, over)
        assertEquals(BetStatus.WON, PickEvaluator.evaluate(pick(PickType.SPREAD, "HOU", 2.5), g, s).status)
        assertEquals(BetStatus.LOST, PickEvaluator.evaluate(pick(PickType.SPREAD, "JAX", -2.5), g, s).status)
        assertEquals(BetStatus.WON, PickEvaluator.evaluate(pick(PickType.TOTAL, "HOU", 42.5), g, s).status)
        assertEquals(BetStatus.LOST, PickEvaluator.evaluate(pick(PickType.TOTAL, "HOU", 42.5, over = false), g, s).status)
        assertEquals(BetStatus.WON, PickEvaluator.evaluate(pick(PickType.MONEYLINE, "HOU"), g, s).status)
        assertEquals(BetStatus.LOST, PickEvaluator.evaluate(pick(PickType.MONEYLINE, "JAX"), g, s).status)
        assertEquals(65.0, PickEvaluator.evaluate(pick(PickType.TOTAL, "HOU", 42.5), g, s).current!!, 0.0)
    }

    @Test fun `live over on game total is won once past the number`() {
        val live = g.copy(state = GameState.LIVE)
        assertEquals(BetStatus.WON, PickEvaluator.evaluate(TrackedPick("x", PickBook.DRAFTKINGS, PickType.TOTAL, "HOU", 42.5), live, s).status)
    }

    @Test fun `no game this week is pending`() =
        assertEquals(BetStatus.PENDING, PickEvaluator.evaluate(TrackedPick("x", PickBook.DRAFTKINGS, PickType.MONEYLINE, "HOU"), null, null).status)

    @Test fun `descriptions`() {
        assertEquals("C.J. Stroud Pass Yds O 224.5", prop("C.J. Stroud", PropStat.PASS_YDS, 224.5).describe())
        assertEquals("HOU +2.5", TrackedPick("x", PickBook.DRAFTKINGS, PickType.SPREAD, "HOU", 2.5).describe())
        assertEquals("HOU @ JAX U 42.5", TrackedPick("x", PickBook.DRAFTKINGS, PickType.TOTAL, "HOU", 42.5, over = false).describe(g))
    }

    @Test fun `stats offered depend on position`() {
        assertEquals(true, PropStat.PASS_YDS in PropStat.forPosition("QB"))
        assertEquals(false, PropStat.PASS_YDS in PropStat.forPosition("WR"))
        assertEquals(true, PropStat.REC_YDS in PropStat.forPosition("TE"))
    }
}
