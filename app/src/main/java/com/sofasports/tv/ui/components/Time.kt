package com.sofasports.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Current time, re-read every [intervalMs] while composed. */
@Composable
fun rememberNowMs(intervalMs: Long = 1_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs - System.currentTimeMillis() % intervalMs.coerceAtLeast(1))
            now = System.currentTimeMillis()
        }
    }
    return now
}

private val kickoffFormat = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)

/** "Sun 1:00 PM" in the TV's time zone. */
fun formatKickoff(epochMs: Long): String =
    kickoffFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
