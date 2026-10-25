package com.sofasports.tv.data.watch

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import com.sofasports.tv.domain.InstalledApp
import com.sofasports.tv.domain.LiveTvApps
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the user's own streaming apps. No app offers a public "open this game" link, so this opens
 * the app (the user picks the channel) or asks Google TV search for the game, which lists where to
 * watch it. The score overlay keeps running on top either way.
 */
@Singleton
class AppLauncher @Inject constructor(@ApplicationContext private val context: Context) {
    private val pm get() = context.packageManager

    /** Apps on the TV's home screen, known live TV apps first. */
    fun installedTvApps(): List<InstalledApp> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return runCatching { pm.queryIntentActivities(launcher, 0) }.getOrDefault(emptyList())
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName && it.pkg !in notVideoApps }
            .distinctBy { it.pkg }
            .sortedWith(compareBy<InstalledApp> { it.pkg !in LiveTvApps.known }.thenBy { it.label.lowercase() })
    }

    /** System and store apps that never play live sports. */
    private val notVideoApps = setOf(
        "com.android.vending", "com.google.android.play.games", "com.android.tv.settings",
        "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.google.android.youtube.tvmusic",
    )

    fun installedPackages(): Set<String> = installedTvApps().map { it.pkg }.toSet()

    fun launch(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Google TV's search for the game; its results show which installed app has it live. */
    fun searchGoogleTv(query: String): Boolean {
        val intent = Intent("android.search.action.GLOBAL_SEARCH")
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
