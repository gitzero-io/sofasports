package com.sofasports.tv.data

import com.sofasports.tv.BuildConfig
import com.sofasports.tv.data.update.UpdateRules
import com.sofasports.tv.data.update.UpdateStatus
import com.sofasports.tv.data.update.VersionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateRulesTest {
    private val url = "https://example.com/a.apk"

    @Test fun `published version json parses and never announces a build newer than this one`() {
        val info = UpdateRules.parse(File("../release-assets/version.json").readText())!!
        assertTrue(info.apkUrl.startsWith("https://"))
        assertEquals(UpdateStatus.UpToDate, UpdateRules.evaluate(info, BuildConfig.VERSION_CODE))
    }

    @Test fun `newer build is available, optional`() {
        val s = UpdateRules.evaluate(VersionInfo(latestVersionCode = 2, minSupportedVersionCode = 1, apkUrl = url), 1)
        assertTrue(s is UpdateStatus.Available && !s.required)
    }

    @Test fun `below min supported is required`() {
        val s = UpdateRules.evaluate(VersionInfo(latestVersionCode = 3, minSupportedVersionCode = 2, apkUrl = url), 1)
        assertTrue(s is UpdateStatus.Available && s.required)
    }

    @Test fun `older remote is up to date`() =
        assertEquals(UpdateStatus.UpToDate, UpdateRules.evaluate(VersionInfo(latestVersionCode = 1, apkUrl = url), 5))

    @Test fun `non https apk url is refused`() =
        assertTrue(UpdateRules.evaluate(VersionInfo(latestVersionCode = 9, apkUrl = "http://x/a.apk"), 1) is UpdateStatus.Failed)

    @Test fun `garbage is null`() = assertNull(UpdateRules.parse("not json"))
}
