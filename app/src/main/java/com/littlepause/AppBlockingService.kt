package com.littlepause

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

class AppBlockingService : AccessibilityService() {
    private var blocker: LinearLayout? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val openedPackage = event.packageName?.toString() ?: return
        if (openedPackage == packageName) {
            removeBlocker()
            return
        }

        val preferences = getSharedPreferences("little_pause", MODE_PRIVATE)
        val blockedPackages = preferences.getStringSet("blocked_packages", emptySet()).orEmpty()
        if (openedPackage !in blockedPackages || !isPauseTime(preferences)) {
            removeBlocker()
            return
        }

        showBlocker(preferences.getInt("end_hour", 11), preferences.getInt("end_minute", 0))
    }

    private fun isPauseTime(preferences: android.content.SharedPreferences): Boolean {
        val start = preferences.getInt("start_hour", 9).coerceIn(0, 23) * 60 +
            preferences.getInt("start_minute", 0).coerceIn(0, 59)
        val end = preferences.getInt("end_hour", 11).coerceIn(0, 23) * 60 +
            preferences.getInt("end_minute", 0).coerceIn(0, 59)
        if (start == end) return false

        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (start < end) current >= start && current < end else current >= start || current < end
    }

    private fun showBlocker(endHour: Int, endMinute: Int) {
        if (blocker != null) return

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 60, 48, 60)
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 248, 245))
                cornerRadius = 64f
            }
        }

        card.addView(TextView(this).apply {
            text = "♡"
            textSize = 48f
            setTextColor(Color.rgb(184, 117, 141))
            gravity = Gravity.CENTER
        })
        card.addView(TextView(this).apply {
            text = "um intervalinho para você"
            textSize = 23f
            setTextColor(Color.rgb(112, 90, 97))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 10)
        })
        card.addView(TextView(this).apply {
            text = "esse aplicativo fica em pausa por enquanto.\nvolte depois das ${endHour.toString().padStart(2, '0')}:${endMinute.toString().padStart(2, '0')}  ♡"
            textSize = 15f
            setTextColor(Color.rgb(184, 117, 141))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })
        card.addView(Button(this).apply {
            text = "voltar para o meu dia"
            setOnClickListener {
                removeBlocker()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(card, params)
            blocker = card
        } catch (_: Exception) {
            blocker = null
        }
    }

    private fun removeBlocker() {
        blocker?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) {
            }
        }
        blocker = null
    }

    override fun onInterrupt() {
        removeBlocker()
    }

    override fun onDestroy() {
        removeBlocker()
        super.onDestroy()
    }
}
