package tv.justrealrin.rinstu

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var pointer: ImageView
    private var pointerX = 0f
    private var pointerY = 0f
    private val startUrl = "https://justrealrin.github.io/"
    private val pointerHideDelayMs = 3000L
    private val handler = Handler(Looper.getMainLooper())
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var customView: View? = null

    private val hidePointerRunnable = Runnable { pointer.visibility = View.GONE }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()

    private fun setImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setImmersive()

        root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    root.removeView(webView)
                    root.addView(view, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                    setImmersive()
                }

                override fun onHideCustomView() {
                    customView?.let { v ->
                        root.removeView(v)
                        customView = null
                    }
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    root.addView(webView, 0)
                    setImmersive()
                }
            }
            loadUrl(startUrl)
        }

        pointer = ImageView(this).apply {
            val size = dpToPx(32)
            layoutParams = FrameLayout.LayoutParams(size, size)
            setBackgroundColor(Color.argb(180, 255, 255, 255))
            elevation = 10f
            visibility = View.GONE
        }

        root.addView(webView)
        root.addView(pointer)
        setContentView(root)

        root.post {
            pointerX = (root.width / 2).toFloat()
            pointerY = (root.height / 2).toFloat()
            updatePointerPosition()
        }
    }

    private fun updatePointerPosition() {
        pointer.x = pointerX - pointer.width / 2
        pointer.y = pointerY - pointer.height / 2
        pointer.requestLayout()
    }

    private fun showPointerForAWhile() {
        handler.removeCallbacks(hidePointerRunnable)
        pointer.visibility = View.VISIBLE
        handler.postDelayed(hidePointerRunnable, pointerHideDelayMs)
    }

    private fun sendMotionEventToWebView(x: Float, y: Float) {
        val down = MotionEvent.obtain(
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        )
        val up = MotionEvent.obtain(
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        )
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (customView != null) {
                    (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
                    return true
                }
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (pointer.visibility == View.VISIBLE) {
                    val localX = pointerX - webView.x
                    val localY = pointerY - webView.y
                    sendMotionEventToWebView(localX, localY)
                    return true
                } else {
                    webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                val handledDown = webView.dispatchKeyEvent(down)
                val handledUp = webView.dispatchKeyEvent(up)
                if (handledDown || handledUp) {
                    return true
                }

                val step = dpToPx(80) 
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> pointerY = (pointerY - step).coerceAtLeast(0f)
                    KeyEvent.KEYCODE_DPAD_DOWN -> pointerY = (pointerY + step).coerceAtMost((root.height - 1).toFloat())
                    KeyEvent.KEYCODE_DPAD_LEFT -> pointerX = (pointerX - step).coerceAtLeast(0f)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> pointerX = (pointerX + step).coerceAtMost((root.width - 1).toFloat())
                }
                updatePointerPosition()
                showPointerForAWhile()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        webView.apply {
            clearHistory()
            removeAllViews()
            destroy()
        }
    }
}