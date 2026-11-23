package com.littlepause

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
                    PlannerHome(
                        onAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannerHome(onAccessibility: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("little_pause", android.content.Context.MODE_PRIVATE)
    }
    var xBlocked by remember { mutableStateOf(prefs.getBoolean("block_x", true)) }
    var checkedTasks by remember { mutableStateOf(setOf<Int>()) }
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val firstOffset = month.atDay(1).dayOfWeek.value % 7
    val days = (1..month.lengthOfMonth()).toList()
    val weekdays = listOf("D", "S", "T", "Q", "Q", "S", "S")

    Column(
        modifier = Modifier.fillMaxSize().background(Cream).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("um cantinho para respirar  ♡", color = Rose, fontSize = 13.sp)
                Text("little pause", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("♡", color = Rose, fontSize = 31.sp)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() },
                            color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("um mês de pequenas coisas ♡", color = Rose, fontSize = 12.sp)
                    }
                    Text("‹       ›", color = Rose, fontSize = 20.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weekdays.forEach { Text(it, color = Rose, fontSize = 12.sp, modifier = Modifier.weight(1f)) }
                }
                val cells = List(firstOffset) { null } + days.map { it }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        week.forEach { day ->
                            BoxDay(day = day, selected = day == today.dayOfMonth)
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallSummary("♡", "hoje", "um passo de cada vez", Modifier.weight(1f), Pink)
            SmallSummary("✿", "seu ritmo", "sem pressa", Modifier.weight(1f), Lilac)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("pequenas tarefas  ♡", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                listOf("arrumar a caminha", "tomar uma aguinha", "fazer algo por mim").forEachIndexed { index, task ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = index in checkedTasks,
                            onCheckedChange = { checked ->
                                checkedTasks = if (checked) checkedTasks + index else checkedTasks - index
                            }
                        )
                        Text(task, color = Ink, fontSize = 14.sp)
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Pink), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("horário de carinho digital", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("09:00 — 11:00  ·  todos os dias", color = Rose, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = xBlocked,
                        onCheckedChange = {
                            xBlocked = it
                            prefs.edit().putBoolean("block_x", it).apply()
                        }
                    )
                    Column(Modifier.weight(1f)) {
                        Text("pausar o X", color = Ink, fontWeight = FontWeight.Medium)
                        Text("duas horinhas para você ♡", color = Rose, fontSize = 12.sp)
                    }
                    Text("♡", color = Color.White, fontSize = 27.sp)
                }
                Button(
                    onClick = onAccessibility,
                    colors = ButtonDefaults.buttonColors(containerColor = Rose),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("configurar bloqueio no celular", color = Color.White)
                }
            }
        }
        Text("feito com carinho, para os seus dias ♡", color = Rose, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun BoxDay(day: Int?, selected: Boolean) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.weight(1f).height(34.dp),
        contentAlignment = Alignment.Center
    ) {
        if (day != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .background(if (selected) Pink else Color.Transparent, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(day.toString(), color = if (selected) Rose else Ink, fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SmallSummary(icon: String, title: String, detail: String, modifier: Modifier, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(icon, color = Rose, fontSize = 20.sp)
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = Rose, fontSize = 11.sp)
        }
    }
}
