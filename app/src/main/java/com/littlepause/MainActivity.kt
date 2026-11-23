package com.littlepause

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Cream = Color(0xFFFFF8F5)
private val Paper = Color(0xFFFFFFFF)
private val Blush = Color(0xFFF5E2E8)
private val Rose = Color(0xFFB8758D)
private val Ink = Color(0xFF705A61)
private val Lilac = Color(0xFFF0ECF6)
private val Muted = Color(0xFFB49AA3)
private val PtBr = Locale("pt", "BR")

data class AppChoice(val packageName: String, val label: String)
private data class PlannerTask(val id: Long, val title: String, val done: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = loadLaunchableApps()
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
                    PlannerHome(apps) {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadLaunchableApps(): List<AppChoice> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val appPackage = info.activityInfo?.packageName ?: return@mapNotNull null
                if (appPackage == packageName) return@mapNotNull null
                AppChoice(appPackage, info.loadLabel(packageManager).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(PtBr) }
    }
}

@Composable
private fun PlannerHome(apps: List<AppChoice>, onAccessibility: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("little_pause", Context.MODE_PRIVATE) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedApps by remember {
        mutableStateOf(prefs.getStringSet("blocked_packages", emptySet())?.toSet() ?: emptySet())
    }
    var startHour by remember { mutableStateOf(prefs.getInt("start_hour", 9).toString()) }
    var startMinute by remember { mutableStateOf(prefs.getInt("start_minute", 0).toString().padStart(2, '0')) }
    var endHour by remember { mutableStateOf(prefs.getInt("end_hour", 11).toString()) }
    var endMinute by remember { mutableStateOf(prefs.getInt("end_minute", 0).toString().padStart(2, '0')) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showTaskEditor by remember { mutableStateOf(false) }
    var newTask by remember { mutableStateOf("") }
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
                Text("um cantinho para respirar  ♡", color = Rose, fontSize = 12.sp)
                Text("little pause", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier.size(46.dp).background(Blush, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("♡", color = Rose, fontSize = 27.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
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
                        title = "pequenas pausas",
                        detail = "${selectedApps.size} app(s) selecionado(s)",
                        modifier = Modifier.weight(1f),
                        color = Lilac
                    )
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = Blush),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("horário de carinho digital", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("escolha quando sua pausa acontece ♡", color = Rose, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimeField("começa", startHour, { startHour = it }, startMinute, { startMinute = it }, Modifier.weight(1f))
                            TimeField("termina", endHour, { endHour = it }, endMinute, { endMinute = it }, Modifier.weight(1f))
                        }
                        Text("${selectedApps.size} aplicativo(s) na sua pausa", color = Ink, fontSize = 13.sp)
                        OutlinedButton(
                            onClick = { showAppPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose)
                        ) {
                            Text("escolher aplicativos")
                        }
                        Button(
                            onClick = {
                                val fromHour = startHour.toIntOrNull()?.coerceIn(0, 23) ?: 9
                                val fromMinute = startMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                                val untilHour = endHour.toIntOrNull()?.coerceIn(0, 23) ?: 11
                                val untilMinute = endMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                                prefs.edit()
                                    .putStringSet("blocked_packages", selectedApps)
                                    .putInt("start_hour", fromHour)
                                    .putInt("start_minute", fromMinute)
                                    .putInt("end_hour", untilHour)
                                    .putInt("end_minute", untilMinute)
                                    .apply()
                                startHour = fromHour.toString()
                                startMinute = fromMinute.toString().padStart(2, '0')
                                endHour = untilHour.toString()
                                endMinute = untilMinute.toString().padStart(2, '0')
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Rose)
                        ) {
                            Text("salvar minha pausa", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onAccessibility,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
                        ) {
                            Text("ativar serviço de bloqueio")
                        }
                        Text(
                            "o bloqueio só funciona depois que você ativar o serviço nas configurações de acessibilidade do Android.",
                            color = Rose,
                            fontSize = 11.sp
                        )
                    }
                }
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

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("escolha seus aplicativos ♡", color = Ink) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = app.packageName in selectedApps,
                                onCheckedChange = { checked ->
                                    selectedApps = if (checked) selectedApps + app.packageName else selectedApps - app.packageName
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Rose)
                            )
                            Text(app.label, color = Ink, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text("prontinho", color = Rose) }
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
