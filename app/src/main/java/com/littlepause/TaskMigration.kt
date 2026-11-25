package com.littlepause

import android.content.Context
import org.json.JSONObject

/**
 * As tarefinhas do modelo antigo ficavam em SharedPreferences ("tasks_2026-11-25").
 * Agora elas vivem no calendário; na primeira abertura, entregamos esse pacote
 * para a página importar sem perder nada.
 */
internal fun legacyTasksJson(context: Context): String? {
    val prefs = context.getSharedPreferences(KeepAlive.PREFS, Context.MODE_PRIVATE)
    val out = JSONObject()
    prefs.all.forEach { (key, value) ->
        if (!key.startsWith("tasks_")) return@forEach
        val date = key.removePrefix("tasks_")
        if (date.length != 10) return@forEach
        val items = (value as? Set<*>)?.mapNotNull { raw ->
            val parts = raw.toString().split("|", limit = 3)
            if (parts.size == 3) parts[2].trim() else null
        }?.filter { it.isNotEmpty() } ?: return@forEach
        if (items.isNotEmpty()) out.put(date, items.joinToString("\n"))
    }
    return if (out.length() > 0) out.toString() else null
}
