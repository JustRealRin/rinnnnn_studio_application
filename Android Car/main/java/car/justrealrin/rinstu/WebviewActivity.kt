package car.justrealrin.rinstu

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class WebviewActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_webview)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webView = findViewById(R.id.webview)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.loadUrl("https://justrealrin.github.io/")

        val backBtn = findViewById<ImageButton>(R.id.btn_back)
        backBtn.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        // Hide system UI for full screen experience
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
