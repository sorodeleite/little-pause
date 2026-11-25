package com.littlepause

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class PauseReminder(
    val id: Long,
    val title: String,
    val date: String,
    val time: String,
    val enabled: Boolean
)

private const val REMINDERS_KEY = "pause_reminders_json"

internal fun loadReminders(prefs: android.content.SharedPreferences): List<PauseReminder> {
    val json = runCatching { JSONArray(prefs.getString(REMINDERS_KEY, "[]")) }.getOrNull() ?: JSONArray()
    return (0 until json.length()).mapNotNull { index ->
        val item = json.optJSONObject(index) ?: return@mapNotNull null
        PauseReminder(
            id = item.optLong("id"),
            title = item.optString("title"),
            date = item.optString("date"),
            time = item.optString("time"),
            enabled = item.optBoolean("enabled", true)
        )
    }
}

internal fun saveReminders(
    prefs: android.content.SharedPreferences,
    reminders: List<PauseReminder>
) {
    val json = JSONArray()
    reminders.forEach { reminder ->
        json.put(
            JSONObject()
                .put("id", reminder.id)
                .put("title", reminder.title)
                .put("date", reminder.date)
                .put("time", reminder.time)
                .put("enabled", reminder.enabled)
        )
    }
    prefs.edit().putString(REMINDERS_KEY, json.toString()).apply()
}

internal fun scheduleReminder(context: Context, reminder: PauseReminder) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pending = reminderIntent(context, reminder)
    if (!reminder.enabled) {
        alarm.cancel(pending)
        return
    }
    val dateTime = runCatching {
        LocalDateTime.parse("${reminder.date}T${reminder.time}:00")
    }.getOrNull() ?: return
    val millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    if (millis > System.currentTimeMillis()) {
        runCatching { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending) }
    }
}

internal fun cancelReminder(context: Context, id: Long) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    alarm.cancel(reminderIntent(context, PauseReminder(id, "", "", "", true)))
}

private fun reminderIntent(context: Context, reminder: PauseReminder): PendingIntent {
    val intent = Intent(context, ReminderReceiver::class.java)
        .putExtra("reminder_title", reminder.title)
        .putExtra("reminder_id", reminder.id)
    return PendingIntent.getBroadcast(
        context,
        reminder.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/* o resumo das 22h: um bilhetinho sobre o tempo de tela do dia */

internal fun scheduleDailyUsageReport(context: Context) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pending = dailyReportIntent(context)
    val now = ZonedDateTime.now()
    var next = now.toLocalDate().atTime(22, 0).atZone(now.zone)
    if (!next.isAfter(now)) next = next.plusDays(1)
    runCatching {
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
    }
}

internal fun cancelDailyUsageReport(context: Context) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    alarm.cancel(dailyReportIntent(context))
}

private fun dailyReportIntent(context: Context): PendingIntent {
    val intent = Intent(context, DailyUsageReceiver::class.java).setAction(DailyUsageReceiver.ACTION_REPORT)
    return PendingIntent.getBroadcast(
        context,
        2200,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun todayLabel(): String = LocalDate.now().format(
    java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", java.util.Locale("pt", "BR"))
)
