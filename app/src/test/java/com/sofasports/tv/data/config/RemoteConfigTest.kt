package com.sofasports.tv.data.config

import com.sofasports.tv.data.provider.Endpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemoteConfigTest {

    @Test fun `sample in release-assets parses to the defaults`() {
        // Unit tests run from the :app module dir.
        val sample = File("../release-assets/config.json").readText()
        val cfg = RemoteConfig.parseOrNull(sample)
        assertEquals(RemoteConfig.DEFAULT.sanitized(), cfg)
    }

    @Test fun `garbage and wrong types return null so the old config is kept`() {
        assertNull(RemoteConfig.parseOrNull("<html>404</html>"))
        assertNull(RemoteConfig.parseOrNull("""{"pollIntervalLiveSec": {"x":1}}"""))
    }

    @Test fun `partial config fills in defaults and ignores unknown keys`() {
        val cfg = RemoteConfig.parseOrNull("""{"announcement":"hi","newThing":true,"features":{"overlay":false}}""")!!
        assertEquals("hi", cfg.announcement)
        assertFalse(cfg.features.overlay)
        assertTrue(cfg.features.fantasy)
        assertEquals(30, cfg.pollIntervalLiveSec)
    }

    @Test fun `dangerous values are clamped`() {
        val cfg = RemoteConfig.parseOrNull(
            """{"pollIntervalLiveSec":1,"closeGame":{"maxPointDiff":-5,"minPeriod":9},
               "endpoints":{"espnNflBase":"http://insecure.example","sleeperBase":""},"announcement":"  "}"""
        )!!
        assertEquals(RemoteConfig.MIN_POLL_SEC, cfg.pollIntervalLiveSec)
        assertEquals(0, cfg.closeGame.maxPointDiff)
        assertEquals(5, cfg.closeGame.minPeriod)
        assertEquals(Endpoints.ESPN_NFL_BASE, cfg.endpoints.espnNflBase)
        assertEquals(Endpoints.SLEEPER_BASE, cfg.endpoints.sleeperBase)
        assertNull(cfg.announcement)
    }

    @Test fun `kill switch can target old versions only`() {
        assertFalse(KillSwitch(enabled = false).appliesTo(1))
        assertTrue(KillSwitch(enabled = true).appliesTo(99))
        assertTrue(KillSwitch(enabled = true, maxVersionCode = 3).appliesTo(3))
        assertFalse(KillSwitch(enabled = true, maxVersionCode = 3).appliesTo(4))
    }
}
