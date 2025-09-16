package oculus.justrealrin.rinstu;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import android.widget.FrameLayout;
import android.view.InputDevice;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private HandTrackingBridge handBridge;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        setContentView(root);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(webView.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
        }

        webView.loadUrl("https://justrealrin.github.io/");

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        // Initialize hand tracking bridge (will attempt to wire into Spatial SDK via reflection)
        handBridge = new HandTrackingBridge(this, webView);
        Log.i("MainActivity", "HandTrackingBridge initialized");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BUTTON_A) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) || keyCode == KeyEvent.KEYCODE_ENTER) {
            webView.evaluateJavascript("(function(){var el=document.activeElement; if(el){el.click();}else{var evt=new MouseEvent('click'); document.body.dispatchEvent(evt);} })()", null);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
            float x = event.getAxisValue(android.view.MotionEvent.AXIS_X);
            float y = event.getAxisValue(android.view.MotionEvent.AXIS_Y);
            if (Math.abs(x) > 0.5f) {
                if (x > 0) {
                    webView.evaluateJavascript("(function(){var e=new KeyboardEvent('keydown',{key:'ArrowRight'});document.activeElement&&document.activeElement.dispatchEvent(e);})()", null);
                } else {
                    webView.evaluateJavascript("(function(){var e=new KeyboardEvent('keydown',{key:'ArrowLeft'});document.activeElement&&document.activeElement.dispatchEvent(e);})()", null);
                }
            }
            if (Math.abs(y) > 0.5f) {
                if (y > 0) {
                    webView.evaluateJavascript("(function(){var e=new KeyboardEvent('keydown',{key:'ArrowDown'});document.activeElement&&document.activeElement.dispatchEvent(e);})()", null);
                } else {
                    webView.evaluateJavascript("(function(){var e=new KeyboardEvent('keydown',{key:'ArrowUp'});document.activeElement&&document.activeElement.dispatchEvent(e);})()", null);
                }
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
