package com.juanpacas.stackenmarcha;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONObject;

/** Muestra el curso (assets/index.html) sin internet y le da voz nativa, también con la pantalla bloqueada. */
public class MainActivity extends Activity implements Speech.Events {

    private WebView web;
    private Speech speech;
    private boolean askedNotifications = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int bg = night ? 0xFF0C131C : 0xFFEEF1F5;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        web = new WebView(this);
        web.setBackgroundColor(bg);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        setupSystemBars(root, night, bg);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);

        speech = Speech.get(this);
        speech.events = this;

        web.addJavascriptInterface(new Bridge(), "AndroidTTS");
        web.setWebChromeClient(new WebChromeClient() {
            // Los errores de la página aparecen en logcat con la etiqueta StackEnMarcha
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.i("StackEnMarcha", "console: " + m.message() + " @" + m.lineNumber());
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri uri = req.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    // Las fuentes y enlaces externos se abren en el navegador del celular
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                    return true;
                }
                return false;
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }

        if (state != null) web.restoreState(state);
        else web.loadUrl("file:///android_asset/index.html");
    }

    private void setupSystemBars(View root, boolean night, int bg) {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(night ? 0 : mask, mask);
            }
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                return WindowInsets.CONSUMED;
            });
        } else {
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            if (!night) {
                int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        }
    }

    private void js(String code) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    // Avisos del servicio de voz hacia la pantalla
    @Override public void onSegment(int index) { js("window.__qProgress && window.__qProgress(" + index + ")"); }
    @Override public void onQueueDone() { js("window.__qDone && window.__qDone()"); }
    @Override public void onQueueStopped() { js("window.__qStopped && window.__qStopped()"); }
    @Override public void onSingleDone(String id) { js("window.__ttsDone && window.__ttsDone(" + JSONObject.quote(id) + ")"); }

    /** El botón atrás navega dentro del curso; en la pantalla de inicio sale de la app. */
    private void handleBack() {
        web.evaluateJavascript("(window.__back ? window.__back() : false)", v -> {
            // moveTaskToBack en vez de finish: si está sonando una lección, sigue sonando
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void askNotificationsOnce() {
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askedNotifications = true;
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) speech.refreshNotification();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (speech.events == this) speech.events = null;
        if (isFinishing()) speech.stop();
        if (web != null) web.destroy();
        web = null;
        super.onDestroy();
    }

    /** Lo que el curso puede pedirle al teléfono desde JavaScript. */
    private class Bridge {
        @JavascriptInterface
        public boolean speak(String text, float rate, String id) {
            return speech.speakOne(text, rate, id);
        }

        @JavascriptInterface
        public boolean playQueue(String json, float rate, String title) {
            runOnUiThread(MainActivity.this::askNotificationsOnce);
            return speech.playQueue(json, rate, title);
        }

        @JavascriptInterface
        public void stop() {
            speech.stop();
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    }
}
