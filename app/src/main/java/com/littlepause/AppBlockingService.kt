package com.littlepause

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class AppBlockingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlay: FrameLayout? = null
    private var foregroundPackage: String? = null
    private var lastQueryTime = 0L
    private var temporarilyAllowedUntil = 0L

    private val checkForeground = object : Runnable {
        override fun run() {
            checkCurrentApp()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        lastQueryTime = System.currentTimeMillis() - 15_000
        handler.post(checkForeground)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun checkCurrentApp() {
        val prefs = getSharedPreferences("little_pause", MODE_PRIVATE)
        if (!prefs.getBoolean("blocking_enabled", false)) {
            stopSelf()
            return
        }

        val now = System.currentTimeMillis()
        val usageStats = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usageStats.queryEvents(lastQueryTime, now)
        if (events != null) {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> foregroundPackage = event.packageName
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (foregroundPackage == event.packageName) foregroundPackage = null
                    }
                }
            }
        }
        lastQueryTime = now

        val activePackage = foregroundPackage
        val hasNewSchedules = runCatching {
            org.json.JSONArray(prefs.getString("block_schedules_json", "[]")).length() > 0
        }.getOrDefault(false)
        val scheduledForThisApp = activePackage != null && isScheduledForPackage(prefs, activePackage, now)
        val legacyApps = prefs.getStringSet("blocked_apps", emptySet()).orEmpty()
        val legacyScheduleAllows = !prefs.getBoolean("schedule_enabled", false) || isWithinSchedule(prefs, now)
        val legacyShouldBlock = !hasNewSchedules && legacyScheduleAllows &&
            activePackage != null && activePackage in legacyApps
        val shouldBlock = now >= temporarilyAllowedUntil &&
            activePackage != null && activePackage != packageName &&
            (if (hasNewSchedules) scheduledForThisApp else legacyShouldBlock)

        if (shouldBlock) showPauseScreen() else hidePauseScreen()
    }

    private fun isScheduledForPackage(
        prefs: android.content.SharedPreferences,
        appPackage: String,
        nowMillis: Long
    ): Boolean {
        val schedules = runCatching {
            org.json.JSONArray(prefs.getString("block_schedules_json", "[]"))
        }.getOrNull() ?: return false
        val current = java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault())
        val minuteNow = current.hour * 60 + current.minute
        val today = current.dayOfWeek.value
        val previousDay = if (today == 1) 7 else today - 1

        for (index in 0 until schedules.length()) {
            val item = schedules.optJSONObject(index) ?: continue
            if (!item.optBoolean("enabled", true)) continue
            val packages = item.optJSONArray("packages") ?: continue
            var includesApp = false
            for (appIndex in 0 until packages.length()) {
                if (packages.optString(appIndex) == appPackage) {
                    includesApp = true
                    break
                }
            }
            if (!includesApp) continue

            val dayArray = item.optJSONArray("days") ?: continue
            val selectedDays = (0 until dayArray.length()).map { dayArray.optString(it) }.toSet()
            if (selectedDays.isEmpty()) continue

            val startParts = item.optString("start", "08:00").split(":")
            val endParts = item.optString("end", "18:00").split(":")
            val start = (startParts.getOrNull(0)?.toIntOrNull() ?: 8) * 60 +
                (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
            val end = (endParts.getOrNull(0)?.toIntOrNull() ?: 18) * 60 +
                (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

            val active = when {
                start == end -> today.toString() in selectedDays
                start < end -> today.toString() in selectedDays && minuteNow >= start && minuteNow < end
                else -> (today.toString() in selectedDays && minuteNow >= start) ||
                    (previousDay.toString() in selectedDays && minuteNow < end)
            }
            if (active) return true
        }
        return false
    }

    private fun isWithinSchedule(prefs: android.content.SharedPreferences, nowMillis: Long): Boolean {
        val days = prefs.getStringSet("schedule_days", emptySet()).orEmpty()
        if (days.isEmpty()) return false
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault())
        val minuteNow = now.hour * 60 + now.minute
        val startParts = (prefs.getString("block_start", "08:00") ?: "08:00").split(":")
        val endParts = (prefs.getString("block_end", "18:00") ?: "18:00").split(":")
        val start = (startParts.getOrNull(0)?.toIntOrNull() ?: 8) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val end = (endParts.getOrNull(0)?.toIntOrNull() ?: 18) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val today = now.dayOfWeek.value
        if (start == end) return today.toString() in days
        if (start < end) return today.toString() in days && minuteNow >= start && minuteNow < end
        val previousDay = if (today == 1) 7 else today - 1
        return (today.toString() in days && minuteNow >= start) ||
            (previousDay.toString() in days && minuteNow < end)
    }

    private fun showPauseScreen() {
        if (overlay != null || !android.provider.Settings.canDrawOverlays(this)) return

        val root = FrameLayout(this).apply {
            setBackgroundColor(AndroidColor.rgb(255, 248, 245))
            isClickable = true
            isFocusable = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(56, 48, 56, 48)
        }

        val heading = TextView(this).apply {
            text = "um minutinho para respirar  ♡"
            textSize = 23f
            setTextColor(AndroidColor.rgb(112, 90, 97))
            gravity = Gravity.CENTER
        }

        val message = TextView(this).apply {
            text = "você escolheu fazer uma pequena pausa antes de continuar."
            textSize = 15f
            setTextColor(AndroidColor.rgb(180, 154, 163))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 32)
        }

        val backButton = Button(this).apply {
            text = "voltar para o meu dia"
            setOnClickListener {
                temporarilyAllowedUntil = System.currentTimeMillis() + 2500
                hidePauseScreen()
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(home)
            }
        }

        content.addView(heading)
        content.addView(message)
        content.addView(backButton)
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager?.addView(root, params)
            overlay = root
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun hidePauseScreen() {
        overlay?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlay = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bloqueio de aplicativos",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("little pause")
            .setContentText("seu cantinho de foco está ativo")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkForeground)
        hidePauseScreen()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "little_pause_blocker"
        private const val NOTIFICATION_ID = 31
        const val ACTION_STOP = "com.littlepause.STOP_BLOCKING"
    }
}
