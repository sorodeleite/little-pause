package com.littlepause

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val API_LATEST = "https://api.github.com/repos/sorodeleite/little-pause/releases/latest"
private const val API_RECENT = "https://api.github.com/repos/sorodeleite/little-pause/releases?per_page=5"
private const val APK_ASSET = "little-pause.apk"
private const val RELEASES_PAGE = "https://github.com/sorodeleite/little-pause/releases"
private const val UPDATER_PREFS = "little_pause_updater"
private const val KEY_LAST_CHECK = "last_check"
private const val KEY_DOWNLOAD_ID = "download_id"
private const val KEY_DOWNLOAD_VERSION = "download_version"
private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

data class ReleaseNote(
    val version: String,
    val date: String,
    val notes: String,
    val url: String
)

data class UpdateState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    val latestVersion: String = "",
    val hasUpdate: Boolean = false,
    val apkUrl: String = "",
    val releaseUrl: String = RELEASES_PAGE,
    val notes: String = "",
    val recent: List<ReleaseNote> = emptyList(),
    val downloading: Boolean = false,
    val progress: Int = 0,
    val downloadedVersion: String = "",
    val canInstall: Boolean = true,
    val error: String = "",
    val checkedAt: Long = 0L
) {
    val downloaded: Boolean
        get() = downloadedVersion.isNotEmpty() && downloadedVersion == latestVersion
}

/**
 * A parte de atualizações, do jeitinho que ela funciona no sleep my angel:
 * confere a versão nova no GitHub, mostra o que mudou, baixa o APK e instala
 * por cima — só quando você manda.
 */
