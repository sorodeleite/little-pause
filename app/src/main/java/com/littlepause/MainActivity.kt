package com.littlepause

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // desenha atrás das barras do sistema e deixa o Compose cuidar dos espaços
        enableEdgeToEdge()
        setContent {
            LittlePauseTheme {
                LittlePauseApp()
            }
        }
    }
}

@Composable
private fun LittlePauseTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Rose,
            onPrimary = Color.White,
            secondary = Ink,
            onSecondary = Color.White,
            background = Cream,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            surfaceVariant = Sand,
            onSurfaceVariant = Muted,
            outline = Line
        ),
        typography = base.copy(
            titleLarge = base.titleLarge.copy(color = Ink, fontWeight = FontWeight.Normal),
            bodySmall = base.bodySmall.copy(color = Muted)
        ),
        content = content
    )
}

internal enum class PauseTab(val label: String, val mark: String) {
    Hoje("hoje", "♡"),
    Calendario("calendário", "✧"),
    Pausas("pausas", "☁")
}

@Composable
private fun LittlePauseApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(KeepAlive.PREFS, Context.MODE_PRIVATE)
    }
    var tab by remember { mutableStateOf(PauseTab.Hoje) }

    // se a pausa estava ligada e o serviço cochilou, volta a vigiar ao abrir o app
    LaunchedEffect(Unit) {
        if (KeepAlive.isBlockingEnabled(context)) KeepAlive.start(context)
    }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { PauseBottomBar(tab) { tab = it } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                PauseTab.Hoje -> HomeScreen(
                    prefs = prefs,
                    onOpenCalendar = { tab = PauseTab.Calendario },
                    onOpenPause = { tab = PauseTab.Pausas }
                )

                PauseTab.Calendario -> CalendarScreen(onLeave = { tab = PauseTab.Hoje })

                PauseTab.Pausas -> PauseScreen(
                    prefs = prefs,
                    onOpenCalendar = { tab = PauseTab.Calendario }
                )
            }
        }
    }
}

@Composable
private fun PauseBottomBar(selected: PauseTab, onSelect: (PauseTab) -> Unit) {
    Column(Modifier.background(Paper)) {
        HorizontalDivider(color = Line, thickness = 0.8.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PauseTab.entries.forEach { item ->
                val active = item == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (active) Blush else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { onSelect(item) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(item.mark, color = if (active) RoseDeep else Muted, fontSize = 14.sp)
                    Text(
                        item.label,
                        color = if (active) RoseDeep else Muted,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
