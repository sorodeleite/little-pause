package com.littlepause

import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
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

private const val CALENDAR_URL = "file:///android_asset/calendar/index.html"
private const val CALENDAR_TAG = "LittlePauseCalendar"

private class CalendarBridge(private val onDate: (String) -> Unit) {
    @JavascriptInterface
    fun onDateSelected(date: String) {
        onDate(date)
    }
}

/**
 * O calendário que nasceu no little place, morando aqui dentro.
 * A página é servida do próprio APK (só arquivos locais), guarda tudo no
 * armazenamento do webview e avisa o app quando alguém toca num dia.
 */
@Composable
fun CalendarView(
    modifier: Modifier = Modifier,
    onDateSelected: (String) -> Unit = {},
    onReady: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val latestDate = rememberUpdatedState(onDateSelected)
    val latestReady = rememberUpdatedState(onReady)
    val pendingFile = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingFile[0]?.onReceiveValue(uri?.let { arrayOf(it) } ?: emptyArray())
        pendingFile[0] = null
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            WebView(viewContext).apply {
                setBackgroundColor(Color.rgb(255, 253, 252))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mediaPlaybackRequiresUserGesture = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    textZoom = 100
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    loadsImagesAutomatically = true
                }
                isVerticalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                addJavascriptInterface(
                    CalendarBridge { date -> latestDate.value(date) },
                    "LittlePauseCalendar"
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            "document.querySelectorAll('.cal-day-cell').length.toString()"
                        ) { count ->
                            Log.d(CALENDAR_TAG, "calendário carregado; células: $count")
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d(CALENDAR_TAG, "${message.message()} (linha ${message.lineNumber()})")
                        return true
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        pendingFile[0]?.onReceiveValue(null)
                        pendingFile[0] = filePathCallback
                        imagePicker.launch("image/*")
                        return true
                    }
                }
                latestReady.value(this)
                loadUrl(CALENDAR_URL)
            }
        },
        update = { }
    )
}
