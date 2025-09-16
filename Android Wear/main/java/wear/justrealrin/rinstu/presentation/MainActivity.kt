package wear.justrealrin.rinstu.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private var webViewRefGlobal: WebView? = null

    private fun applyImmersiveModeTo(view: View?) {
        view?.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            val context = LocalContext.current
            var webViewRef by remember { mutableStateOf<WebView?>(null) }

            BackHandler(enabled = true) {
                webViewRef?.let { wv ->
                    if (wv.canGoBack()) wv.goBack() else finish()
                } ?: finish()
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val webView = WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }
                        }
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }

                    val EDGE_THRESHOLD_RATIO = 0.15f
                    val SWIPE_MIN_DISTANCE = 50f
                    val SWIPE_MAX_DURATION_MS = 600L

                    var touchStartX = -1f
                    var touchStartTime = 0L

                    webView.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                touchStartX = event.x
                                touchStartTime = System.currentTimeMillis()
                            }
                            MotionEvent.ACTION_UP -> {
                                val dx = event.x - touchStartX
                                val dt = System.currentTimeMillis() - touchStartTime
                                val edgeThreshold = v.width * EDGE_THRESHOLD_RATIO
                                if (touchStartX >= 0f && touchStartX <= edgeThreshold && dx > SWIPE_MIN_DISTANCE && dt <= SWIPE_MAX_DURATION_MS) {
                                    if (webView.canGoBack()) {
                                        webView.goBack()
                                        return@setOnTouchListener true
                                    }
                                }
                            }
                        }

                        false
                    }

                    webView.loadUrl("https://justrealrin.github.io/")
                    webViewRef = webView

                    applyImmersiveModeTo(webView)
                    webViewRef = webView
                    webViewRefGlobal = webView

                    webView
                },
                update = { webView ->
                    webViewRef = webView
                    webViewRefGlobal = webView
                    applyImmersiveModeTo(webView)
                }
            )
        }

        val decorView = window.decorView
        val sysUiListener = View.OnSystemUiVisibilityChangeListener { visibility ->
            val isFullscreen = (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
            if (!isFullscreen) {
                applyImmersiveModeTo(webViewRefGlobal)
                applyImmersiveModeTo(decorView)
            }
        }
        decorView.setOnSystemUiVisibilityChangeListener(sysUiListener)

        decorView.setTag(-200, sysUiListener)
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveModeTo(webViewRefGlobal)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveModeTo(webViewRefGlobal)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val decorView = window.decorView
        val tag = decorView.getTag(-200)
        if (tag is View.OnSystemUiVisibilityChangeListener) {
            decorView.setOnSystemUiVisibilityChangeListener(null)
            decorView.setTag(-200, null)
        }
    }
}
