package com.littlepause

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val Cream = Color(0xFFFFF8F5)
private val Pink = Color(0xFFF4DDE5)
private val Rose = Color(0xFFB8758D)
private val Ink = Color(0xFF705A61)
private val Lilac = Color(0xFFEDE8F5)

data class AppChoice(val packageName: String, val label: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = loadLaunchableApps()
        setContent {
            MaterialTheme {
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
            .mapNotNull {
                val name = it.activityInfo?.packageName ?: return@mapNotNull null
                if (name == packageName) return@mapNotNull null
                AppChoice(name, it.loadLabel(packageManager).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
}

@Composable
private fun PlannerHome(apps: List<AppChoice>, onAccessibility: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("little_pause", android.content.Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(prefs.getStringSet("blocked_packages", emptySet())?.toSet() ?: emptySet()) }
    var start by remember { mutableStateOf(prefs.getInt("start_hour", 9).toString()) }
    var end by remember { mutableStateOf(prefs.getInt("end_hour", 11).toString()) }
    var picker by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf(setOf<Int>()) }
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val offset = month.atDay(1).dayOfWeek.value % 7

    Column(Modifier.fillMaxSize().background(Cream).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("um cantinho para respirar  ♡", color = Rose, fontSize = 13.sp)
                Text("little pause", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("♡", color = Rose, fontSize = 31.sp)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() },
                    color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("um mês de pequenas coisas ♡", color = Rose, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth()) {
                    listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                        Text(it, color = Rose, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    }
                }
                (List(offset) { null } + (1..month.lengthOfMonth()).map { it }).chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                                if (day != null) {
                                    Box(Modifier.background(if (day == today.dayOfMonth) Pink else Color.Transparent, RoundedCornerShape(50)).padding(8.dp)) {
                                        Text(day.toString(), color = if (day == today.dayOfMonth) Rose else Ink, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("♡", "hoje", "um passo de cada vez", Modifier.weight(1f), Pink)
            SummaryCard("✿", "seu ritmo", "sem pressa", Modifier.weight(1f), Lilac)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("pequenas tarefas  ♡", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                listOf("arrumar a caminha", "tomar uma aguinha", "fazer algo por mim").forEachIndexed { index, task ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(index in tasks, { checked -> tasks = if (checked) tasks + index else tasks - index })
                        Text(task, color = Ink, fontSize = 14.sp)
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Pink), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("horário de carinho digital", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("escolha quando sua pausa acontece ♡", color = Rose, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(start, { start = it.filter(Char::isDigit).take(2) }, label = { Text("começa (0–23)") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(end, { end = it.filter(Char::isDigit).take(2) }, label = { Text("termina (0–23)") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text("${selected.size} aplicativo(s) escolhido(s)", color = Ink)
                OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text("escolher aplicativos") }
                Button(onClick = {
                    val from = start.toIntOrNull()?.coerceIn(0, 23) ?: 9
                    val until = end.toIntOrNull()?.coerceIn(0, 23) ?: 11
                    prefs.edit().putStringSet("blocked_packages", selected).putInt("start_hour", from).putInt("end_hour", until).apply()
                    start = from.toString()
                    end = until.toString()
                }, colors = ButtonDefaults.buttonColors(containerColor = Rose), modifier = Modifier.fillMaxWidth()) {
                    Text("salvar minha pausa", color = Color.White)
                }
                OutlinedButton(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) { Text("ativar serviço de bloqueio") }
                Text("o bloqueio só funciona depois que você ativar o serviço nas configurações do Android.", color = Rose, fontSize = 11.sp)
            }
        }
        Text("feito com carinho, para os seus dias ♡", color = Rose, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }

    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            title = { Text("escolha seus aplicativos ♡", color = Ink) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(app.packageName in selected, { checked ->
                                selected = if (checked) selected + app.packageName else selected - app.packageName
                            })
                            Text(app.label, color = Ink)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = false }) { Text("prontinho", color = Rose) } },
            containerColor = Cream
        )
    }
}

@Composable
private fun SummaryCard(icon: String, title: String, detail: String, modifier: Modifier, color: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(icon, color = Rose, fontSize = 20.sp)
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = Rose, fontSize = 11.sp)
        }
    }
}
