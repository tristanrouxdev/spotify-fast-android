package com.tristan.spotifyfast.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tristan.spotifyfast.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0L,
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String = "",
    val body: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<GitHubAssetDto> = emptyList(),
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpdateAvailable(
        val tagName: String,
        val releaseNotes: String,
        val apkUrl: String,
        val apkSizeBytes: Long,
    ) : UpdateStatus
    data class Downloading(val percent: Int) : UpdateStatus
    data class ReadyToInstall(val apkFile: File, val tagName: String) : UpdateStatus
    data class UpToDate(val currentVersion: String) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/**
 * Checks GitHub Releases API (`api.github.com/repos/<owner>/<repo>/releases/latest`),
 * downloads the attached `.apk` asset into `cacheDir/updates/`, and invokes Android's package installer.
 */
class AppUpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val apiBaseUrl: String = "https://api.github.com",
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    fun resetStatus() {
        _status.value = UpdateStatus.Idle
    }

    suspend fun fetchLatestRelease(repoSlug: String): Result<GitHubReleaseDto> = withContext(ioDispatcher) {
        runCatching {
            val cleanRepo = repoSlug.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
            require(cleanRepo.contains("/")) { "Format dépôt GitHub invalide (attendu : utilisateur/depot)" }

            val base = apiBaseUrl.trimEnd('/')
            val latestReq = Request.Builder()
                .url("$base/repos/$cleanRepo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()

            httpClient.newCall(latestReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    return@runCatching json.decodeFromString<GitHubReleaseDto>(body)
                }
                if (resp.code == 404) {
                    // Fallback to /releases list (in case only pre-releases exist)
                    val listReq = Request.Builder()
                        .url("$base/repos/$cleanRepo/releases?per_page=1")
                        .header("Accept", "application/vnd.github+json")
                        .build()
                    httpClient.newCall(listReq).execute().use { listResp ->
                        if (listResp.isSuccessful) {
                            val listBody = listResp.body?.string().orEmpty()
                            val releases = json.decodeFromString<List<GitHubReleaseDto>>(listBody)
                            return@runCatching releases.firstOrNull()
                                ?: throw IllegalStateException("Aucune Release publiée sur github.com/$cleanRepo")
                        }
                    }
                    throw IllegalStateException("Dépôt ou Release introuvable sur github.com/$cleanRepo")
                }
                throw IllegalStateException("Erreur GitHub API (HTTP ${resp.code})")
            }
        }
    }

    suspend fun checkAndDownloadAndInstall(repoSlug: String, forceDownload: Boolean = false) {
        _status.value = UpdateStatus.Checking
        val releaseRes = fetchLatestRelease(repoSlug)
        val release = releaseRes.getOrElse { err ->
            _status.value = UpdateStatus.Error(err.message ?: "Impossible de joindre GitHub")
            return
        }

        val apkAsset = release.assets.firstOrNull { it.name.equals("app-release.apk", ignoreCase = true) }
            ?: release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

        if (apkAsset == null) {
            _status.value = UpdateStatus.Error("Aucun fichier .apk trouvé dans la release ${release.tagName}")
            return
        }

        if (!forceDownload && !isNewerVersion(release.tagName, currentVersionName)) {
            _status.value = UpdateStatus.UpToDate(currentVersionName)
            return
        }

        _status.value = UpdateStatus.UpdateAvailable(
            tagName = release.tagName,
            releaseNotes = release.body,
            apkUrl = apkAsset.downloadUrl,
            apkSizeBytes = apkAsset.size,
        )

        val downloaded = downloadApk(apkAsset.downloadUrl, release.tagName, apkAsset.size)
        val apkFile = downloaded.getOrElse { err ->
            _status.value = UpdateStatus.Error(err.message ?: "Échec du téléchargement de la mise à jour")
            return
        }

        _status.value = UpdateStatus.ReadyToInstall(apkFile, release.tagName)
        withContext(Dispatchers.Main) {
            launchApkInstaller(apkFile)
        }
    }

    private suspend fun downloadApk(
        downloadUrl: String,
        tagName: String,
        expectedSize: Long,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { old -> old.delete() }
            val safeTag = tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val targetFile = File(updatesDir, "spotify-fast-$safeTag.apk")

            val req = Request.Builder().url(downloadUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "Téléchargement APK échoué (HTTP ${resp.code})" }
                val body = resp.body ?: error("Corps de réponse vide")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: expectedSize

                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (totalBytes > 0L) {
                                val pct = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastPercent) {
                                    lastPercent = pct
                                    _status.value = UpdateStatus.Downloading(pct)
                                }
                            }
                        }
                    }
                }
            }
            targetFile
        }
    }

    fun launchApkInstaller(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            _status.value = UpdateStatus.Error(
                "Autorisez l'installation d'applis inconnues pour Spotify Fast, puis relancez la mise à jour.",
            )
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    companion object {
        fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            val remoteParts = parseSemverParts(remoteTag)
            val currentParts = parseSemverParts(currentVersion)
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        private fun parseSemverParts(raw: String): List<Int> {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V")
                .substringBefore("-")
                .substringBefore("+")
            return cleaned.split(".").map { it.toIntOrNull() ?: 0 }
        }
    }
}
