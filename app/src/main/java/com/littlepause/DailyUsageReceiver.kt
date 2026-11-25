package com.littlepause

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.ZoneId

class DailyUsageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("little_pause", Context.MODE_PRIVATE)
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (prefs.getBoolean("daily_usage_report_enabled", true)) scheduleNext(context)
            return
        }
        if (intent?.action != ACTION_REPORT || !prefs.getBoolean("daily_usage_report_enabled", true)) return

        scheduleNext(context)
        val now = System.currentTimeMillis()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val usageAllowed = hasUsageAccess(context)
        val totalMillis = if (usageAllowed) {
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            calculateUniqueForegroundTime(usageManager, start, now)
        } else 0L

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "little_pause_daily_summary"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Resumo diário", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val message = if (!usageAllowed) {
            "Ative o acesso ao uso nas configurações do Android para receber seu resumo de tempo de tela ♡"
        } else {
            "Hoje você passou ${formatDuration(totalMillis)} no celular. ${movieComparison(totalMillis)} Amanhã também merece um pouquinho de vida fora da tela. ☁"
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("little pause  ♡  seu dia em números")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(2200, notification)
    }

    /**
     * Counts the union of foreground activity intervals. Summing each app's
     * totalTimeInForeground can count the same wall-clock minute more than
     * once when Android reports overlapping/multi-resume activities.
     */
    private fun calculateUniqueForegroundTime(
        usageManager: UsageStatsManager,
        start: Long,
        end: Long
    ): Long {
        val events = usageManager.queryEvents(start, end)
        val activeActivities = mutableSetOf<String>()
        var activeSince: Long? = null
        var total = 0L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            val activityKey = "$packageName/${event.className.orEmpty()}"

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val wasInactive = activeActivities.isEmpty()
                    activeActivities.add(activityKey)
                    if (wasInactive && activeActivities.isNotEmpty()) {
                        activeSince = event.timeStamp.coerceIn(start, end)
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    activeActivities.remove(activityKey)
                    if (activeActivities.isEmpty()) {
                        activeSince?.let { since ->
                            total += (event.timeStamp.coerceIn(start, end) - since).coerceAtLeast(0L)
                        }
                        activeSince = null
                    }
                }
            }
        }

        if (activeActivities.isNotEmpty()) {
            activeSince?.let { since ->
                total += (end - since).coerceAtLeast(0L)
            }
        }
        return total.coerceAtMost(end - start)
    }

    private fun scheduleNext(context: Context) {
        val intent = Intent(context, DailyUsageReceiver::class.java).setAction(ACTION_REPORT)
        val pending = PendingIntent.getBroadcast(
            context, 2200, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val now = java.time.ZonedDateTime.now()
        var next = now.toLocalDate().atTime(22, 0).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        return appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun formatDuration(millis: Long): String {
        val minutes = (millis / 60_000L).toInt()
        val hours = minutes / 60
        val remainder = minutes % 60
        return when {
            hours > 0 && remainder > 0 -> "${hours}h${remainder.toString().padStart(2, '0')}"
            hours > 0 -> "${hours}h"
            else -> "${minutes} min"
        }
    }

    private fun movieComparison(millis: Long): String {
        val minutes = (millis / 60_000L).toInt()
        val movies = minutes / 100
        val remainder = minutes % 100
        return when {
            movies > 0 && remainder > 0 -> "Nesse tempo, você podia ter assistido a $movies ${if (movies == 1) "filme" else "filmes"} de 1h40 e ainda sobrariam $remainder min de outro!"
            movies > 0 -> "Nesse tempo, você podia ter assistido a $movies ${if (movies == 1) "filme" else "filmes"} de aproximadamente 1h40!"
            minutes >= 10 -> "Isso daria para assistir a $minutes min de um filme — quase ${(minutes * 100 / 100)}% de um filme de 1h40!"
            else -> "Foi menos de 10 min de tela hoje. Que tal continuar nesse ritmo gentil? ♡"
        }
    }

    companion object {
        const val ACTION_REPORT = "com.littlepause.DAILY_USAGE_REPORT"
    }
}
