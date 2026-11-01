package pl.proszczok.player.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.proszczok.player.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    internal val apkUrl: URL,
    internal val sha256: String,
)

sealed interface InstallResult {
    data object Started : InstallResult
    data object PermissionRequired : InstallResult
    data class Failed(val message: String) : InstallResult
}

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

object UpdateManager {
    suspend fun check(): UpdateInfo? =
        (checkResult() as? UpdateCheckResult.Available)?.info

    suspend fun checkResult(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching<UpdateCheckResult> {
            val manifestUrl = URL(BuildConfig.UPDATE_MANIFEST_URL)
            val connection = (manifestUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PiggiPlayer/${BuildConfig.VERSION_NAME}")
            }
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()
            val root = JSONObject(json)
            val versionCode = root.getLong("versionCode")
            if (versionCode <= BuildConfig.VERSION_CODE) {
                UpdateCheckResult.UpToDate
            } else {
                val apk = root.getString("apk")
                val sha = root.getString("sha256").lowercase()
                require(sha.matches(Regex("[0-9a-f]{64}"))) { "Nieprawidłowa suma kontrolna aktualizacji" }
                UpdateCheckResult.Available(
                    UpdateInfo(
                        versionCode = versionCode,
                        versionName = root.getString("versionName"),
                        notes = root.optString("notes", "Nowa wersja aplikacji jest gotowa."),
                        apkUrl = URL(manifestUrl, apk),
                        sha256 = sha,
                    ),
                )
            }
        }.getOrElse {
            UpdateCheckResult.Failed(it.message ?: "Nie udało się połączyć z serwerem aktualizacji.")
        }
    }

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): InstallResult = withContext(Dispatchers.IO) {
        runCatching {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(updateDir, "piggi-player-update.apk")
            val connection = (info.apkUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 30_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PiggiPlayer/${BuildConfig.VERSION_NAME}")
            }
            connection.inputStream.use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
            connection.disconnect()
            val actual = apk.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            require(actual == info.sha256) { "Plik aktualizacji jest uszkodzony lub nieautoryzowany." }

            if (!context.packageManager.canRequestPackageInstalls()) {
                withContext(Dispatchers.Main) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                return@runCatching InstallResult.PermissionRequired
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
            InstallResult.Started
        }.getOrElse { InstallResult.Failed(it.message ?: "Nie udało się pobrać aktualizacji.") }
    }
}
