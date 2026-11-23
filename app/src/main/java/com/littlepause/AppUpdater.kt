package com.littlepause

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASE_API =
    "https://api.github.com/repos/sorodeleite/little-pause/releases/latest"

private val UpdateRose = Color(0xFFB8758D)
private val UpdateInk = Color(0xFF705A61)
private val UpdateMuted = Color(0xFFB49AA3)
private val UpdatePaper = Color(0xFFFFFFFF)

private data class AppRelease(
    val version: String,
    val notes: String,
    val downloadUrl: String
)

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("as atualizações oficiais aparecem aqui ♡") }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = UpdatePaper),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("um cantinho sempre em dia  ♡", color = UpdateInk)
            Text(message, color = UpdateMuted)

            Button(
                onClick = {
                    scope.launch {
                        checking = true
                        message = "procurando novidades..."
                        try {
                            val result = withContext(Dispatchers.IO) { findLatestRelease(context) }
                            release = result
                            downloadedFile = null
                            message = if (result == null) {
                                "você já está usando a versão mais recente ♡"
                            } else {
                                buildString {
                                    append("nova versão disponível: ")
                                    append(result.version)
                                    if (result.notes.isNotBlank()) {
                                        append("\n")
                                        append(result.notes.take(280))
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            message = "não consegui verificar agora. confira sua conexão e tente novamente."
                        } finally {
                            checking = false
                        }
                    }
                },
                enabled = !checking && !downloading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UpdateRose)
            ) {
                Text(if (checking) "verificando..." else "verificar atualizações", color = Color.White)
            }

            if (release != null) {
                Button(
                    onClick = {
                        scope.launch {
                            downloading = true
                            message = "baixando a atualização..."
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    downloadRelease(release!!.downloadUrl, release!!.version, context)
                                }
                                downloadedFile = file
                                message = "prontinho! a atualização foi baixada."
                            } catch (_: Exception) {
                                message = "não consegui baixar o arquivo. tente novamente."
                            } finally {
                                downloading = false
                            }
                        }
                    },
                    enabled = !checking && !downloading && downloadedFile == null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UpdateRose)
                ) {
                    Text(if (downloading) "baixando..." else "baixar atualização", color = Color.White)
                }
            }

            if (downloadedFile != null) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !context.packageManager.canRequestPackageInstalls()
                        ) {
                            val settingsIntent = Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(settingsIntent)
                            message = "permita a instalação para o Little Pause e volte aqui para continuar."
                        } else {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                downloadedFile!!
                            )
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(installIntent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UpdateRose)
                ) {
                    Text("instalar atualização", color = Color.White)
                }
            }
        }
    }
}

private fun findLatestRelease(context: android.content.Context): AppRelease? {
    val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "LittlePause-Android")
    try {
        if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("GitHub returned ${connection.responseCode}")
        }

        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val release = JSONObject(json)
        val tag = release.optString("tag_name").removePrefix("v")
        val currentVersion = context.packageManager
            .getPackageInfo(context.packageName, 0).versionName.orEmpty()

        if (tag.isBlank() || compareVersions(tag, currentVersion) <= 0) return null

        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return AppRelease(
                    version = tag,
                    notes = release.optString("body"),
                    downloadUrl = asset.optString("browser_download_url")
                )
            }
        }
        throw IllegalStateException("No APK attached to the latest release")
    } finally {
        connection.disconnect()
    }
}

private fun compareVersions(first: String, second: String): Int {
    val left = first.split(".").map { it.toIntOrNull() ?: 0 }
    val right = second.split(".").map { it.toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(index) { 0 }
        val b = right.getOrElse(index) { 0 }
        if (a != b) return a.compareTo(b)
    }
    return 0
}

private fun downloadRelease(url: String, version: String, context: android.content.Context): File {
    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    val destination = File(directory, "little-pause-$version.apk")
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    connection.setRequestProperty("User-Agent", "LittlePause-Android")
    try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Download failed: ${connection.responseCode}")
        }
        connection.inputStream.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    } finally {
        connection.disconnect()
    }
    return destination
}
