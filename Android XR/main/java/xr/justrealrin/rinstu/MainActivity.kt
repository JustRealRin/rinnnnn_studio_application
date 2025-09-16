package xr.justrealrin.rinstu

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.LocalSpatialConfiguration
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.width
import xr.justrealrin.rinstu.ui.theme.RinStuXRTheme

class MainActivity : ComponentActivity() {

    // Hold reference to WebView so we can post messages from native input handlers
    private var webViewRef: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private var handRunnable: Runnable? = null

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Try to open the target URL in a browser (preferably Chrome / Android XR capable browser)
        val targetUrl = "https://justrealrin.github.io/"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            // Let the system pick an appropriate browser (Chrome on Android XR will handle WebXR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        var openedInBrowser = false
        try {
            startActivity(intent)
            openedInBrowser = true
        } catch (e: ActivityNotFoundException) {
            // No browser available, will fall back to an in-app WebView below
            openedInBrowser = false
        }

        if (openedInBrowser) {
            // Optionally finish this Activity so the browser is foregrounded
            finish()
            return
        }

        // Fallback: render a simple WebView that loads the URL and exposes a JS bridge
        setContent {
            RinStuXRTheme {
                val spatialConfiguration = LocalSpatialConfiguration.current
                if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
                    Subspace {
                        // Replace the panel content with a WebView host
                        AndroidWebViewHost(url = targetUrl, onWebViewCreated = { web -> webViewRef = web; startMockHandStream() })
                    }
                } else {
                    AndroidWebViewHost(url = targetUrl, onWebViewCreated = { web -> webViewRef = web; startMockHandStream() })
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewRef = null
        handRunnable?.let { handler.removeCallbacks(it) }
    }

    // Simple key handling PoC: send key down events to page as JSON
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val obj = JSONObject()
        obj.put("type", "key")
        obj.put("action", "down")
        obj.put("keyCode", keyCode)
        sendMessageToPage(obj.toString())
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val obj = JSONObject()
        obj.put("type", "key")
        obj.put("action", "up")
        obj.put("keyCode", keyCode)
        sendMessageToPage(obj.toString())
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent?): Boolean {
        // For Gamepad axes / joystick events you can extract axis values here; we'll send a minimal example
        event?.let {
            val source = it.source
            if ((source and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK ||
                (source and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD) {
                val axes = JSONObject()
                axes.put("lx", it.getAxisValue(android.view.MotionEvent.AXIS_X))
                axes.put("ly", it.getAxisValue(android.view.MotionEvent.AXIS_Y))
                // additional axes
                axes.put("rx", it.getAxisValue(android.view.MotionEvent.AXIS_RX))
                axes.put("ry", it.getAxisValue(android.view.MotionEvent.AXIS_RY))
                axes.put("rz", it.getAxisValue(android.view.MotionEvent.AXIS_RZ))
                axes.put("hatX", it.getAxisValue(android.view.MotionEvent.AXIS_HAT_X))
                axes.put("hatY", it.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y))
                val obj = JSONObject()
                obj.put("type", "gamepad")
                obj.put("axes", axes)
                sendMessageToPage(obj.toString())
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun sendMessageToPage(jsonPayload: String) {
        val js = "javascript:window.NativeXR && window.NativeXR.onInput && window.NativeXR.onInput($jsonPayload)"
        webViewRef?.post {
            try {
                webViewRef?.evaluateJavascript(js, null)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "failed to post message to page: ${e.message}")
            }
        }
    }

    // Periodically send mock hand joint frames (PoC). Replace this with real hand tracking provider.
    private fun startMockHandStream() {
        handRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                try {
                    val left = generateMockHand("left")
                    val right = generateMockHand("right")
                    sendMessageToPage(left.toString())
                    sendMessageToPage(right.toString())
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "mock hand frame failed: ${e.message}")
                }
                handler.postDelayed(this, 500)
            }
        }
        handRunnable = runnable
        handler.post(runnable)
    }

