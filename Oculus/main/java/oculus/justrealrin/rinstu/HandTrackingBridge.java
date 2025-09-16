package oculus.justrealrin.rinstu;

import android.app.Activity;
import android.util.Log;
import android.webkit.WebView;

/**
 * HandTrackingBridge tries to integrate with Meta Spatial SDK if present.
 * It uses reflection so the app can build even if the SDK jars are not available.
 */
public class HandTrackingBridge {
    private static final String TAG = "HandTrackingBridge";
    private Activity activity;
    private WebView webView;

    public HandTrackingBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        tryInit();
    }

    private void tryInit() {
        // Use reflection to find a likely hand-tracking listener in Spatial SDK samples.
        try {
            // Example: com.meta.spatial.vr.HandTrackingFeature or similar
            Class<?> handClass = null;
            try {
                handClass = Class.forName("com.meta.spatial.hand.HandTrackingFeature");
            } catch (ClassNotFoundException e) {
                // try alternate known classes from samples
                try { handClass = Class.forName("com.meta.spatial.vr.HandTrackingFeature"); } catch (ClassNotFoundException ex) { /* ignore */ }
            }

            if (handClass != null) {
                Log.i(TAG, "Found hand tracking class: " + handClass.getName());
                // Try to find the PointerInfoSystem used in samples to inspect controller/hand state.
                try {
                    Class<?> spatialActivityManager = Class.forName("com.meta.spatial.toolkit.SpatialActivityManager");
                    // SpatialActivityManager.getVrActivity<AppSystemActivity>() is used in samples; we'll try a simpler approach:
                    Class<?> spatialMgr = Class.forName("com.meta.spatial.toolkit.SpatialActivityManager");
                    // Attempt to execute SpatialActivityManager.getAppSystemActivity() via reflection and then systemManager.findSystem(...)
                    try {
                        java.lang.reflect.Method getApp = spatialMgr.getMethod("getAppSystemActivity");
                        Object appSystemActivity = getApp.invoke(null);
                        if (appSystemActivity != null) {
                            // try to get systemManager field or method
                            try {
                                java.lang.reflect.Method getSystemManager = appSystemActivity.getClass().getMethod("getSystemManager");
                                Object systemManager = getSystemManager.invoke(appSystemActivity);
                                if (systemManager != null) {
                                    // try to find PointerInfoSystem class
                                    Class<?> pointerInfoClass = null;
                                    try {
                                        pointerInfoClass = Class.forName("com.meta.spatial.samples.premiummediasample.systems.pointerInfo.PointerInfoSystem");
                                    } catch (ClassNotFoundException e) {
                                        // fallback: search common package
                                        try { pointerInfoClass = Class.forName("com.meta.spatial.toolkit.PointerInfoSystem"); } catch (ClassNotFoundException ex) { /* ignore */ }
                                    }
                                    if (pointerInfoClass != null) {
                                        // call systemManager.findSystem(pointerInfoClass)
                                        try {
                                            java.lang.reflect.Method findSystem = systemManager.getClass().getMethod("findSystem", Class.class);
                                            Object pointerSystem = findSystem.invoke(systemManager, pointerInfoClass);
                                            if (pointerSystem != null) {
                                                Log.i(TAG, "PointerInfoSystem found via reflection. Hooking will require polling or callback registration.");
                                                // We could register a polling Runnable to inspect pointerSystem state periodically.
                                                startPointerPolling(pointerSystem);
                                            }
                                        } catch (NoSuchMethodException nsme) {
                                            Log.i(TAG, "systemManager.findSystem(Class) not available via reflection: " + nsme.getMessage());
                                        }
                                    }
                                }
                            } catch (NoSuchMethodException nsme) {
                                Log.i(TAG, "AppSystemActivity.getSystemManager() not found: " + nsme.getMessage());
                            }
                        }
                    } catch (NoSuchMethodException nsme) {
                        Log.i(TAG, "SpatialActivityManager.getAppSystemActivity() not found via reflection: " + nsme.getMessage());
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to locate system manager or pointer system", t);
                }
            } else {
                Log.i(TAG, "No Spatial SDK hand tracking class found; running without hand-tracking integration.");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Hand tracking init failed", t);
        }
    }

    // Simple polling: inspect pointerSystem for hovered entities or controller states and call bridge methods.
    private void startPointerPolling(Object pointerSystem) {
        // Start a background thread that periodically checks for button/gesture-like state.
        new Thread(() -> {
            try {
                java.lang.reflect.Method checkHover = null;
                java.lang.reflect.Method getClassMethod = pointerSystem.getClass().getMethod("getClass");
                while (true) {
                    try {
                        // Example heuristic: if pointerSystem has a method named "checkHover" or "execute" we may call it or inspect fields.
                        // Since signatures vary, keep this conservative: look for boolean methods named "isPinching" or similar.
                        try {
                            java.lang.reflect.Method isPinching = pointerSystem.getClass().getMethod("isPinching");
                            Object res = isPinching.invoke(pointerSystem);
                            if (res instanceof Boolean && ((Boolean) res)) {
                                onConfirmGesture();
                            }
                        } catch (NoSuchMethodException ignored) {
                        }

                        // Sleep a bit between polls
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Throwable t) {
                        // ignore reflection failures for specific probes
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Pointer polling failed", t);
            }
        }, "PointerPollingThread").start();
    }

    // Public methods to be called by SDK callbacks (when integrated)
    public void onConfirmGesture() {
        // Map to click on focused element
        webView.post(() -> webView.evaluateJavascript("(function(){var el=document.activeElement; if(el){el.click();}else{document.body.dispatchEvent(new MouseEvent('click'));}})()", null));
    }

    public void onBackGesture() {
        webView.post(() -> {
            if (webView.canGoBack()) webView.goBack(); else activity.finish();
        });
    }
}
