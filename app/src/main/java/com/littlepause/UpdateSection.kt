package com.littlepause

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog

@Composable
internal fun UpdateSection() {
    val context = LocalContext.current
    val state = AppUpdater.state.value
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppUpdater.refreshInstalled(context)
        AppUpdater.autoCheckIfDue(context)
    }

    SoftCard(container = if (state.hasUpdate) Blush else Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("atualizações ♡", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        state.checking -> "procurando uma versão nova no github…"
                        state.downloading -> "baixando a versão ${state.latestVersion}… ${state.progress}%"
                        state.error.isNotEmpty() -> state.error
                        state.downloaded -> "a v${state.latestVersion} está baixada e pronta pra instalar ♡"
                        state.hasUpdate -> "a v${state.latestVersion} já está disponível"
                        state.latestVersion.isNotEmpty() -> "você está na versão mais nova ♡  (v${state.currentVersion})"
                        else -> "versão ${state.currentVersion} · veja as novidades e atualize por aqui"
                    },
                    color = Muted, fontSize = 11.sp
                )
            }
            Pill(
                text = if (state.hasUpdate) "v${state.latestVersion} ✧" else "v${state.currentVersion}",
                active = state.hasUpdate,
                onClick = { open = true }
            )
        }

        if (state.hasUpdate && state.notes.isNotEmpty()) {
            Text(
                state.notes.lineSequence().take(3).joinToString("\n"),
                color = Muted, fontSize = 11.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hasUpdate) {
                Button(
                    onClick = {
                        if (state.downloaded) {
                            AppUpdater.install(context)
                        } else {
                            AppUpdater.download(context)
                        }
                    },
                    enabled = !state.downloading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        if (state.downloaded) "instalar agora" else if (state.downloading) "baixando…" else "baixar atualização",
                        fontSize = 12.sp
                    )
                }
            }
            TextButton(onClick = { open = true }) {
                Text(if (state.hasUpdate) "ver o que mudou" else "ver novidades", color = RoseDeep, fontSize = 11.sp)
            }
        }
    }

    if (open) UpdateDialog(onClose = { open = false })
}

@Composable
private fun UpdateDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val state = AppUpdater.state.value

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Paper,
            border = BorderStroke(0.8.dp, Line)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("atualizações", color = RoseDeep, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        Text("atualizar o app", color = Ink, fontSize = 20.sp)
                    }
                    TextButton(onClick = onClose, contentPadding = PaddingValues(4.dp)) {
                        Text("×", color = Muted, fontSize = 20.sp)
                    }
                }
                Text(
                    "atualiza por cima da versão atual, sem desinstalar e sem perder o calendário, as tarefinhas ou os adesivos.",
                    color = Muted, fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VersionPill("instalado", "v${state.currentVersion}")
                    Text("→", color = Muted, fontSize = 13.sp)
                    VersionPill(
                        label = if (state.hasUpdate) "nova" else "atual",
                        value = if (state.latestVersion.isNotEmpty()) "v${state.latestVersion}" else "—",
                        highlight = state.hasUpdate
                    )
                }

                if (state.downloading || (state.progress in 1..99)) {
                    LinearProgressIndicator(
                        progress = { (state.progress.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Rose,
                        trackColor = Blush
                    )
                    Text("baixando… ${state.progress}%", color = Muted, fontSize = 10.sp)
                }

                Text(statusMessage(state), color = if (state.error.isNotEmpty()) RoseDeep else Muted, fontSize = 11.sp)

                when {
                    state.downloaded -> PrimaryAction("instalar agora") { AppUpdater.install(context) }
                    state.hasUpdate && state.apkUrl.isNotEmpty() -> PrimaryAction(
                        if (state.downloading) "baixando…" else "baixar atualização",
                        enabled = !state.downloading
                    ) { AppUpdater.download(context) }
                    state.hasUpdate -> Text(
                        "essa versão ainda não tem o APK publicado. dá pra baixar direto do github.",
                        color = Muted, fontSize = 11.sp
                    )
                }

                if (!state.canInstall) {
                    TextButton(onClick = { AppUpdater.openInstallSettings(context) }, contentPadding = PaddingValues(0.dp)) {
                        Text("liberar a instalação de apps desconhecidos", color = RoseDeep, fontSize = 11.sp)
                    }
                }

                val shownNotes = if (state.hasUpdate) {
                    state.notes
                } else {
                    state.recent.firstOrNull { it.version == state.currentVersion }?.notes.orEmpty()
                }
                val shownVersion = if (state.hasUpdate) state.latestVersion else state.currentVersion
                if (shownNotes.isNotEmpty()) {
                    NoteBlock(
                        title = if (state.hasUpdate) "o que mudou na v$shownVersion" else "o que tem na v$shownVersion",
                        text = shownNotes
                    )
                }
                if (state.recent.size > 1) {
                    Text("versões recentes ♡", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    state.recent.take(5).forEach { release ->
                        RecentRelease(release)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { AppUpdater.check(context) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("verificar de novo", color = RoseDeep, fontSize = 11.sp)
                    }
                    TextButton(onClick = { AppUpdater.openReleases(context) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("ver no github", color = RoseDeep, fontSize = 11.sp)
                    }
                    TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("fechar", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionPill(label: String, value: String, highlight: Boolean = false) {
    Column(
        modifier = Modifier
            .background(if (highlight) Blush else Sand, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = Muted, fontSize = 9.sp)
        Text(value, color = if (highlight) RoseDeep else Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Color.White)
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun NoteBlock(title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(text, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun RecentRelease(release: ReleaseNote) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("v${release.version}", color = RoseDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (release.date.isNotEmpty()) {
                Text("· ${release.date}", color = Muted, fontSize = 10.sp)
            }
        }
        if (release.notes.isNotEmpty()) {
            Text(
                release.notes.lineSequence().take(4).joinToString("\n"),
                color = Muted, fontSize = 10.sp, lineHeight = 15.sp
            )
        }
    }
}

private fun statusMessage(state: UpdateState): String = when {
    state.error.isNotEmpty() -> state.error
    state.checking -> "procurando uma versão nova no github…"
    state.downloaded -> "download concluído. o Android pode pedir pra liberar a instalação; seus dados continuam aqui."
    state.hasUpdate -> "nova versão v${state.latestVersion} disponível."
    state.latestVersion.isNotEmpty() -> "você já está na versão mais nova (v${state.currentVersion}) ‧°"
    else -> "toque em verificar de novo pra procurar uma versão nova."
}
