package com.sofasports.tv.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorePulseTest {
    @Test fun `first value never pulses`() = assertFalse(ScorePulse.shouldPulse(null, 7.0, onlyIncrease = true))
    @Test fun `touchdown pulses`() = assertTrue(ScorePulse.shouldPulse(14.0, 21.0, onlyIncrease = true))
    @Test fun `no change no pulse`() = assertFalse(ScorePulse.shouldPulse(21.0, 21.0, onlyIncrease = true))
    @Test fun `game score correction down does not pulse`() = assertFalse(ScorePulse.shouldPulse(21.0, 20.0, onlyIncrease = true))
    @Test fun `fantasy drop pulses`() = assertTrue(ScorePulse.shouldPulse(88.4, 86.4, onlyIncrease = false))
    @Test fun `float noise ignored`() = assertFalse(ScorePulse.shouldPulse(88.40, 88.401, onlyIncrease = false))
    @Test fun `value disappearing does not pulse`() = assertFalse(ScorePulse.shouldPulse(10.0, null, onlyIncrease = true))
}
