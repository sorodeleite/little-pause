package com.littlepause

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * O calendário ocupa a tela inteira: assim o Android não corta as bordas
 * dele com a barra de status, o notch ou a barra de navegação. A página
 * rola sozinha por dentro do webview.
 */
@Composable
internal fun CalendarScreen(onLeave: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val view = webView
        if (view == null) {
            onLeave()
            return@BackHandler
        }
        val sent = runCatching {
            view.evaluateJavascript(
                "(window.LittlePausePlanner && window.LittlePausePlanner.closeTop)" +
                    " ? window.LittlePausePlanner.closeTop() : false"
            ) { result ->
                if (result != "true") onLeave()
            }
        }.isSuccess
        if (!sent) onLeave()
    }

    CalendarView(
        modifier = Modifier.fillMaxSize(),
        onReady = { webView = it }
    )
}
