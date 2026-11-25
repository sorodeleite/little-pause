package com.littlepause

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray

@Composable
internal fun HomeScreen(
    prefs: android.content.SharedPreferences,
    onOpenCalendar: () -> Unit,
    onOpenPause: () -> Unit
) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(loadReminders(prefs)) }
    var showEditor by remember { mutableStateOf(false) }
    var reminderTitle by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf("21:00") }
    val blockingEnabled = remember { KeepAlive.isBlockingEnabled(context) }
    val activeSchedules = remember { countActiveSchedules(prefs) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("00.  ( sweetest pause )  ˚♡", color = Muted, fontSize = 11.sp)
                Text("little pause", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Normal)
            }
            Box(
                modifier = Modifier.size(44.dp).background(Blush, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("୨୧", color = RoseDeep, fontSize = 22.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                SoftCard(container = Sand) {
                    Text("um cantinho para respirar  ✧", color = Ink, fontSize = 15.sp)
                    Text(
                        "o little pause segura os apps que você escolheu, só nos horários que você escolheu. " +
                            "sem sermão, sem contagem regressiva na cara.",
                        color = Muted, fontSize = 11.sp
                    )
                    Box(
                        Modifier.fillMaxWidth().height(74.dp)
                            .background(Paper, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("୨୧       ✧       ୨୧", color = Rose, fontSize = 20.sp)
                            Text("slow down, you're doing fine", color = Muted, fontSize = 10.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("♡ pausa", Modifier.weight(1f), active = blockingEnabled)
                        Pill("☁ calendário", Modifier.weight(1f), onClick = onOpenCalendar)
                        Pill("✧ horários", Modifier.weight(1f), onClick = onOpenPause)
                    }
                }
            }

            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                if (blockingEnabled) "a pausa está de pé ♡" else "a pausa está descansando",
                                color = Ink, fontSize = 16.sp
                            )
                            Text(
                                if (blockingEnabled)
                                    "$activeSchedules horário(s) ativo(s) · seus apps entram na pausa na hora combinada"
                                else "quando quiser, ligue um horário e o app começa a cuidar de você",
                                color = Muted, fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = blockingEnabled,
                            onCheckedChange = { onOpenPause() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Paper, checkedTrackColor = Rose)
                        )
                    }
                    TextButton(onClick = onOpenPause, contentPadding = PaddingValues(0.dp)) {
                        Text("abrir as pausas →", color = RoseDeep, fontSize = 12.sp)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionHeader(
                        title = "little reminders  ♡",
                        detail = "recadinhos para acompanhar seu dia",
                        action = {
                            TextButton(onClick = { showEditor = true }) {
                                Text("+ novo", color = RoseDeep, fontSize = 12.sp)
                            }
                        }
                    )
                    if (reminders.isEmpty()) {
                        SoftCard {
                            Text("nenhum recadinho ainda", color = Ink, fontSize = 13.sp)
                            Text("adicione uma mensagem, uma data e um horário ♡", color = Muted, fontSize = 11.sp)
                        }
                    } else {
                        reminders.forEach { reminder ->
                            SoftCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(reminder.title, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = reminder.enabled,
                                        onCheckedChange = { enabled ->
                                            val updated = reminder.copy(enabled = enabled)
                                            reminders = reminders.map { if (it.id == reminder.id) updated else it }
                                            saveReminders(prefs, reminders)
                                            scheduleReminder(context, updated)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Paper, checkedTrackColor = Rose)
                                    )
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        reminder.time, color = Ink, fontSize = 30.sp,
                                        fontWeight = FontWeight.Light, modifier = Modifier.weight(1f)
                                    )
                                    Text(prettyDate(reminder.date), color = Muted, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (reminder.enabled) "lembrete ativado ♡" else "pausado",
                                        color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        cancelReminder(context, reminder.id)
                                        reminders = reminders.filterNot { it.id == reminder.id }
                                        saveReminders(prefs, reminders)
                                    }) { Text("remover", color = Muted, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
            }

            item { UpdateCard() }

            item {
                Text(
                    "feito com carinho, para os seus dias ♡",
                    color = Muted, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            containerColor = Paper,
            shape = RoundedCornerShape(24.dp),
            title = { Text("um recadinho para você ♡", color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reminderTitle,
                        onValueChange = { reminderTitle = it },
                        placeholder = { Text("ex.: hora de se arrumar") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reminderTime,
                        onValueChange = { reminderTime = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                        label = { Text("horário (HH:mm)") },
                        singleLine = true
                    )
                    Text("hoje: ${todayLabel()}", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val title = reminderTitle.trim()
                    val valid = reminderTime.matches(Regex("([01]?[0-9]|2[0-3]):[0-5][0-9]"))
                    if (title.isNotEmpty() && valid) {
                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val item = PauseReminder(
                            id = System.currentTimeMillis(),
                            title = title,
                            date = java.time.LocalDate.now().toString(),
                            time = reminderTime.padStart(5, '0'),
                            enabled = true
                        )
                        reminders = reminders + item
                        saveReminders(prefs, reminders)
                        scheduleReminder(context, item)
                        reminderTitle = ""
                        reminderTime = "21:00"
                        showEditor = false
                    }
                }) { Text("salvar", color = RoseDeep) }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("cancelar", color = Muted) }
            }
        )
    }
}

private fun countActiveSchedules(prefs: android.content.SharedPreferences): Int = runCatching {
    val array = JSONArray(prefs.getString("block_schedules_json", "[]"))
    (0 until array.length()).count { index ->
        val item = array.optJSONObject(index)
        item != null && item.optBoolean("enabled", true) &&
            (item.optJSONArray("packages")?.length() ?: 0) > 0
    }
}.getOrDefault(0)

private fun prettyDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso)
        .format(java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM", java.util.Locale("pt", "BR")))
}.getOrDefault(iso)
