package com.littlepause

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Cream = Color(0xFFF7F8FA)
private val Paper = Color(0xFFFFFFFF)
private val Blush = Color(0xFFF4EDF2)
private val Rose = Color(0xFF9B8796)
private val Ink = Color(0xFF29272B)
private val Lilac = Color(0xFFEEF2F7)
private val Muted = Color(0xFF9A9AA3)
private val Mist = Color(0xFFEAF0F7)
private val PtBr = Locale("pt", "BR")
private data class PlannerTask(val id: Long, val title: String, val done: Boolean)
private data class AppChoice(val packageName: String, val label: String)
private data class PauseReminder(val id: Long, val title: String, val date: String, val time: String, val enabled: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Rose,
                    onPrimary = Color.White,
                    secondary = Ink,
                    background = Cream,
                    surface = Paper,
                    onSurface = Ink
                )
            ) {
                Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
                    PlannerHome()
                }
            }
        }
    }

}

@Composable
private fun PlannerHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("little_pause", Context.MODE_PRIVATE) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showTaskEditor by remember { mutableStateOf(false) }
    var newTask by remember { mutableStateOf("") }
    var reminders by remember { mutableStateOf(loadReminders(prefs)) }
    var showReminderEditor by remember { mutableStateOf(false) }
    var reminderTitle by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf("09:00") }
    var dailyReportEnabled by remember { mutableStateOf(prefs.getBoolean("daily_usage_report_enabled", true)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var showAppPicker by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember {
        mutableStateOf(prefs.getStringSet("blocked_apps", emptySet()).orEmpty())
    }
    var usageAllowed by remember { mutableStateOf(hasUsageAccess(context)) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isBlocking by remember { mutableStateOf(prefs.getBoolean("blocking_enabled", false)) }
    var scheduleEnabled by remember { mutableStateOf(prefs.getBoolean("schedule_enabled", false)) }
    var scheduleDays by remember { mutableStateOf(prefs.getStringSet("schedule_days", setOf("1","2","3","4","5")).orEmpty()) }
    var blockStart by remember { mutableStateOf(prefs.getString("block_start", "08:00") ?: "08:00") }
    var blockEnd by remember { mutableStateOf(prefs.getString("block_end", "18:00") ?: "18:00") }
    val installedApps = remember(context) { loadLaunchableApps(context) }
    LaunchedEffect(dailyReportEnabled) {
        prefs.edit().putBoolean("daily_usage_report_enabled", dailyReportEnabled).apply()
        if (dailyReportEnabled) scheduleDailyUsageReport(context)
        else cancelDailyUsageReport(context)
    }

    val tasks = remember(selectedDate) {
        loadTasks(prefs, selectedDate)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Cream).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("00.  ( sweetest pause )  ˚♡", color = Muted, fontSize = 12.sp)
                Text("little pause", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Normal)
            }
            Box(
                modifier = Modifier.size(46.dp).background(Mist, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("୨୧", color = Rose, fontSize = 24.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("soft days  †  little moments", color = Ink, fontSize = 15.sp)
                        Box(Modifier.fillMaxWidth().height(104.dp).background(Mist, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("୨୧       ✧       ୨୧", color = Muted, fontSize = 25.sp)
                                Text("a little space to breathe", color = Ink, fontSize = 14.sp)
                                Text("♡  slow down, you're doing fine  ♡", color = Muted, fontSize = 10.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("♡ gentle", "✧ dreamy", "☁ calm").forEachIndexed { index, label ->
                                Box(Modifier.weight(1f).background(if (index == 1) Blush else Cream, RoundedCornerShape(8.dp)).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                    Text(label, color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                CalendarCard(
                    month = month,
                    selectedDate = selectedDate,
                    onPrevious = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    onSelectDate = { selectedDate = it }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard(
                        title = "seu dia",
                        detail = selectedDate.format(DateTimeFormatter.ofPattern("d 'de' MMMM", PtBr)),
                        modifier = Modifier.weight(1f),
                        color = Blush
                    )
                    SummaryCard(
                        title = "um passo de cada vez",
                        detail = "sem pressa, no seu ritmo",
                        modifier = Modifier.weight(1f),
                        color = Lilac
                    )
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("horários de bloqueio  ♡", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                Text("escolha quando sua pausa começa e termina", color = Muted, fontSize = 11.sp)
                            }
                            Switch(
                                checked = scheduleEnabled,
                                onCheckedChange = {
                                    scheduleEnabled = it
                                    prefs.edit().putBoolean("schedule_enabled", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Paper, checkedTrackColor = Rose)
                            )
                        }
                        Text("dias da semana", color = Ink, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("D","S","T","Q","Q","S","S").forEachIndexed { index, day ->
                                val key = (if (index == 0) 7 else index).toString()
                                val active = key in scheduleDays
                                Box(
                                    Modifier.weight(1f).background(if (active) Blush else Cream, CircleShape)
                                        .clickable {
                                            val next = scheduleDays.toMutableSet()
                                            if (active) next.remove(key) else next.add(key)
                                            scheduleDays = next
                                            prefs.edit().putStringSet("schedule_days", next).apply()
                                        }.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(day, color = if (active) Rose else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeField("começa às", blockStart.substringBefore(":"),
                                { v -> blockStart = "$v:${blockStart.substringAfter(":", "00")}"; prefs.edit().putString("block_start", blockStart).apply() },
                                blockStart.substringAfter(":", "00"),
                                { v -> blockStart = "${blockStart.substringBefore(":")}:$v"; prefs.edit().putString("block_start", blockStart).apply() },
                                Modifier.weight(1f))
                            TimeField("termina às", blockEnd.substringBefore(":"),
                                { v -> blockEnd = "$v:${blockEnd.substringAfter(":", "00")}"; prefs.edit().putString("block_end", blockEnd).apply() },
                                blockEnd.substringAfter(":", "00"),
                                { v -> blockEnd = "${blockEnd.substringBefore(":")}:$v"; prefs.edit().putString("block_end", blockEnd).apply() },
                                Modifier.weight(1f))
                        }
                        Text(
                            if (scheduleEnabled) "seu bloqueio seguirá esses dias e horários ♡" else "ative para limitar o bloqueio aos horários escolhidos",
                            color = Muted, fontSize = 11.sp
                        )
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Paper),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("seu cantinho de foco  ♡", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("sem Acessibilidade: usando o acesso ao uso do Android", color = Muted, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Acesso ao uso", color = Ink, fontSize = 13.sp)
                                Text(if (usageAllowed) "autorizado ♡" else "necessário para identificar o app aberto", color = Muted, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }) { Text(if (usageAllowed) "abrir" else "permitir", color = Rose) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Aparecer sobre outros apps", color = Ink, fontSize = 13.sp)
                                Text(if (overlayAllowed) "autorizado ♡" else "necessário para mostrar a pausa", color = Muted, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                )
                            }) { Text(if (overlayAllowed) "abrir" else "permitir", color = Rose) }
                        }
                        OutlinedButton(
                            onClick = { showAppPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("escolher aplicativos  ·  ${selectedPackages.size} selecionados", color = Rose)
                        }
                        OutlinedButton(
                            onClick = {
                                usageAllowed = hasUsageAccess(context)
                                overlayAllowed = Settings.canDrawOverlays(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("atualizar permissões", color = Muted) }
                        Button(
                            onClick = {
                                if (usageAllowed && overlayAllowed && selectedPackages.isNotEmpty()) {
                                    prefs.edit().putBoolean("blocking_enabled", true).apply()
                                    context.startForegroundService(Intent(context, AppBlockingService::class.java))
                                    isBlocking = true
                                }
                            },
                            enabled = usageAllowed && overlayAllowed && selectedPackages.isNotEmpty() && !isBlocking,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Rose)
                        ) { Text("ativar bloqueio") }
                        if (isBlocking) {
                            TextButton(
                                onClick = {
                                    prefs.edit().putBoolean("blocking_enabled", false).apply()
                                    context.stopService(Intent(context, AppBlockingService::class.java))
                                    isBlocking = false
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) { Text("desativar bloqueio", color = Rose) }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("seu resumo das 22h  ♡", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("um lembrete gentil sobre seu tempo de tela", color = Muted, fontSize = 11.sp)
                            }
                            Switch(
                                checked = dailyReportEnabled,
                                onCheckedChange = { enabled ->
                                    dailyReportEnabled = enabled
                                    if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Paper, checkedTrackColor = Rose)
                            )
                        }
                        Text("Todos os dias, às 22h, você recebe seu tempo total no celular e uma comparação com filmes de 1h40.", color = Muted, fontSize = 11.sp)
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            TextButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("permitir notificações", color = Rose)
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("little reminders  ♡", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text("recadinhos para acompanhar seu dia", color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { showReminderEditor = true }) { Text("+ novo", color = Rose) }
                    }
                    if (reminders.isEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("♡", color = Rose, fontSize = 23.sp)
                                Text("seus recadinhos aparecem aqui", color = Ink, fontSize = 13.sp)
                                Text("adicione uma mensagem, data e horário", color = Muted, fontSize = 11.sp)
                            }
                        }
                    } else {
                        reminders.forEach { reminder ->
                            Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(22.dp)) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("♪  : ${reminder.title}", color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                        Switch(checked = reminder.enabled, onCheckedChange = { enabled ->
                                            val updated = reminder.copy(enabled = enabled)
                                            reminders = reminders.map { if (it.id == reminder.id) updated else it }
                                            saveReminders(prefs, reminders)
                                            scheduleReminder(context, updated)
                                        }, colors = SwitchDefaults.colors(checkedThumbColor = Paper, checkedTrackColor = Rose))
                                    }
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(reminder.time, color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                                        Text(runCatching { LocalDate.parse(reminder.date).format(DateTimeFormatter.ofPattern("EEE, dd MMM", PtBr)) }.getOrDefault(reminder.date), color = Muted, fontSize = 12.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (reminder.enabled) "lembrete ativado ♡" else "pausado", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
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
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Paper),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("pequenas tarefas  ♡", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text("um pouquinho de cada vez", color = Muted, fontSize = 12.sp)
                            }
                            TextButton(onClick = { showTaskEditor = true }) {
                                Text("+ adicionar", color = Rose)
                            }
                        }

                        if (tasks.isEmpty()) {
                            Text("seu dia ainda está em branco. que tal colocar uma coisinha aqui?", color = Muted, fontSize = 13.sp)
                        } else {
                            tasks.forEach { task ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = task.done,
                                        onCheckedChange = { checked ->
                                            updateTask(prefs, selectedDate, tasks, task.copy(done = checked))
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Rose)
                                    )
                                    Text(
                                        task.title,
                                        color = if (task.done) Muted else Ink,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { deleteTask(prefs, selectedDate, tasks, task.id) }) {
                                        Text("×", color = Muted, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                UpdateCard()
            }
            item {
                Text(
                    "feito com carinho, para os seus dias ♡",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false; appSearchQuery = "" },
            title = { Text("quais apps entram na pausa? ♡", color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("digite o nome do aplicativo...") },
                        leadingIcon = { Text("⌕", color = Muted, fontSize = 20.sp) },
                        shape = RoundedCornerShape(14.dp)
                    )
                    val filteredApps = installedApps.filter {
                        it.label.contains(appSearchQuery.trim(), ignoreCase = true) ||
                            it.packageName.contains(appSearchQuery.trim(), ignoreCase = true)
                    }
                    if (filteredApps.isEmpty()) {
                        Text("nenhum aplicativo encontrado ♡", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val next = selectedPackages.toMutableSet()
                                if (app.packageName in next) next.remove(app.packageName) else next.add(app.packageName)
                                selectedPackages = next
                                prefs.edit().putStringSet("blocked_apps", next).apply()
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.packageName in selectedPackages,
                                onCheckedChange = { checked ->
                                    val next = selectedPackages.toMutableSet()
                                    if (checked) next.add(app.packageName) else next.remove(app.packageName)
                                    selectedPackages = next
                                    prefs.edit().putStringSet("blocked_apps", next).apply()
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Rose)
                            )
                            Text(app.label, color = Ink, fontSize = 14.sp)
                        }
                    }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false; appSearchQuery = "" }) { Text("prontinho", color = Rose) }
            },
            containerColor = Cream
        )
    }

    if (showReminderEditor) {
        AlertDialog(
            onDismissRequest = { showReminderEditor = false },
            title = { Text("um recadinho para você ♡", color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = reminderTitle, onValueChange = { reminderTitle = it }, placeholder = { Text("ex.: hora de se arrumar") }, singleLine = true)
                    OutlinedTextField(value = reminderTime, onValueChange = { reminderTime = it.filter { c -> c.isDigit() || c == ':' }.take(5) }, label = { Text("horário (HH:mm)") }, singleLine = true)
                    Text("data: ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val title = reminderTitle.trim()
                    val valid = reminderTime.matches(Regex("([01]?[0-9]|2[0-3]):[0-5][0-9]"))
                    if (title.isNotEmpty() && valid) {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val time = reminderTime.padStart(5, '0')
                        val item = PauseReminder(System.currentTimeMillis(), title, selectedDate.toString(), time, true)
                        reminders = reminders + item
                        saveReminders(prefs, reminders)
                        scheduleReminder(context, item)
                        reminderTitle = ""
                        reminderTime = "09:00"
                        showReminderEditor = false
                    }
                }) { Text("salvar", color = Rose) }
            },
            dismissButton = { TextButton(onClick = { showReminderEditor = false }) { Text("cancelar", color = Muted) } },
            containerColor = Cream
        )
    }

    if (showTaskEditor) {
        AlertDialog(
            onDismissRequest = { showTaskEditor = false },
            title = { Text("uma coisinha para fazer ♡", color = Ink) },
            text = {
                OutlinedTextField(
                    value = newTask,
                    onValueChange = { newTask = it },
                    placeholder = { Text("escreva sua tarefa") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val title = newTask.trim()
                    if (title.isNotEmpty()) {
                        addTask(prefs, selectedDate, tasks, title)
                        newTask = ""
                        showTaskEditor = false
                    }
                }) { Text("adicionar", color = Rose) }
            },
            dismissButton = {
                TextButton(onClick = { showTaskEditor = false }) { Text("cancelar", color = Muted) }
            },
            containerColor = Cream
        )
    }

}

@Composable
private fun CalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val firstDayOffset = month.atDay(1).dayOfWeek.value % 7
    val days = List(firstDayOffset) { null } + (1..month.lengthOfMonth()).map { it }

    Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, PtBr).replaceFirstChar { it.titlecase(PtBr) },
                        color = Ink,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(month.year.toString(), color = Muted, fontSize = 12.sp)
                }
                TextButton(onClick = onPrevious) { Text("‹", color = Rose, fontSize = 25.sp) }
                TextButton(onClick = onNext) { Text("›", color = Rose, fontSize = 25.sp) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb").forEach { label ->
                    Text(label, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier.weight(1f).height(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                val date = month.atDay(day)
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            when {
                                                isSelected -> Blush
                                                isToday -> Lilac
                                                else -> Color.Transparent
                                            },
                                            CircleShape
                                        )
                                        .clickable { onSelectDate(date) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        day.toString(),
                                        color = if (isSelected || isToday) Rose else Ink,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text("toque em um dia para organizar sua agenda ♡", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    hour: String,
    onHourChange: (String) -> Unit,
    minute: String,
    onMinuteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Ink, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            OutlinedTextField(
                value = hour,
                onValueChange = { onHourChange(it.filter(Char::isDigit).take(2)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("09") }
            )
            Text(":", color = Ink)
            OutlinedTextField(
                value = minute,
                onValueChange = { onMinuteChange(it.filter(Char::isDigit).take(2)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("00") }
            )
        }
    }
}

@Composable
private fun SummaryCard(title: String, detail: String, modifier: Modifier, color: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("♡", color = Rose, fontSize = 18.sp)
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = Rose, fontSize = 11.sp)
        }
    }
}

private fun loadTasks(prefs: android.content.SharedPreferences, date: LocalDate): SnapshotStateList<PlannerTask> {
    val result = mutableStateListOf<PlannerTask>()
    prefs.getStringSet(taskKey(date), emptySet()).orEmpty().forEach { saved ->
        val parts = saved.split("|", limit = 3)
        if (parts.size == 3) {
            val id = parts[0].toLongOrNull() ?: return@forEach
            result.add(PlannerTask(id, parts[2], parts[1] == "1"))
        }
    }
    return result
}

private fun addTask(
    prefs: android.content.SharedPreferences,
    date: LocalDate,
    tasks: SnapshotStateList<PlannerTask>,
    title: String
) {
    tasks.add(PlannerTask(System.currentTimeMillis(), title, false))
    saveTasks(prefs, date, tasks)
}

private fun updateTask(
    prefs: android.content.SharedPreferences,
    date: LocalDate,
    tasks: SnapshotStateList<PlannerTask>,
    updated: PlannerTask
) {
    val index = tasks.indexOfFirst { it.id == updated.id }
    if (index >= 0) tasks[index] = updated
    saveTasks(prefs, date, tasks)
}

private fun deleteTask(
    prefs: android.content.SharedPreferences,
    date: LocalDate,
    tasks: SnapshotStateList<PlannerTask>,
    id: Long
) {
    tasks.removeAll { it.id == id }
    saveTasks(prefs, date, tasks)
}

private fun saveTasks(
    prefs: android.content.SharedPreferences,
    date: LocalDate,
    tasks: List<PlannerTask>
) {
    val values = tasks.map { "${it.id}|${if (it.done) 1 else 0}|${it.title}" }.toSet()
    prefs.edit().putStringSet(taskKey(date), values).apply()
}

private fun taskKey(date: LocalDate) = "tasks_${date}"


private fun scheduleDailyUsageReport(context: Context) {
    val intent = Intent(context, DailyUsageReceiver::class.java).setAction(DailyUsageReceiver.ACTION_REPORT)
    val pending = PendingIntent.getBroadcast(
        context, 2200, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val now = java.time.ZonedDateTime.now()
    var next = now.toLocalDate().atTime(22, 0).atZone(now.zone)
    if (!next.isAfter(now)) next = next.plusDays(1)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
}

private fun cancelDailyUsageReport(context: Context) {
    val intent = Intent(context, DailyUsageReceiver::class.java).setAction(DailyUsageReceiver.ACTION_REPORT)
    val pending = PendingIntent.getBroadcast(
        context, 2200, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
}

private fun loadReminders(prefs: android.content.SharedPreferences): List<PauseReminder> {
    val json = runCatching { JSONArray(prefs.getString("pause_reminders_json", "[]")) }.getOrNull() ?: JSONArray()
    return (0 until json.length()).mapNotNull { i ->
        val item = json.optJSONObject(i) ?: return@mapNotNull null
        PauseReminder(item.optLong("id"), item.optString("title"), item.optString("date"), item.optString("time"), item.optBoolean("enabled", true))
    }
}

private fun saveReminders(prefs: android.content.SharedPreferences, reminders: List<PauseReminder>) {
    val json = JSONArray()
    reminders.forEach { r ->
        json.put(JSONObject().put("id", r.id).put("title", r.title).put("date", r.date).put("time", r.time).put("enabled", r.enabled))
    }
    prefs.edit().putString("pause_reminders_json", json.toString()).apply()
}

private fun scheduleReminder(context: Context, reminder: PauseReminder) {
    val intent = Intent(context, ReminderReceiver::class.java)
        .putExtra("reminder_title", reminder.title)
        .putExtra("reminder_id", reminder.id)
    val pending = PendingIntent.getBroadcast(context, reminder.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (!reminder.enabled) { alarm.cancel(pending); return }
    val dateTime = runCatching { java.time.LocalDateTime.parse("${reminder.date}T${reminder.time}:00") }.getOrNull() ?: return
    val millis = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    if (millis > System.currentTimeMillis()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
}

private fun cancelReminder(context: Context, id: Long) {
    val intent = Intent(context, ReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    ) == AppOpsManager.MODE_ALLOWED
}

private fun loadLaunchableApps(context: Context): List<AppChoice> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { info ->
            val packageName = info.activityInfo.packageName
            if (packageName == context.packageName) null
            else AppChoice(
                packageName,
                info.loadLabel(context.packageManager).toString()
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(PtBr) }
}
