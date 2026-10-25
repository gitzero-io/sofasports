package com.sofasports.tv.data.update

import com.sofasports.tv.BuildConfig
import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.data.config.ConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** version.json (sample in /release-assets). */
@Serializable
data class VersionInfo(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    /** Builds below this must update (shown as a required update). */
    val minSupportedVersionCode: Int = 0,
    val apkUrl: String = "",
    /** Optional; when set, the in-app installer checks the download against it. */
    val apkSha256: String = "",
    val notes: String = "",
)

sealed interface UpdateStatus {
    data object Unknown : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: VersionInfo, val required: Boolean) : UpdateStatus
    data class Failed(val reason: String) : UpdateStatus
}

object UpdateRules {
    fun parse(json: String): VersionInfo? = runCatching { SofaJson.decodeFromString(VersionInfo.serializer(), json) }.getOrNull()

    fun evaluate(info: VersionInfo, currentVersionCode: Int): UpdateStatus = when {
        info.latestVersionCode <= currentVersionCode -> UpdateStatus.UpToDate
        !info.apkUrl.startsWith("https://") -> UpdateStatus.Failed("version.json has no https apkUrl")
        else -> UpdateStatus.Available(info, required = currentVersionCode < info.minSupportedVersionCode)
    }
}

/** Checks [BuildConfig.VERSION_URL]. Installing is [UpdateInstaller], and only when the user asks. */
@Singleton
class UpdateChecker @Inject constructor(
    private val http: HttpClient,
    private val config: ConfigRepository,
) {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()
    private var lastCheckMs = 0L

    /** Background check, at most every 12 h, and only if remote config allows it. */
    suspend fun checkIfDue() {
        if (!config.config.value.features.updateCheck) return
        if (System.currentTimeMillis() - lastCheckMs < 12 * 3_600_000L) return
        check()
    }

    suspend fun check(): UpdateStatus {
        _status.value = UpdateStatus.Checking
        lastCheckMs = System.currentTimeMillis()
        val result = runCatching {
            val res = http.get(BuildConfig.VERSION_URL)
            if (!res.status.isSuccess()) return@runCatching UpdateStatus.Failed("HTTP ${res.status.value}")
            val info = UpdateRules.parse(res.bodyAsText()) ?: return@runCatching UpdateStatus.Failed("version.json unreadable")
            UpdateRules.evaluate(info, BuildConfig.VERSION_CODE)
        }.getOrElse { UpdateStatus.Failed(it.message ?: "network error") }
        _status.value = result
        return result
    }
}
