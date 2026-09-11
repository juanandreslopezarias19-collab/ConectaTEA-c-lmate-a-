package com.example
 
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.BgDark
import com.example.ui.theme.MyApplicationTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        initTextToSpeech()

        setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark)
                        .statusBarsPadding()
                        .testTag("main_screen_container")
                ) {
                    CalmaTeaWebView(
                        onWebViewCreated = { wv -> webView = wv },
                        bridge = AndroidBridge(this@MainActivity)
                    )
                }
            }
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val spanishLocale = Locale("es", "ES")
                val result = textToSpeech?.setLanguage(spanishLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale("es"))
                }
                textToSpeech?.setSpeechRate(0.92f)
                textToSpeech?.setPitch(1.08f)
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            webView?.evaluateJavascript("if (window.onNativeSpeechStart) window.onNativeSpeechStart();", null)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            webView?.evaluateJavascript("if (window.onNativeSpeechEnd) window.onNativeSpeechEnd();", null)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            webView?.evaluateJavascript("if (window.onNativeSpeechEnd) window.onNativeSpeechEnd();", null)
                        }
                    }
                })
                isTtsInitialized = true
            }
        }
    }

    inner class AndroidBridge(private val context: Context) {
        @JavascriptInterface
        fun isTtsReady(): Boolean = isTtsInitialized

        @JavascriptInterface
        fun speak(text: String) {
            runOnUiThread {
                if (isTtsInitialized && textToSpeech != null) {
                    val params = Bundle()
                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "CalmaTeaUtterance")
                }
            }
        }

        @JavascriptInterface
        fun stopSpeech() {
            runOnUiThread {
                textToSpeech?.stop()
            }
        }

        @JavascriptInterface
        fun vibrate(durationMs: Long) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        webView?.destroy()
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CalmaTeaWebView(
    onWebViewCreated: (WebView) -> Unit,
    bridge: MainActivity.AndroidBridge
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(0xFF0F172A.toInt())

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    displayZoomControls = false
                    builtInZoomControls = false
                }

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return false
                    }
                }

                addJavascriptInterface(bridge, "AndroidBridge")
                loadUrl("file:///android_asset/index.html")
                onWebViewCreated(this)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("calmatea_webview")
    )
}
