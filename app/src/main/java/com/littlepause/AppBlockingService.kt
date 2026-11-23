package com.littlepause

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

class AppBlockingService : AccessibilityService() {
    private var blocker: LinearLayout? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        if (packageName != "com.twitter.android") {
            removeBlocker()
            return
        }

        val prefs = getSharedPreferences("little_pause", MODE_PRIVATE)
        if (!prefs.getBoolean("block_x", true) || !isQuietTime()) {
            removeBlocker()
            return
        }
        showBlocker()
    }

    private fun isQuietTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 9 && hour < 11
    }

    private fun showBlocker() {
        if (blocker != null) return

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(42, 54, 42, 54)
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 248, 245))
                cornerRadius = 52f
            }
        }

        card.addView(TextView(this).apply {
            text = "♡"
            textSize = 46f
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
            text = "o X fica quietinho até às 11h.\nque tal aproveitar esse tempinho?  ♡"
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(card, params)
            blocker = card
        } catch (_: Exception) {
            blocker = null
        }
    }

    private fun removeBlocker() {
        blocker?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        blocker = null
    }

    override fun onInterrupt() = removeBlocker()

    override fun onDestroy() {
        removeBlocker()
        super.onDestroy()
    }
}