object AppUpdater {
    val state = mutableStateOf(UpdateState())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    }.getOrDefault("0.0.0")

    fun canInstall(context: Context): Boolean = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(true)

    /** chamado quando a tela abre: acerta a versão instalada e o que já foi baixado */
    fun refreshInstalled(context: Context) {
        val prefs = context.getSharedPreferences(UPDATER_PREFS, Context.MODE_PRIVATE)
        val version = currentVersion(context)
        val savedDownload = prefs.getString(KEY_DOWNLOAD_VERSION, "") ?: ""
        state.value = state.value.copy(
            currentVersion = version,
            downloadedVersion = savedDownload,
            canInstall = canInstall(context),
            checkedAt = prefs.getLong(KEY_LAST_CHECK, 0L)
        )
    }

    fun autoCheckIfDue(context: Context) {
        val prefs = context.getSharedPreferences(UPDATER_PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(context, silent = true)
    }

    fun check(context: Context, silent: Boolean = false) {
        val appContext = context.applicationContext
        if (state.value.checking || state.value.downloading) return
        state.value = state.value.copy(checking = true, error = "", progress = 0)
        scope.launch {
            val result = runCatching { fetchUpdates(appContext) }
            val current = currentVersion(appContext)
            result.onSuccess { payload ->
                val latest = payload.latest
                val hasUpdate = latest != null &&
                    compareVersions(latest.version, current) > 0
                val prefs = appContext.getSharedPreferences(UPDATER_PREFS, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                state.value = state.value.copy(
                    currentVersion = current,
                    checking = false,
                    latestVersion = latest?.version.orEmpty(),
                    hasUpdate = hasUpdate,
                    apkUrl = latest?.apkUrl.orEmpty(),
                    releaseUrl = latest?.url ?: RELEASES_PAGE,
                    notes = latest?.notes.orEmpty(),
                    recent = payload.recent,
                    progress = 0,
                    checkedAt = System.currentTimeMillis(),
                    error = ""
                )
            }.onFailure { error ->
                state.value = state.value.copy(
                    checking = false,
                    error = if (silent) "" else friendlyError(error)
                )
            }
        }
    }

    fun download(context: Context) {
        val current = state.value
        if (current.downloading || current.apkUrl.isBlank()) return
        val appContext = context.applicationContext
        state.value = current.copy(downloading = true, progress = 1, error = "")
        scope.launch {
            val request = DownloadManager.Request(Uri.parse(current.apkUrl))
                .setTitle("little pause • atualização")
                .setDescription("baixando a versão ${current.latestVersion}")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (manager == null) {
                state.value = state.value.copy(downloading = false, progress = 0, error = "não achei o gerenciador de downloads do Android.")
                return@launch
            }
            val id = runCatching { manager.enqueue(request) }.getOrElse { error ->
                state.value = state.value.copy(
                    downloading = false,
                    progress = 0,
                    error = "o download não começou: ${error.message ?: "tente de novo"}"
                )
                return@launch
            }
            appContext.getSharedPreferences(UPDATER_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_DOWNLOAD_ID, id)
                .putString(KEY_DOWNLOAD_VERSION, current.latestVersion)
                .apply()
            watchDownload(appContext, manager, id, current.latestVersion)
        }
    }

    private suspend fun watchDownload(
        context: Context,
        manager: DownloadManager,
        id: Long,
        version: String
    ) {
        repeat(900) {
            delay(400)
            val status = runCatching { downloadStatus(manager, id) }.getOrNull() ?: return@repeat
            when {
                status.done -> {
                    state.value = state.value.copy(
                        downloading = false,
                        progress = 100,
                        downloadedVersion = version,
                        canInstall = canInstall(context),
                        error = ""
                    )
                    context.getSharedPreferences(UPDATER_PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_DOWNLOAD_VERSION, version)
                        .apply()
                    return
                }
                status.failed -> {
                    state.value = state.value.copy(
                        downloading = false,
                        progress = 0,
                        error = "o download falhou. tenta de novo em um minutinho."
                    )
                    return
                }
                else -> {
                    state.value = state.value.copy(progress = status.progress)
                }
            }
        }
        state.value = state.value.copy(
            downloading = false,
            error = "o download está demorando mais que o normal. você pode tentar de novo."
        )
    }

    private data class DownloadStatus(val progress: Int, val done: Boolean, val failed: Boolean)

    private fun downloadStatus(manager: DownloadManager, id: Long): DownloadStatus {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id))
        return cursor.use { row ->
            if (!row.moveToFirst()) return@use DownloadStatus(0, false, false)
            val statusIndex = row.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesIndex = row.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIndex = row.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val status = if (statusIndex >= 0) row.getInt(statusIndex) else -1
            val soFar = if (bytesIndex >= 0) row.getLong(bytesIndex) else 0L
            val total = if (totalIndex >= 0) row.getLong(totalIndex) else 0L
            val progress = if (total > 0) ((soFar * 100) / total).toInt().coerceIn(0, 100) else 0
            DownloadStatus(
                progress = progress,
                done = status == DownloadManager.STATUS_SUCCESSFUL,
                failed = status == DownloadManager.STATUS_FAILED
            )
        }
    }

    fun install(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(UPDATER_PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L) {
            state.value = state.value.copy(error = "a atualização ainda não foi baixada.")
            return
        }
        if (!canInstall(appContext)) {
            state.value = state.value.copy(
                canInstall = false,
                error = "falta liberar a instalação: ative \"instalar apps desconhecidos\" para o little pause e volte aqui."
            )
            openInstallSettings(appContext)
            return
        }
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val apkUri = runCatching { manager.getUriForDownloadedFile(id) }.getOrNull()
        if (apkUri == null) {
            state.value = state.value.copy(error = "não encontrei o arquivo baixado. baixa de novo, por favor.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }.onFailure {
            state.value = state.value.copy(error = "não consegui abrir o instalador. toque no aviso de download do Android.")
        }
    }

    fun openInstallSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openReleases(context: Context) {
        val url = state.value.releaseUrl.ifBlank { RELEASES_PAGE }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /* ── github ───────────────────────────────────────────────── */

    private data class LatestRelease(
        val version: String,
        val notes: String,
        val url: String,
        val apkUrl: String
    )

    private data class Payload(val latest: LatestRelease?, val recent: List<ReleaseNote>)

    private suspend fun fetchUpdates(context: Context): Payload = withContext(Dispatchers.IO) {
        val latestJson = runCatching { getJson(API_LATEST) }.getOrNull()
        val recentJson = runCatching { getJsonArray(API_RECENT) }.getOrNull()

        val latest = latestJson?.let { release ->
            val assets = release.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (asset.optString("name") == APK_ASSET) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            LatestRelease(
                version = release.optString("tag_name").removePrefix("v"),
                notes = cleanNotes(release.optString("body")),
                url = release.optString("html_url").ifBlank { RELEASES_PAGE },
                apkUrl = apkUrl
            )
        }

        val recent = buildList {
            if (recentJson != null) {
                for (index in 0 until recentJson.length()) {
                    val item = recentJson.optJSONObject(index) ?: continue
                    if (item.optBoolean("draft")) continue
                    val version = item.optString("tag_name").removePrefix("v")
                    if (version.isBlank()) continue
                    add(
                        ReleaseNote(
                            version = version,
                            date = prettyDate(item.optString("published_at")),
                            notes = cleanNotes(item.optString("body")),
                            url = item.optString("html_url").ifBlank { RELEASES_PAGE }
                        )
                    )
                }
            }
        }
        Payload(latest, recent)
    }

    private fun getJson(url: String): JSONObject = JSONObject(request(url))
    private fun getJsonArray(url: String): JSONArray = JSONArray(request(url))

    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Little-Pause-Android")
            if (connection.responseCode !in 200..299) {
                error("o GitHub respondeu ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("404") ->
                "não achei nenhuma versão publicada ainda. toque em \"ver no github\" para abrir a página de releases."
            message.contains("403") ->
                "o GitHub pediu uma pausa (muitas consultas). tente de novo daqui a pouco."
            message.isBlank() ->
                "não consegui verificar agora. confira sua internet e tente de novo."
            else ->
                "não consegui verificar agora: $message"
        }
    }

    private fun prettyDate(iso: String): String = runCatching {
        OffsetDateTime.parse(iso)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("pt", "BR")))
    }.getOrDefault("")

    /** deixa as notas do release legíveis dentro do app */
    internal fun cleanNotes(value: String): String {
        val text = value.replace("\r", "").trim()
        if (text.isBlank()) return ""
        return text
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    internal fun compareVersions(first: String, second: String): Int {
        val a = first.split(".").map { it.toIntOrNull() ?: 0 }
        val b = second.split(".").map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}
