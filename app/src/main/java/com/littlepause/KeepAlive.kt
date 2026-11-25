package com.littlepause

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * O Android é carinhoso, mas adora fechar as asinhas dos apps que ficam de
 * vigia. Aqui a gente pede licença (sem exageros) e deixa um lembrete de
 * tempos em tempos para o bloqueio voltar a respirar sozinho.
 */
object KeepAlive {
    const val PREFS = "little_pause"
    private const val KEY_BLOCKING = "blocking_enabled"
    private const val WATCHDOG_ACTION = "com.littlepause.WATCHDOG"
    private const val WATCHDOG_REQUEST = 4100
    private const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L

    fun isBlockingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCKING, false)

    fun setBlockingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLOCKING, enabled).apply()
        if (enabled) start(context) else stop(context)
    }

    fun start(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AppBlockingService::class.java)
            )
        }
        scheduleWatchdog(context)
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, AppBlockingService::class.java)) }
        cancelWatchdog(context)
    }

    fun scheduleWatchdog(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            alarm.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                WATCHDOG_INTERVAL_MS,
                watchdogIntent(context)
            )
        }
    }

    fun cancelWatchdog(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarm.cancel(watchdogIntent(context)) }
    }

    private fun watchdogIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        WATCHDOG_REQUEST,
        Intent(context, WatchdogReceiver::class.java).setAction(WATCHDOG_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** A tela certa para cada versão do Android: sem pedir nada absurdo. */
    fun batteryIntent(context: Context): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    fun openBatterySettings(context: Context) {
        val intent = batteryIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Volta a vigiar depois que o celular reinicia ou o app se atualiza. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                if (KeepAlive.isBlockingEnabled(context)) KeepAlive.start(context)
                else KeepAlive.cancelWatchdog(context)
            }
        }
    }
}

/** O alarme que reacende o bloqueio se o Android apagar a chama. */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!KeepAlive.isBlockingEnabled(context)) {
            KeepAlive.cancelWatchdog(context)
            return
        }
        KeepAlive.start(context)
    }
}
