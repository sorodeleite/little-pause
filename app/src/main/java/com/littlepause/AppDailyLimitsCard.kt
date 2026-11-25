package com.littlepause

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private data class LimitApp(val pkg: String, val label: String)

@Composable
fun AppDailyLimitsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("little_pause", Context.MODE_PRIVATE) }
    val apps = remember(context) {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .mapNotNull { info -> val p = info.activityInfo.packageName; if (p == context.packageName) null else LimitApp(p, info.loadLabel(context.packageManager).toString()) }
            .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }
    fun read(): Map<String, Int> {
        val j = runCatching { JSONObject(prefs.getString("app_daily_limits_json", "{}") ?: "{}") }.getOrNull() ?: JSONObject()
        val out = mutableMapOf<String, Int>(); val keys = j.keys()
        while (keys.hasNext()) { val k = keys.next(); val v = j.optInt(k); if (v in 1..1440) out[k] = v }
        return out
    }
    var limits by remember { mutableStateOf(read()) }
    var picker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LimitApp?>(null) }
    var minutes by remember { mutableStateOf("60") }
    fun save(next: Map<String, Int>) { limits = next; val j = JSONObject(); next.forEach { (k,v) -> j.put(k,v) }; prefs.edit().putString("app_daily_limits_json", j.toString()).apply() }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("limites diários por app ♡", fontSize = 16.sp, color = Color(0xFF51494A))
                    Text("tempo acumulado em todas as sessões", fontSize = 11.sp, color = Color(0xFF9C9293))
                }
                TextButton(onClick = { picker = true; query = "" }) { Text("+ adicionar", color = Color(0xFFB87588)) }
            }
            if (limits.isEmpty()) Text("nenhum limite configurado ainda ♡", color = Color.Gray, fontSize = 12.sp)
            limits.forEach { (pkg, value) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text((apps.firstOrNull { it.pkg == pkg }?.label ?: pkg) + " · " + value + " min/dia", Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = { selected = apps.firstOrNull { it.pkg == pkg }; minutes = value.toString() }) { Text("editar") }
                    TextButton(onClick = { save(limits - pkg) }) { Text("×") }
                }
            }
            Text("ao atingir o limite, o app fica bloqueado até o próximo dia ♡", color = Color.Gray, fontSize = 10.sp)
        }
    }
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text("escolha um aplicativo ♡") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, placeholder = { Text("pesquisar aplicativo...") })
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }, key = { it.pkg }) { app ->
                    Row(Modifier.fillMaxWidth().clickable { selected = app; minutes = (limits[app.pkg] ?: 60).toString(); picker = false }.padding(8.dp)) { Text(app.label) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { picker = false }) { Text("fechar") } })
    selected?.let { app ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(app.label) }, text = {
            Column {
                Text("limite diário acumulado")
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("minutos por dia") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }, confirmButton = { TextButton(onClick = { val n = minutes.toIntOrNull(); if (n != null && n in 1..1440) save(limits + (app.pkg to n)); selected = null }) { Text("salvar") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("cancelar") } })
    }
}