    private fun generateMockHand(handedness: String): JSONObject {
        val obj = JSONObject()
        obj.put("type", "hand")
        obj.put("handedness", handedness)
        val jointNames = listOf(
            "wrist",
            "thumb-metacarpal","thumb-phalanx-proximal","thumb-phalanx-distal","thumb-tip",
            "index-finger-metacarpal","index-finger-phalanx-proximal","index-finger-phalanx-intermediate","index-finger-phalanx-distal","index-finger-tip",
            "middle-finger-metacarpal","middle-finger-phalanx-proximal","middle-finger-phalanx-intermediate","middle-finger-phalanx-distal","middle-finger-tip",
            "ring-finger-metacarpal","ring-finger-phalanx-proximal","ring-finger-phalanx-intermediate","ring-finger-phalanx-distal","ring-finger-tip",
            "pinky-finger-metacarpal","pinky-finger-phalanx-proximal","pinky-finger-phalanx-intermediate","pinky-finger-phalanx-distal","pinky-finger-tip"
        )
        val joints = JSONArray()
        for (name in jointNames) {
            val j = JSONObject()
            j.put("name", name)
            // Mock position & orientation near origin; replace with real tracking data
            val x = (random.nextDouble() - 0.5) * 0.2
            val y = (random.nextDouble() - 0.5) * 0.2 + 0.1
            val z = (random.nextDouble() - 0.5) * 0.2
            j.put("x", x)
            j.put("y", y)
            j.put("z", z)
            // quaternion
            j.put("qx", 0.0)
            j.put("qy", 0.0)
            j.put("qz", 0.0)
            j.put("qw", 1.0)
            j.put("radius", 0.01)
            joints.put(j)
        }
        obj.put("joints", joints)
        return obj
    }
}

/**
 * Minimal WebView host composable (keeps the existing project structure simple).
 * Note: Compose interop is intentionally minimal here to avoid adding new dependencies.
 */
@Composable
fun AndroidWebViewHost(url: String, onWebViewCreated: ((WebView) -> Unit)? = null) {
    // Use AndroidView to host a WebView inside Compose
    androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(WebAppBridge(), "AndroidBridge")
            // Also inject a small adapter JS from assets if available
            try {
                val assetManager = ctx.assets
                val adapter = assetManager.open("native_xr_adapter.js").bufferedReader().use { it.readText() }
                // Load the adapter first via loadDataWithBaseURL so it is present for the page
                evaluateJavascript(adapter, null)
            } catch (e: Exception) {
                // asset not present or failed to load; ignore
                android.util.Log.d("AndroidWebViewHost", "native_xr_adapter.js not loaded: ${e.message}")
            }
            loadUrl(url)
            onWebViewCreated?.invoke(this)
        }
    }, update = { view ->
        // no-op update
    })
}

/**
 * Simple JavaScript bridge. Page can call AndroidBridge.postMessage(msg)
 * for future controller/gesture integration.
 */
class WebAppBridge {
    @JavascriptInterface
    fun postMessage(msg: String) {
        android.util.Log.d("WebAppBridge", "msg from page: $msg")
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun MySpatialContent(onRequestHomeSpaceMode: () -> Unit) {
    SpatialPanel(SubspaceModifier.width(1280.dp).height(800.dp).resizable().movable()) {
        Surface {
            MainContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp)
            )
        }
        Orbiter(
            position = ContentEdge.Top,
            offset = 20.dp,
            alignment = Alignment.End,
            shape = SpatialRoundedCornerShape(CornerSize(28.dp))
        ) {
            HomeSpaceModeIconButton(
                onClick = onRequestHomeSpaceMode,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun My2DContent(onRequestFullSpaceMode: () -> Unit) {
    Surface {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MainContent(modifier = Modifier.padding(48.dp))
            if (LocalSession.current != null) {
                FullSpaceModeIconButton(
                    onClick = onRequestFullSpaceMode,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun MainContent(modifier: Modifier = Modifier) {
    Text(text = stringResource(R.string.hello_android_xr), modifier = modifier)
}

@Composable
fun FullSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_full_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_full_space_mode)
        )
    }
}

@Composable
fun HomeSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_home_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_home_space_mode)
        )
    }
}

@PreviewLightDark
@Composable
fun My2dContentPreview() {
    RinStuXRTheme {
        My2DContent(onRequestFullSpaceMode = {})
    }
}

@Preview(showBackground = true)
@Composable
fun FullSpaceModeButtonPreview() {
    RinStuXRTheme {
        FullSpaceModeIconButton(onClick = {})
    }
}

@PreviewLightDark
@Composable
fun HomeSpaceModeButtonPreview() {
    RinStuXRTheme {
        HomeSpaceModeIconButton(onClick = {})
    }
}