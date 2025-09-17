package xr.cardboard.rinstu

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONException
import kotlin.math.roundToInt
import kotlin.math.tan

class CardboardWebviewActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var webViewLeft: WebView
    private lateinit var webViewRight: WebView

    private lateinit var sensorManager: SensorManager
    private var gyroscopeSensor: Sensor? = null
    private var gravitySensor: Sensor? = null

    private val gravityValues = FloatArray(3)
    private val gyroscopeValues = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val TAG = "CardboardWebviewActivity"

    private var clickableElements: JSONArray? = null

    private var lastGazedElementId: String? = null
    private var gazeStartTime: Long = 0L

    private val GAZE_DWELL_TIME_MS = 2000L
    private val FIELD_OF_VIEW_Y = 60.0
    private val FIELD_OF_VIEW_X = 60.0

    private var isProgrammaticScroll = false
    private var isProgrammaticUrlLoading = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_cardboard_webview)

        webViewLeft = findViewById(R.id.webview_left)
        webViewRight = findViewById(R.id.webview_right)

        setupWebView(webViewLeft)
        setupWebView(webViewRight)

        webViewLeft.loadUrl("https://justrealrin.github.io/")
        webViewRight.loadUrl("https://justrealrin.github.io/")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

        if (gyroscopeSensor == null) {
            Log.e(TAG, "Gyroscope sensor not available.")
        }
        if (gravitySensor == null) {
            Log.e(TAG, "Gravity sensor not available.")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            addJavascriptInterface(JavaScriptInterface(), "Android")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false

                    if (isProgrammaticUrlLoading) {
                        isProgrammaticUrlLoading = false
                        return true
                    }

                    isProgrammaticUrlLoading = true
                    if (webView == webViewLeft) {
                        webViewRight.loadUrl(url)
                    } else {
                        webViewLeft.loadUrl(url)
                    }
                    isProgrammaticUrlLoading = false
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "onPageFinished for WebView: $url")
                    view?.evaluateJavascript("""
                        javascript: (function() {
                            var links = document.getElementsByTagName('a');
                            var data = [];
                            for (var i = 0; i < links.length; i++) {
                                var link = links[i];
                                data.push({
                                    id: link.id || '',
                                    tagName: link.tagName,
                                    href: link.href,
                                    offsetLeft: link.offsetLeft,
                                    offsetTop: link.offsetTop,
                                    offsetWidth: link.offsetWidth,
                                    offsetHeight: link.offsetHeight
                                });
                            }
                            Android.receiveClickableElements(JSON.stringify(data));
                        })()
                    """, null)
                }
            }

            setOnScrollChangeListener { _, scrollX, scrollY, _, _ ->
                if (isProgrammaticScroll) {
                    isProgrammaticScroll = false
                    return@setOnScrollChangeListener
                }

                isProgrammaticScroll = true
                if (webView == webViewLeft) {
                    webViewRight.scrollTo(scrollX, scrollY)
                } else {
                    webViewLeft.scrollTo(scrollX, scrollY)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        Log.d(TAG, "Sensors registered.")
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Sensors unregistered.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, gyroscopeValues, 0, gyroscopeValues.size)
            }
            Sensor.TYPE_GRAVITY -> {
                System.arraycopy(event.values, 0, gravityValues, 0, gravityValues.size)
            }
        }

        if (webViewLeft.width == 0 || webViewLeft.height == 0) {
            Log.d(TAG, "WebView dimensions not ready. Width: ${webViewLeft.width}, Height: ${webViewLeft.height}")
            return
        }

        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, gyroscopeValues)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)


            val pitchDegrees = Math.toDegrees(orientationAngles[1].toDouble())
            val yawDegrees = Math.toDegrees(orientationAngles[0].toDouble())

            val normalizedYaw = yawDegrees
            val normalizedPitch = pitchDegrees

            val gazeX = (((normalizedYaw / (FIELD_OF_VIEW_X / 2)) * (webViewLeft.width / 2)) + (webViewLeft.width / 2)).roundToInt()
            val gazeY = (((normalizedPitch / (FIELD_OF_VIEW_Y / 2)) * (webViewLeft.height / 2)) + (webViewLeft.height / 2)).roundToInt()

            Log.d(TAG, "Orientation: Azimuth: ${yawDegrees.roundToInt()}, Pitch: ${pitchDegrees.roundToInt()}")
            Log.d(TAG, "Gaze X: $gazeX, Gaze Y: $gazeY. WebView Width: ${webViewLeft.width}, Height: ${webViewLeft.height}")

            performGazeInteraction(gazeX, gazeY)
        } else {
            Log.d(TAG, "getRotationMatrix failed. Gravity: ${gravityValues.contentToString()}, Gyro: ${gyroscopeValues.contentToString()}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun performGazeInteraction(gazeX: Int, gazeY: Int) {
        val currentGazedElement = findGazedElement(gazeX, gazeY)
        val currentGazedElementIdentifier = if (currentGazedElement != null) {
            currentGazedElement.optString("id").ifEmpty { currentGazedElement.optString("href") }
        } else null

        if (currentGazedElementIdentifier == null) {
            if (lastGazedElementId != null) {
                Log.d(TAG, "Gaze left element: $lastGazedElementId")
            }
            lastGazedElementId = null
            gazeStartTime = 0L
        } else {
            if (currentGazedElementIdentifier == lastGazedElementId) {
                val dwellTime = System.currentTimeMillis() - gazeStartTime
                Log.d(TAG, "Gazing at $currentGazedElementIdentifier for ${dwellTime}ms")
                if (dwellTime >= GAZE_DWELL_TIME_MS) {
                    Log.d(TAG, "Clicking element: $currentGazedElementIdentifier")
                    simulateClick(currentGazedElementIdentifier) 
                    lastGazedElementId = null
                    gazeStartTime = 0L
                }
            } else {
                Log.d(TAG, "Gaze moved to new element: $currentGazedElementIdentifier")
                lastGazedElementId = currentGazedElementIdentifier
                gazeStartTime = System.currentTimeMillis()
            }
        }
    }

    private fun findGazedElement(gazeX: Int, gazeY: Int): org.json.JSONObject? {
        clickableElements?.let { links ->
            for (i in 0 until links.length()) {
                val link = links.getJSONObject(i)
                val offsetLeft = link.getInt("offsetLeft")
                val offsetTop = link.getInt("offsetTop")
                val offsetWidth = link.getInt("offsetWidth")
                val offsetHeight = link.getInt("offsetHeight")

                val scrolledOffsetLeft = offsetLeft - webViewLeft.scrollX
                val scrolledOffsetTop = offsetTop - webViewLeft.scrollY

                if (gazeX >= scrolledOffsetLeft && gazeX <= scrolledOffsetLeft + offsetWidth &&
                    gazeY >= scrolledOffsetTop && gazeY <= scrolledOffsetTop + offsetHeight) {
                    Log.d(TAG, "Hit: ${link.optString("id").ifEmpty { link.optString("href") }} at ($gazeX, $gazeY) against element bounds [($scrolledOffsetLeft, $scrolledOffsetTop), (${scrolledOffsetLeft + offsetWidth}, ${scrolledOffsetTop + offsetHeight})]")
                    return link
                }
            }
        }
        return null
    }

    private fun simulateClick(elementIdentifier: String) {
        if (elementIdentifier.startsWith("http") || elementIdentifier.startsWith("https")) {
            webViewLeft.loadUrl(elementIdentifier)
            webViewRight.loadUrl(elementIdentifier)
            Log.d(TAG, "Navigating to URL: $elementIdentifier")
        } else if (elementIdentifier.isNotEmpty()) {
            val script = "javascript:document.getElementById('$elementIdentifier').click();"
            webViewLeft.evaluateJavascript(script, null)
            webViewRight.evaluateJavascript(script, null)
            Log.d(TAG, "Clicking element with ID: $elementIdentifier")
        }
    }

    private inner class JavaScriptInterface {
        @JavascriptInterface
        @Suppress("unused")
        fun receiveClickableElements(json: String) {
            try {
                clickableElements = JSONArray(json)
                Log.d(TAG, "Received ${clickableElements?.length()} clickable elements.")
            } catch (e: JSONException) {
                Log.e(TAG, "Error parsing clickable elements JSON: ${e.message}")
            }
        }
    }
}