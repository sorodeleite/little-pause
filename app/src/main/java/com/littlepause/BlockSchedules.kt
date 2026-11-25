package com.littlepause

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val ScheduleRose = Color(0xFFB87588)
private val ScheduleInk = Color(0xFF51494A)
private val ScheduleMuted = Color(0xFF9C9293)
private val SchedulePaper = Color(0xFFFFFDFC)
private val ScheduleBlush = Color(0xFFFFF1F4)
private val ScheduleDays = listOf("D", "S", "T", "Q", "Q", "S", "S")
private val ScheduleDayKeys = listOf("7", "1", "2", "3", "4", "5", "6")

data class BlockSchedule(
    val id: Long,
    val start: String,
    val end: String,
    val days: Set<String>,
    val packages: Set<String>,
    val enabled: Boolean = true
)
private data class ScheduleApp(val packageName: String, val label: String)

private fun readBlockSchedules(prefs: android.content.SharedPreferences): List<BlockSchedule> {
    val raw = runCatching { JSONArray(prefs.getString("block_schedules_json", "[]")) }.getOrNull() ?: JSONArray()
    if (raw.length() == 0) {
        val oldApps = prefs.getStringSet("blocked_apps", emptySet()).orEmpty()
        if (oldApps.isNotEmpty()) {
            val hadSchedule = prefs.getBoolean("schedule_enabled", false)
            val oldDays = if (hadSchedule) prefs.getStringSet("schedule_days", setOf("1", "2", "3", "4", "5")).orEmpty()
                else setOf("1", "2", "3", "4", "5", "6", "7")
            return listOf(
                BlockSchedule(
                    id = System.currentTimeMillis(),
                    start = if (hadSchedule) prefs.getString("block_start", "08:00") ?: "08:00" else "00:00",
                    end = if (hadSchedule) prefs.getString("block_end", "18:00") ?: "18:00" else "23:59",
                    days = oldDays,
                    packages = oldApps
                )
            )
        }
    }
    return (0 until raw.length()).mapNotNull { index ->
        val item = raw.optJSONObject(index) ?: return@mapNotNull null
        val dayArray = item.optJSONArray("days") ?: JSONArray()
        val appArray = item.optJSONArray("packages") ?: JSONArray()
        BlockSchedule(
            id = item.optLong("id", System.currentTimeMillis() + index),
            start = item.optString("start", "08:00"),
            end = item.optString("end", "18:00"),
            days = (0 until dayArray.length()).map { dayArray.optString(it) }.toSet(),
            packages = (0 until appArray.length()).map { appArray.optString(it) }.filter { it.isNotBlank() }.toSet(),
            enabled = item.optBoolean("enabled", true)
        )
    }
}

private fun writeBlockSchedules(prefs: android.content.SharedPreferences, schedules: List<BlockSchedule>) {
    val json = JSONArray()
    schedules.forEach { schedule ->
        val dayArray = JSONArray()
        schedule.days.sorted().forEach(dayArray::put)
        val appArray = JSONArray()
        schedule.packages.sorted().forEach(appArray::put)
        json.put(JSONObject().put("id", schedule.id).put("start", schedule.start).put("end", schedule.end)
            .put("days", dayArray).put("packages", appArray).put("enabled", schedule.enabled))
    }
    prefs.edit().putString("block_schedules_json", json.toString()).apply()
}

private fun scheduleApps(context: Context): List<ScheduleApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, 0).mapNotNull { info ->
        val pkg = info.activityInfo.packageName
        if (pkg == context.packageName) null else ScheduleApp(pkg, info.loadLabel(context.packageManager).toString())
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale("pt", "BR")) }
}

private fun scheduleHasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

