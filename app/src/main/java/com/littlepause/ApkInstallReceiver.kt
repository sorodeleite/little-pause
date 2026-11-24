package com.littlepause

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val prefs = context.getSharedPreferences("little_pause_updater", Context.MODE_PRIVATE)
        val expectedId = prefs.getLong("download_id", -2L)
        if (completedId == -1L || completedId != expectedId) return

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(completedId)
        manager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0 || cursor.getInt(statusIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                Toast.makeText(context, "Não foi possível baixar a atualização.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val apkUri = manager.getUriForDownloadedFile(completedId)
        if (apkUri == null) {
            Toast.makeText(context, "O arquivo da atualização não foi encontrado.", Toast.LENGTH_LONG).show()
            return
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(installIntent) }.onFailure {
            Toast.makeText(context, "Abra o download do Little Pause para instalar a atualização.", Toast.LENGTH_LONG).show()
        }
    }
}
