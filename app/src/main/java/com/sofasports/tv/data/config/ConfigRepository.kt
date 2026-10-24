package com.sofasports.tv.data.config

import android.content.Context
import android.util.Log
import com.sofasports.tv.BuildConfig
import com.sofasports.tv.data.provider.ApiBases
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote config: starts from the cached copy (or built-in defaults), then refreshes from
 * [BuildConfig.CONFIG_URL]. A failed or invalid fetch never replaces a good config.
 */
@Singleton
class ConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpClient,
) : ApiBases {

    private val cacheFile get() = File(context.filesDir, "remote_config.json")

    private val _config = MutableStateFlow(loadCached() ?: RemoteConfig.DEFAULT)
    val config: StateFlow<RemoteConfig> = _config.asStateFlow()

    val killed: Boolean get() = _config.value.killSwitch.appliesTo(BuildConfig.VERSION_CODE)

    override val espnNflBase: String get() = _config.value.endpoints.espnNflBase
    override val sleeperBase: String get() = _config.value.endpoints.sleeperBase
    override val espnFantasyBase: String get() = _config.value.endpoints.espnFantasyBase

    /** Returns true if a new config was applied. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val body = runCatching {
            val res = http.get(BuildConfig.CONFIG_URL)
            if (res.status.isSuccess()) res.bodyAsText() else null
        }.onFailure { Log.i(TAG, "config fetch failed: ${it.message}") }.getOrNull() ?: return@withContext false
        val parsed = RemoteConfig.parseOrNull(body) ?: return@withContext false
        runCatching { cacheFile.writeText(body) }
        _config.value = parsed
        true
    }

    private fun loadCached(): RemoteConfig? =
        runCatching { cacheFile.takeIf { it.exists() }?.readText() }.getOrNull()?.let(RemoteConfig::parseOrNull)

    private companion object { const val TAG = "SofaConfig" }
}
