package com.littlepause

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASE_API = "https://api.github.com/repos/sorodeleite/little-pause/releases/latest"
private const val APK_ASSET_NAME = "little-pause.apk"
private const val PREFS_NAME = "little_pause_updater"
private const val PREF_DOWNLOAD_ID = "download_id"

private val UpdateRose = Color(0xFFB8758D)
private val UpdateInk = Color(0xFF705A61)
private val UpdateMuted = Color(0xFFB49AA3)
private val UpdatePaper = Color(0xFFFFFFFF)

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("confira se existe uma versão nova do Little Pause ♡") }
    var checking by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = UpdatePaper),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("um cantinho sempre em dia  ♡", color = UpdateInk)
            Text(status, color = UpdateMuted)
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !context.packageManager.canRequestPackageInstalls()
                    ) {
                        status = "permita a instalação de apps para o Little Pause e toque novamente ♡"
                        val settingsIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(settingsIntent)
                        return@Button
                    }

                    if (checking) return@Button
                    checking = true
                    status = "procurando atualizações... ♡"

                    Thread {
                        val result = runCatching {
                            val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                                requestMethod = "GET"
                                connectTimeout = 15000
                                readTimeout = 15000
                                setRequestProperty("Accept", "application/vnd.github+json")
                                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                                setRequestProperty("User-Agent", "Little-Pause-Android")
                            }
                            try {
                                if (connection.responseCode !in 200..299) {
                                    error("Não foi possível consultar as versões.")
                                }
                                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                                val tag = release.optString("tag_name").removePrefix("v")
                                val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                                val assets = release.optJSONArray("assets") ?: error("A versão mais recente não tem arquivos.")
                                var apkUrl: String? = null
                                for (i in 0 until assets.length()) {
                                    val asset = assets.optJSONObject(i) ?: continue
                                    if (asset.optString("name") == APK_ASSET_NAME) {
                                        apkUrl = asset.optString("browser_download_url")
                                        break
                                    }
                                }
                                if (apkUrl.isNullOrBlank()) error("O APK ainda não está disponível nesta versão.")
                                if (compareVersions(tag, current) <= 0) {
                                    "already"
                                } else {
                                    val request = DownloadManager.Request(Uri.parse(apkUrl))
                                        .setTitle("Little Pause • atualização")
                                        .setDescription("Baixando a versão $tag")
                                        .setMimeType("application/vnd.android.package-archive")
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setAllowedOverMetered(true)
                                        .setAllowedOverRoaming(true)
                                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    val downloadId = manager.enqueue(request)
                                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit().putLong(PREF_DOWNLOAD_ID, downloadId).apply()
                                    "download"
                                }
                            } finally {
                                connection.disconnect()
                            }
                        }

                        Handler(Looper.getMainLooper()).post {
                            checking = false
                            status = result.fold(
                                onSuccess = {
                                    when (it) {
                                        "already" -> "você já está usando a versão mais recente! ♡"
                                        "download" -> "nova versão encontrada! o download começou; ao terminar, o Android abrirá a instalação ♡"
                                        else -> "não foi possível verificar a atualização."
                                    }
                                },
                                onFailure = { "não consegui verificar agora: ${it.message ?: "tente novamente mais tarde"}" }
                            )
                        }
                    }.start()
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UpdateRose)
            ) {
                Text(if (checking) "procurando..." else "procurar atualização", color = Color.White)
            }
        }
    }
}

private fun compareVersions(first: String, second: String): Int {
    val a = first.split(".").map { it.toIntOrNull() ?: 0 }
    val b = second.split(".").map { it.toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(index) { 0 }
        val right = b.getOrElse(index) { 0 }
        if (left != right) return left.compareTo(right)
    }
    return 0
}
