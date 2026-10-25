package com.sofasports.tv.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.sofasports.tv.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InstallState {
    data object Idle : InstallState
    /** Android's "Install unknown apps" switch is off for Sofa Sports; we sent the user to it. */
    data object NeedsPermission : InstallState
    data class Downloading(val percent: Int?) : InstallState
    /** Handed to Android's installer; it asks the user to confirm, then replaces (and closes) the app. */
    data object Installing : InstallState
    data class Failed(val reason: String) : InstallState
}

/**
 * Downloads the APK from version.json and installs it through a PackageInstaller session, so the
 * update is one button press and Android's usual "Update this app?" prompt. Android itself refuses
 * an APK signed with a different key, so a tampered download can't replace the app.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val http: HttpClient,
) {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    val canInstall: Boolean get() = context.packageManager.canRequestPackageInstalls()

    suspend fun install(info: VersionInfo) {
        if (_state.value is InstallState.Downloading || _state.value == InstallState.Installing) return
        if (!canInstall) {
            openInstallPermission()
            _state.value = InstallState.NeedsPermission
            return
        }
        _state.value = InstallState.Downloading(null)
        val result = runCatching {
            val apk = download(info.apkUrl)
            info.apkSha256.takeIf { it.isNotBlank() }?.let { want ->
                if (!sha256(apk).equals(want, ignoreCase = true)) error("download was corrupted, try again")
            }
            checkArchive(apk)
            commit(apk)
        }
        _state.value = result.fold({ InstallState.Installing }, { InstallState.Failed(it.message ?: "download failed") })
    }

    /** Called by [InstallResultReceiver] when Android reports how the install went. */
    internal fun onResult(status: Int, message: String?) {
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                InstallState.Failed("this build isn't signed like the installed one; install it with adb instead")
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallState.Failed("not enough storage on the TV")
            else -> InstallState.Failed(message ?: "install failed")
        }
    }

    /** Opens Android's "Install unknown apps" page for us, falling back to the general security page. */
    fun openInstallPermission() {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in intents) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    private suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "sofasports.apk")
        http.prepareGet(url) { timeout { requestTimeoutMillis = 10 * 60_000L } }.execute { res ->
            if (!res.status.isSuccess()) error("download failed (HTTP ${res.status.value})")
            val total = res.contentLength()
            val channel = res.bodyAsChannel()
            val buf = ByteArray(64 * 1024)
            var done = 0L
            out.outputStream().use { os ->
                while (true) {
                    val n = channel.readAvailable(buf)
                    if (n < 0) break
                    if (n == 0) { if (channel.isClosedForRead) break else continue }
                    os.write(buf, 0, n)
                    done += n
                    _state.value = InstallState.Downloading(total?.takeIf { it > 0 }?.let { (done * 100 / it).toInt() })
                }
            }
        }
        out
    }

    /** Catch a wrong or truncated file before Android shows a confusing "App not installed". */
    private fun checkArchive(apk: File) {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: error("download wasn't a valid app")
        if (info.packageName != context.packageName) error("download was a different app")
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        if (code <= BuildConfig.VERSION_CODE) error("download isn't newer than this version")
    }

    private suspend fun commit(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("sofasports.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Receives the installer's answer; "needs confirmation" means showing Android's prompt. */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {
    @Inject lateinit var installer: UpdateInstaller

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
            runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        installer.onResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
