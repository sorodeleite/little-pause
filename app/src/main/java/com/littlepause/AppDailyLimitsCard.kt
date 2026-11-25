package com.littlepause

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private data class LimitApp(val pkg: String, val label: String)

@Composable
fun AppDailyLimitsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(KeepAlive.PREFS, Context.MODE_PRIVATE) }
    val apps = remember(context) {
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) null
                else LimitApp(pkg, info.loadLabel(context.packageManager).toString())
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
    fun read(): Map<String, Int> {
        val json = runCatching {
            JSONObject(prefs.getString("app_daily_limits_json", "{}") ?: "{}")
        }.getOrNull() ?: JSONObject()
        val out = mutableMapOf<String, Int>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optInt(key)
            if (value in 1..1440) out[key] = value
        }
        return out
    }
    var limits by remember { mutableStateOf(read()) }
    var picker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LimitApp?>(null) }
    var minutes by remember { mutableStateOf("60") }
    fun save(next: Map<String, Int>) {
        limits = next
        val json = JSONObject()
        next.forEach { (key, value) -> json.put(key, value) }
        prefs.edit().putString("app_daily_limits_json", json.toString()).apply()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(0.7.dp, Line)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("limites diários por app ♡", fontSize = 16.sp, color = Ink)
                    Text("tempo acumulado em todas as sessões do dia", fontSize = 11.sp, color = Muted)
                }
                TextButton(onClick = { picker = true; query = "" }) {
                    Text("+ adicionar", color = RoseDeep, fontSize = 12.sp)
                }
            }
            if (limits.isEmpty()) {
                Text("nenhum limite configurado ainda ♡", color = Muted, fontSize = 12.sp)
            }
            limits.forEach { (pkg, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (apps.firstOrNull { it.pkg == pkg }?.label ?: pkg) + " · " + value + " min/dia",
                        Modifier.weight(1f), fontSize = 12.sp, color = Ink
                    )
                    TextButton(onClick = {
                        selected = apps.firstOrNull { it.pkg == pkg }
                        minutes = value.toString()
                    }) { Text("editar", color = RoseDeep, fontSize = 11.sp) }
                    TextButton(onClick = { save(limits - pkg) }) {
                        Text("×", color = Muted, fontSize = 14.sp)
                    }
                }
            }
            Text("ao atingir o limite, o app entra na pausa até o próximo dia ♡", color = Muted, fontSize = 10.sp)
        }
    }

    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            containerColor = Paper,
            shape = RoundedCornerShape(24.dp),
            title = { Text("escolha um aplicativo ♡", color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("pesquisar aplicativo...") },
                        singleLine = true
                    )
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(
                            apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) },
                            key = { it.pkg }
                        ) { app ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        selected = app
                                        minutes = (limits[app.pkg] ?: 60).toString()
                                        picker = false
                                    }
                                    .padding(8.dp)
                            ) { Text(app.label, color = Ink, fontSize = 13.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker = false }) { Text("fechar", color = Muted) }
            }
        )
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Paper,
            shape = RoundedCornerShape(24.dp),
            title = { Text(app.label, color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("limite diário acumulado", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                        label = { Text("minutos por dia") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = minutes.toIntOrNull()
                    if (value != null && value in 1..1440) save(limits + (app.pkg to value))
                    selected = null
                }) { Text("salvar", color = RoseDeep) }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("cancelar", color = Muted) }
            }
        )
    }
}
