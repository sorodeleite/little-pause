package com.littlepause

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private class LittlePauseCalendarBridge(
    private val selected: (String) -> Unit
) {
    @JavascriptInterface
    fun onDateSelected(date: String) {
        selected(date)
    }
}

@Composable
fun LittlePlaceCalendar(
    modifier: Modifier = Modifier,
    onDateSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val latestDateCallback = rememberUpdatedState(onDateSelected)
    val pendingFileCallback = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingFileCallback[0]?.onReceiveValue(uri?.let { arrayOf(it) } ?: emptyArray())
        pendingFileCallback[0] = null
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            WebView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                addJavascriptInterface(
                    LittlePauseCalendarBridge { date -> latestDateCallback.value(date) },
                    "LittlePauseCalendar"
                )
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        pendingFileCallback[0]?.onReceiveValue(null)
                        pendingFileCallback[0] = filePathCallback
                        imagePicker.launch("image/*")
                        return true
                    }
                }
                loadUrl("file:///android_asset/calendar/index.html")
            }
        },
        update = { }
    )
}
