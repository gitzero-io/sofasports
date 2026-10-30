package com.sofasports.tv.ui.picks

import com.sofasports.tv.domain.BetStatus
import com.sofasports.tv.domain.PickBook
import com.sofasports.tv.domain.PickProgress
import com.sofasports.tv.domain.PickType
import com.sofasports.tv.domain.PropStat
import com.sofasports.tv.domain.TrackedPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressFractionTest {
    private fun progress(type: PickType, line: Double, current: Double?) =
        PickProgress(TrackedPick("p", PickBook.PRIZEPICKS, type, "HOU", line, stat = PropStat.PASS_YDS), null, current, BetStatus.WINNING)

    @Test fun `over-under picks fill toward the line`() {
        assertEquals(0.5f, progressFraction(progress(PickType.PROP, 200.0, 100.0))!!, 0.001f)
        assertEquals(1.2f, progressFraction(progress(PickType.TOTAL, 40.0, 48.0))!!, 0.001f)
    }

    @Test fun `no bar for spreads, moneylines or picks without a number yet`() {
        assertNull(progressFraction(progress(PickType.SPREAD, -2.5, 3.0)))
        assertNull(progressFraction(progress(PickType.MONEYLINE, 0.0, 7.0)))
        assertNull(progressFraction(progress(PickType.PROP, 200.0, null)))
    }
}
