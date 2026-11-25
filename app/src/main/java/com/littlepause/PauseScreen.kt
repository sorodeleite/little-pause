package com.littlepause

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun PauseScreen(
    prefs: android.content.SharedPreferences,
    onOpenCalendar: () -> Unit
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var blocking by remember { mutableStateOf(KeepAlive.isBlockingEnabled(context)) }
    var confirmOff by remember { mutableStateOf(false) }
    var dailyReport by remember { mutableStateOf(prefs.getBoolean("daily_usage_report_enabled", true)) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh += 1 }

    val usageAllowed = remember(refresh) { hasUsageAccess(context) }
    val overlayAllowed = remember(refresh) { Settings.canDrawOverlays(context) }
    val batteryFree = remember(refresh) { KeepAlive.isIgnoringBatteryOptimizations(context) }
    val notificationsAllowed = remember(refresh) { canNotify(context) }
    val scheduleCount = remember(refresh) {
        runCatching {
            val array = JSONArray(prefs.getString("block_schedules_json", "[]"))
            (0 until array.length()).count { index ->
                val item = array.optJSONObject(index)
                item != null && item.optBoolean("enabled", true) &&
                    (item.optJSONArray("packages")?.length() ?: 0) > 0
            }
        }.getOrDefault(0)
    }
    val limitCount = remember(refresh) {
        runCatching {
            JSONObject(prefs.getString("app_daily_limits_json", "{}") ?: "{}").length()
        }.getOrDefault(0)
    }

    LaunchedEffect(dailyReport) {
        prefs.edit().putBoolean("daily_usage_report_enabled", dailyReport).apply()
        if (dailyReport) scheduleDailyUsageReport(context) else cancelDailyUsageReport(context)
    }

    fun enableBlocking() {
        refresh += 1
        if (!hasUsageAccess(context)) {
            openUsageSettings(context)
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            openOverlaySettings(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !canNotify(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        KeepAlive.setBlockingEnabled(context, true)
        blocking = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 22.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("as pausas ♡", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Normal)
                Text("seu tempo, do seu jeito — mas combinado antes", color = Muted, fontSize = 11.sp)
            }
        }

        item {
            SoftCard(container = if (blocking) Blush else Paper) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (blocking) "a pausa está de pé ♡" else "a pausa está dormindo",
                            color = Ink, fontSize = 17.sp
                        )
                        Text(
                            if (blocking) "$scheduleCount horário(s) · $limitCount limite(s) diário(s) sendo cuidados"
                            else "monte um horário ou um limite diário e ligue a pausa",
                            color = Muted, fontSize = 11.sp
                        )
                    }
                    Text(if (blocking) "✧" else "☁", color = Rose, fontSize = 22.sp)
                }
                Button(
                    onClick = {
                        if (blocking) confirmOff = true else enableBlocking()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (blocking) Sand else Rose,
                        contentColor = if (blocking) Ink else androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text(if (blocking) "desligar por hoje" else "ligar a pausa", fontSize = 13.sp)
                }
                if (!blocking && scheduleCount == 0 && limitCount == 0) {
                    Text("dica: comece com uma pausinha curta, tipo 30 min depois do almoço ♡", color = Muted, fontSize = 10.sp)
                }
            }
        }

        item {
            SoftCard {
                SectionHeader(
                    title = "para a pausa funcionar",
                    detail = "o Android pede algumas licenças; nada além do necessário",
                    action = {
                        TextButton(onClick = { refresh += 1 }) {
                            Text("verificar", color = RoseDeep, fontSize = 11.sp)
                        }
                    }
                )
                PermissionRow(
                    title = "acesso ao uso dos apps",
                    detail = "para saber qual app está na sua frente",
                    ok = usageAllowed,
                    onOpen = { openUsageSettings(context) }
                )
                PermissionRow(
                    title = "sobrepor outros apps",
                    detail = "para mostrar a telinha da pausa",
                    ok = overlayAllowed,
                    onOpen = { openOverlaySettings(context) }
                )
                PermissionRow(
                    title = "notificações",
                    detail = "para o lembrete das 22h e o aviso de pausa ativa",
                    ok = notificationsAllowed,
                    onOpen = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            refresh += 1
                        }
                    }
                )
            }
        }

        item {
            SoftCard(container = if (batteryFree) Lilac else Paper) {
                Text(
                    if (batteryFree) "o little pause pode ficar acordado ♡"
                    else "não deixe o Android cortar as asinhas dele",
                    color = Ink, fontSize = 15.sp
                )
                Text(
                    if (batteryFree)
                        "a economia de bateria não vai mais fechar a pausa quando a tela apagar. " +
                            "em alguns celulares (Xiaomi, Samsung, Motorola) vale liberar também o \"iniciar automaticamente\"."
                    else "quando a tela apaga, o Android às vezes fecha apps que ficam de vigia. " +
                        "liberar a economia de bateria mantém a pausa viva — sem exageros, só o necessário.",
                    color = Muted, fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!batteryFree) {
                        Button(
                            onClick = { KeepAlive.openBatterySettings(context); refresh += 1 },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = androidx.compose.ui.graphics.Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) { Text("liberar a bateria", fontSize = 12.sp) }
                    }
                    TextButton(onClick = { KeepAlive.start(context) }) {
                        Text("reacender agora", color = RoseDeep, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            BlockSchedulesCard(onChanged = { refresh += 1 })
        }

        item { AppDailyLimitsCard() }

        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("seu resumo das 22h  ♡", color = Ink, fontSize = 16.sp)
                        Text("um lembrete gentil sobre o tempo de tela", color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = dailyReport,
                        onCheckedChange = { enabled ->
                            dailyReport = enabled
                            if (enabled && Build.VERSION.SDK_INT >= 33 && !canNotify(context)) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Paper, checkedTrackColor = Rose)
                    )
                }
                Text(
                    "todos os dias, às 22h, você recebe seu tempo total no celular e uma comparação com filmes de 1h40.",
                    color = Muted, fontSize = 11.sp
                )
            }
        }

        item {
            SoftCard(container = Sand) {
                Text("como a pausa se comporta ♡", color = Ink, fontSize = 15.sp)
                Text(
                    "• quando um app bloqueado abre, aparece uma telinha calma com um respiro de 15 segundos\n" +
                        "• \"continuar por 1 minutinho\" existe, mas cada fuga aumenta o respiro da próxima (até 45 s)\n" +
                        "• nada aqui mexe nas suas configurações, não desinstala apps e não esconde o próprio ícone\n" +
                        "• seus horários e limites ficam só no seu celular",
                    color = Muted, fontSize = 11.sp
                )
                TextButton(onClick = onOpenCalendar, contentPadding = PaddingValues(0.dp)) {
                    Text("ver o calendário ✧", color = RoseDeep, fontSize = 11.sp)
                }
            }
        }
    }

    if (confirmOff) {
        GentleConfirmDialog(
            title = "desligar a pausa?",
            message = "os apps voltam a abrir normalmente e os horários param de ser cuidados. " +
                "a pausa não se ofende, mas ela foi feita para te ajudar ♡",
            confirmLabel = "desligar mesmo assim",
            waitSeconds = 6,
            onConfirm = {
                confirmOff = false
                KeepAlive.setBlockingEnabled(context, false)
                blocking = false
                refresh += 1
            },
            onDismiss = { confirmOff = false }
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    ok: Boolean,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (ok) Lilac else Blush, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ok) "✓" else "○", color = if (ok) RoseDeep else Muted, fontSize = 13.sp)
        Column(
            Modifier.weight(1f).padding(horizontal = 9.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(title, color = Ink, fontSize = 12.sp)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
        if (!ok) {
            TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("abrir", color = RoseDeep, fontSize = 11.sp)
            }
        }
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    return appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    ) == AppOpsManager.MODE_ALLOWED
}

private fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED

private fun openUsageSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openOverlaySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
