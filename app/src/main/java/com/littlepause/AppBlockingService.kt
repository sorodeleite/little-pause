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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class AppBlockingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlay: FrameLayout? = null
    private var overlayLabel: TextView? = null
    private var overlayTimer: TextView? = null
    private var overlayPrimary: TextView? = null
    private var overlayEscape: TextView? = null
    private var overlayPackage: String? = null
    private var countdownTask: Runnable? = null
    private var foregroundPackage: String? = null
    private var lastQueryTime = 0L
    private var temporarilyAllowedUntil = 0L
    private var lastUsageAccountedAt = 0L
    private var usageDay = ""
    private val dailyUsageMillis = mutableMapOf<String, Long>()

    private val checkForeground = object : Runnable {
        override fun run() {
            runCatching { checkCurrentApp() }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        lastQueryTime = System.currentTimeMillis() - 15_000
        lastUsageAccountedAt = System.currentTimeMillis()
        loadDailyUsage()
        handler.post(checkForeground)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!KeepAlive.isBlockingEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // a pessoa deslizou o app para fora das recentes: a pausa continua de pé
        if (KeepAlive.isBlockingEnabled(this)) KeepAlive.scheduleWatchdog(this)
        super.onTaskRemoved(rootIntent)
    }

    private fun checkCurrentApp() {
        val prefs = getSharedPreferences(KeepAlive.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("blocking_enabled", false)) {
            stopSelf()
            return
        }

        val now = System.currentTimeMillis()
        val usageStats = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        accountForegroundTime(now)
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
        val dailyLimitReached = activePackage != null && activePackage != packageName &&
            isDailyLimitReached(prefs, activePackage)
        val shouldBlock = now >= temporarilyAllowedUntil && activePackage != null &&
            activePackage != packageName &&
            (dailyLimitReached || (if (hasNewSchedules) scheduledForThisApp else legacyShouldBlock))

        if (shouldBlock && activePackage != null) {
            showPauseScreen(activePackage, dailyLimitReached)
        } else {
            hidePauseScreen()
        }
    }

    private fun loadDailyUsage() {
        val prefs = getSharedPreferences(KeepAlive.PREFS, MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        usageDay = prefs.getString("app_daily_usage_day", today) ?: today
        if (usageDay != today) {
            usageDay = today
            dailyUsageMillis.clear()
            prefs.edit().putString("app_daily_usage_day", today)
                .putString("app_daily_usage_json", "{}").apply()
            return
        }
        val saved = runCatching {
            org.json.JSONObject(prefs.getString("app_daily_usage_json", "{}") ?: "{}")
        }.getOrNull() ?: org.json.JSONObject()
        dailyUsageMillis.clear()
        val keys = saved.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            dailyUsageMillis[key] = saved.optLong(key, 0L).coerceAtLeast(0L)
        }
    }

    private fun accountForegroundTime(now: Long) {
        val today = java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        if (today != usageDay) {
            usageDay = today
            dailyUsageMillis.clear()
        }
        val elapsed = if (lastUsageAccountedAt > 0L) (now - lastUsageAccountedAt).coerceIn(0L, 2_000L) else 0L
        val active = foregroundPackage
        if (elapsed > 0L && active != null && active != packageName) {
            dailyUsageMillis[active] = (dailyUsageMillis[active] ?: 0L) + elapsed
        }
        lastUsageAccountedAt = now
        if (elapsed > 0L) {
            val json = org.json.JSONObject()
            dailyUsageMillis.forEach { (pkg, millis) -> json.put(pkg, millis) }
            getSharedPreferences(KeepAlive.PREFS, MODE_PRIVATE).edit()
                .putString("app_daily_usage_day", usageDay)
                .putString("app_daily_usage_json", json.toString())
                .apply()
        }
    }

    private fun isDailyLimitReached(prefs: android.content.SharedPreferences, appPackage: String): Boolean {
        val limits = runCatching {
            org.json.JSONObject(prefs.getString("app_daily_limits_json", "{}") ?: "{}")
        }.getOrNull() ?: return false
        val minutes = limits.optInt(appPackage, 0)
        return minutes > 0 && (dailyUsageMillis[appPackage] ?: 0L) >= minutes * 60_000L
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
        val start = (startParts.getOrNull(0)?.toIntOrNull() ?: 8) * 60 +
            (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val end = (endParts.getOrNull(0)?.toIntOrNull() ?: 18) * 60 +
            (endParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val today = now.dayOfWeek.value
        if (start == end) return today.toString() in days
        if (start < end) return today.toString() in days && minuteNow >= start && minuteNow < end
        val previousDay = if (today == 1) 7 else today - 1
        return (today.toString() in days && minuteNow >= start) ||
            (previousDay.toString() in days && minuteNow < end)
    }

    /* ── a telinha da pausa ───────────────────────────────────── */

    private fun showPauseScreen(blockedPackage: String, dailyLimitReached: Boolean) {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val label = appLabel(blockedPackage)
        if (overlay != null) {
            if (overlayPackage != blockedPackage) {
                hidePauseScreen()
            } else {
                overlayLabel?.text = messageFor(label, dailyLimitReached)
                return
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(AndroidColor.rgb(255, 253, 252))
            isClickable = true
            isFocusable = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(34), dp(28), dp(34), dp(28))
        }

        column.addView(text("୨୧", 20f, ROSE, center = true))
        column.addView(text("uma pausinha antes de continuar", 25f, INK, center = true, top = 10))
        column.addView(
            text(messageFor(label, dailyLimitReached), 13f, MUTED, center = true, top = 10)
                .also { overlayLabel = it }
        )
        column.addView(text("♡", 34f, ROSE, center = true, top = 18))
        column.addView(
            text("respira comigo...", 12f, ROSE_DEEP, center = true, top = 6)
                .also { overlayTimer = it }
        )

        val primary = actionButton("voltar para o meu dia", filled = true) {
            hidePauseScreen()
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(home) }
        }.also { overlayPrimary = it }
        column.addView(primary, buttonParams(top = 18))

        val escape = actionButton("continuar por 1 minutinho", filled = false) {
            registerEscape()
            temporarilyAllowedUntil = System.currentTimeMillis() + ESCAPE_WINDOW_MS
            hidePauseScreen()
        }.also { overlayEscape = it }
        column.addView(escape, buttonParams(top = 8))

        column.addView(
            text("se continuar, essa pausa volta em 1 minuto ♡", 10f, MUTED, center = true, top = 14)
        )

        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        val added = runCatching { windowManager?.addView(root, params) }.isSuccess
        if (!added) return
        overlay = root
        overlayPackage = blockedPackage
        startBreathingCountdown()
    }

    private fun messageFor(label: String, dailyLimitReached: Boolean): String = if (dailyLimitReached) {
        "o tempinho de hoje de «$label» já foi. amanhã ele volta cheio de novidades ♡"
    } else {
        "«$label» fica quietinho por enquanto. você escolheu esse horário para respirar."
    }

    private fun startBreathingCountdown() {
        countdownTask?.let { handler.removeCallbacks(it) }
        var remaining = breathingSeconds()
        setActionsEnabled(false)
        overlayTimer?.text = "respira comigo... ${remaining}s"
        val task = object : Runnable {
            override fun run() {
                remaining -= 1
                if (remaining <= 0) {
                    overlayTimer?.text = "prontinho ♡"
                    setActionsEnabled(true)
                    countdownTask = null
                    return
                }
                overlayTimer?.text = "respira comigo... ${remaining}s"
                handler.postDelayed(this, 1000)
            }
        }
        countdownTask = task
        handler.postDelayed(task, 1000)
    }

    private fun setActionsEnabled(enabled: Boolean) {
        listOf(overlayPrimary, overlayEscape).forEach { view ->
            view?.isEnabled = enabled
            view?.alpha = if (enabled) 1f else 0.45f
        }
    }

    /** quanto mais vezes fugir, mais tempo respirando (até 45 s) */
    private fun breathingSeconds(): Int {
        val prefs = getSharedPreferences(KeepAlive.PREFS, MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        val count = if (prefs.getString("escape_day", "") == today) prefs.getInt("escape_count", 0) else 0
        return (15 + count * 5).coerceAtMost(45)
    }

    private fun registerEscape() {
        val prefs = getSharedPreferences(KeepAlive.PREFS, MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        val count = if (prefs.getString("escape_day", "") == today) prefs.getInt("escape_count", 0) else 0
        prefs.edit()
            .putString("escape_day", today)
            .putInt("escape_count", count + 1)
            .apply()
    }

    private fun hidePauseScreen() {
        countdownTask?.let { handler.removeCallbacks(it) }
        countdownTask = null
        overlay?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlay = null
        overlayPackage = null
        overlayLabel = null
        overlayTimer = null
        overlayPrimary = null
        overlayEscape = null
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        center: Boolean = false,
        top: Int = 0
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (center) gravity = Gravity.CENTER
        setLineSpacing(0f, 1.25f)
        if (top > 0) setPadding(0, dp(top), 0, 0)
    }

    private fun actionButton(label: String, filled: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT
            setTextColor(if (filled) AndroidColor.WHITE else MUTED)
            background = if (filled) rounded(ROSE, 18) else roundedWithStroke(16)
            setPadding(dp(22), dp(13), dp(22), dp(13))
            isClickable = true
            setOnClickListener { if (isEnabled) onClick() }
        }

    private fun buttonParams(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = dp(top)
            gravity = Gravity.CENTER_HORIZONTAL
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun roundedWithStroke(radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(AndroidColor.rgb(255, 253, 252))
        setStroke(dp(1), AndroidColor.rgb(233, 220, 214))
        cornerRadius = dp(radius).toFloat()
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /* ── notificação ──────────────────────────────────────────── */

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pausa ativa",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "mantém a pausa viva enquanto você foca" }
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
            .setSmallIcon(R.drawable.ic_heart_notification)
            .setContentTitle("little pause")
            .setContentText("seu cantinho de foco está ativo ♡")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkForeground)
        hidePauseScreen()
        if (KeepAlive.isBlockingEnabled(this)) KeepAlive.scheduleWatchdog(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "little_pause_blocker"
        private const val NOTIFICATION_ID = 31
        private const val POLL_INTERVAL_MS = 600L
        private const val ESCAPE_WINDOW_MS = 60_000L
        const val ACTION_STOP = "com.littlepause.STOP_BLOCKING"

        private val ROSE = AndroidColor.rgb(193, 126, 147)
        private val ROSE_DEEP = AndroidColor.rgb(156, 95, 115)
        private val INK = AndroidColor.rgb(70, 53, 41)
        private val MUTED = AndroidColor.rgb(125, 107, 94)
    }
}
