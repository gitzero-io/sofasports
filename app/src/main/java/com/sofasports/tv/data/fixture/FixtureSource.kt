package com.sofasports.tv.data.fixture

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads bundled fixture JSON by path relative to `fixtures/`. */
interface FixtureSource {
    fun read(path: String): String?
}

/** Debug builds ship fixtures as assets; release has none, so every read returns null. */
@Singleton
class AssetFixtureSource @Inject constructor(@ApplicationContext private val context: Context) : FixtureSource {
    override fun read(path: String): String? =
        runCatching { context.assets.open("fixtures/$path").bufferedReader().use { it.readText() } }.getOrNull()
}
