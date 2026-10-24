package com.sofasports.tv.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportingTest {
    @Test fun `blank or malformed DSN means no Sentry`() {
        assertFalse(CrashReporting.isValidDsn(null))
        assertFalse(CrashReporting.isValidDsn(""))
        assertFalse(CrashReporting.isValidDsn("   "))
        assertFalse(CrashReporting.isValidDsn("YOUR_SENTRY_DSN"))
    }

    @Test fun `real looking DSN is accepted`() =
        assertTrue(CrashReporting.isValidDsn("https://abc123@o0.ingest.sentry.io/0"))
}
