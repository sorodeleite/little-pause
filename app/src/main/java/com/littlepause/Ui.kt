package com.littlepause

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/* a paleta do little place: creme, taupe, chocolate e um rosé bem de leve */

val Paper = Color(0xFFFFFDFC)
val Cream = Color(0xFFF7F5F4)
val Blush = Color(0xFFFDEEF1)
val Lilac = Color(0xFFF1EDF6)
val Sand = Color(0xFFF4EDEA)
val Ink = Color(0xFF463529)
val Muted = Color(0xFF7D6B5E)
val Rose = Color(0xFFC17E93)
val RoseDeep = Color(0xFF9C5F73)
val Line = Color(0xFFEEE7E4)

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    container: Color = Paper,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(0.7.dp, Line)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    detail: String? = null,
    kicker: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (kicker != null) Text(kicker, color = Rose, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (detail != null) Text(detail, color = Muted, fontSize = 11.sp)
        }
        action?.invoke()
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = CircleShape
    val base = if (active) Blush else Sand
    Row(
        modifier = modifier
            .background(base, shape)
            .border(0.7.dp, if (active) Rose.copy(alpha = 0.35f) else Line, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text, color = if (active) RoseDeep else Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Confirmação com uma espera curtinha: a pausa não é uma prisão, mas também
 * não se desfaz sem pensar. O botão só acende depois da contagem.
 */
@Composable
fun GentleConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    waitSeconds: Int = 6,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var remaining by remember { mutableIntStateOf(waitSeconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = Ink, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, color = Muted, fontSize = 13.sp)
                if (remaining > 0) {
                    Text(
                        "respira mais $remaining segundinho${if (remaining == 1) "" else "s"}...",
                        color = Rose, fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = remaining == 0) {
                Text(
                    if (remaining > 0) "espera $remaining s" else confirmLabel,
                    color = if (remaining > 0) Muted else RoseDeep
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("deixa quieto", color = Muted) }
        }
    )
}