@Composable
fun BlockSchedulesCard(isBlocking: Boolean, onToggleBlocking: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("little_pause", Context.MODE_PRIVATE) }
    val schedules = remember { mutableStateListOf<BlockSchedule>().apply { addAll(readBlockSchedules(prefs)) } }
    val apps = remember(context) { scheduleApps(context) }
    var showEditor by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var start by remember { mutableStateOf("08:00") }
    var end by remember { mutableStateOf("18:00") }
    var days by remember { mutableStateOf(setOf("1", "2", "3", "4", "5")) }
    var chosenPackages by remember { mutableStateOf(setOf<String>()) }
    var search by remember { mutableStateOf("") }
    var usageAllowed by remember { mutableStateOf(scheduleHasUsageAccess(context)) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    fun persist() { writeBlockSchedules(prefs, schedules) }
    val hasDailyLimits = runCatching { JSONArray(prefs.getString("app_daily_limits_json", "{}") ?: "{}").length() > 0 }.getOrDefault(false)
    fun openEditor(schedule: BlockSchedule?) {
        editingId = schedule?.id
        start = schedule?.start ?: "08:00"
        end = schedule?.end ?: "18:00"
        days = schedule?.days ?: setOf("1", "2", "3", "4", "5")
        chosenPackages = schedule?.packages ?: emptySet()
        search = ""
        showEditor = true
    }

    Card(colors = CardDefaults.cardColors(containerColor = SchedulePaper), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("horários de bloqueio  ♡", color = ScheduleInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("crie quantas pausas precisar, para apps diferentes", color = ScheduleMuted, fontSize = 11.sp)
                }
                TextButton(onClick = { openEditor(null) }) { Text("+ horário", color = ScheduleRose) }
            }
            if (schedules.isEmpty()) {
                Column(Modifier.fillMaxWidth().background(ScheduleBlush, RoundedCornerShape(7.dp)).padding(15.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("♡", color = ScheduleRose, fontSize = 22.sp)
                    Text("nenhum horário por aqui ainda", color = ScheduleInk, fontSize = 13.sp)
                    Text("adicione um período e escolha quais aplicativos entram na pausa", color = ScheduleMuted, fontSize = 10.sp)
                }
            } else {
                schedules.forEachIndexed { index, schedule ->
                    Column(Modifier.fillMaxWidth().border(0.8.dp, Color(0xFFEADBDD), RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFFEFD), RoundedCornerShape(8.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("pausa ${index + 1}  ·  ${schedule.start} — ${schedule.end}", color = ScheduleInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(schedule.days.sorted().joinToString(" · ") { key ->
                                    val i = ScheduleDayKeys.indexOf(key)
                                    listOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb").getOrElse(i) { key }
                                }, color = ScheduleMuted, fontSize = 10.sp)
                            }
                            Switch(checked = schedule.enabled, onCheckedChange = { enabled ->
                                val i = schedules.indexOfFirst { it.id == schedule.id }
                                if (i >= 0) schedules[i] = schedule.copy(enabled = enabled)
                                persist()
                            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ScheduleRose))
                        }
                        Text(schedule.packages.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg }?.label }.joinToString(" · ").ifBlank { "nenhum aplicativo selecionado" },
                            color = ScheduleMuted, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openEditor(schedule) }, contentPadding = PaddingValues(0.dp)) { Text("editar", color = ScheduleRose, fontSize = 11.sp) }
                            TextButton(onClick = { schedules.removeAll { it.id == schedule.id }; persist() }, contentPadding = PaddingValues(0.dp)) {
                                Text("remover", color = ScheduleMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("permissões do bloqueio", color = ScheduleInk, fontSize = 13.sp)
                    Text("uso: ${if (usageAllowed) "ok" else "precisa permitir"}  ·  sobrepor: ${if (overlayAllowed) "ok" else "precisa permitir"}",
                        color = ScheduleMuted, fontSize = 10.sp)
                }
                TextButton(onClick = {
                    usageAllowed = scheduleHasUsageAccess(context)
                    overlayAllowed = Settings.canDrawOverlays(context)
                    if (!usageAllowed) context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    else if (!overlayAllowed) context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }) { Text("verificar", color = ScheduleRose, fontSize = 11.sp) }
            }
            Button(
                onClick = {
                    usageAllowed = scheduleHasUsageAccess(context)
                    overlayAllowed = Settings.canDrawOverlays(context)
                    if (!usageAllowed) context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    else if (!overlayAllowed) context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    else onToggleBlocking(!isBlocking)
                },
                enabled = isBlocking || schedules.any { it.enabled && it.packages.isNotEmpty() } || hasDailyLimits,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isBlocking) Color(0xFFB99DA7) else ScheduleRose)
            ) { Text(if (isBlocking) "desativar todos os bloqueios" else "ativar bloqueios", color = Color.White) }
            Text(if (isBlocking) "seus horários e limites diários estão sendo monitorados ♡" else "ative para monitorar horários e limites diários",
                color = ScheduleMuted, fontSize = 10.sp)
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingId == null) "novo horário de pausa  ♡" else "editar horário  ♡", color = ScheduleInk) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("dias da semana", color = ScheduleInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ScheduleDays.forEachIndexed { index, label ->
                            val key = ScheduleDayKeys[index]
                            val active = key in days
                            Box(Modifier.weight(1f).background(if (active) ScheduleBlush else Color(0xFFF5F3F3), CircleShape)
                                .clickable { days = if (active) days - key else days + key }.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center) {
                                Text(label, color = if (active) ScheduleRose else ScheduleMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("começa às", color = ScheduleMuted, fontSize = 11.sp)
                            OutlinedTextField(value = start, onValueChange = { start = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                                singleLine = true, placeholder = { Text("08:00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.weight(1f)) {
                            Text("termina às", color = ScheduleMuted, fontSize = 11.sp)
                            OutlinedTextField(value = end, onValueChange = { end = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                                singleLine = true, placeholder = { Text("18:00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Text("aplicativos que serão bloqueados", color = ScheduleInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, placeholder = { Text("pesquisar aplicativo pelo nome...") })
                    Text("${chosenPackages.size} aplicativo(s) selecionado(s)", color = ScheduleRose, fontSize = 10.sp)
                    val filtered = apps.filter { it.label.contains(search.trim(), ignoreCase = true) || it.packageName.contains(search.trim(), ignoreCase = true) }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 205.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(Modifier.fillMaxWidth().clickable {
                                chosenPackages = if (app.packageName in chosenPackages) chosenPackages - app.packageName else chosenPackages + app.packageName
                            }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = app.packageName in chosenPackages, onCheckedChange = { checked ->
                                    chosenPackages = if (checked) chosenPackages + app.packageName else chosenPackages - app.packageName
                                }, colors = CheckboxDefaults.colors(checkedColor = ScheduleRose))
                                Text(app.label, color = ScheduleInk, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val validTime = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
                    if (days.isNotEmpty() && chosenPackages.isNotEmpty() && validTime.matches(start) && validTime.matches(end) && start != end) {
                        val item = BlockSchedule(editingId ?: System.currentTimeMillis(), start, end, days, chosenPackages, true)
                        val index = schedules.indexOfFirst { it.id == item.id }
                        if (index >= 0) schedules[index] = item else schedules.add(item)
                        persist()
                        showEditor = false
                    }
                }) { Text("salvar horário", color = ScheduleRose) }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("cancelar", color = ScheduleMuted) } },
            containerColor = SchedulePaper
        )
    }
}
