package com.datpl.displaynow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * Display NOW TV app: a full-screen, always-on window around the Display NOW player page.
 * The page itself (pairing code, playlist, offline copy) lives on the website, so content and
 * player updates arrive without reinstalling the app.
 */
public class MainActivity extends Activity {

    private WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBack = 0;
    private boolean showingError = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                 // keeps the TV's identity and last playlist
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // videos start by themselves
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        s.setUserAgentString(s.getUserAgentString() + " DisplayNOW/" + appVersion());

        web.addJavascriptInterface(new Bridge(), "DNApp");
        web.setWebViewClient(new Client());
        setContentView(web);
        hideSystemUi();

        web.loadUrl(playerUrl());
        askForAutoStartPermission();
    }

    private String playerUrl() { return getString(R.string.player_url); }

    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    /** Exposed to the page as window.DNApp */
    private class Bridge {
        @JavascriptInterface public String version() { return appVersion(); }
        @JavascriptInterface public String model() { return Build.MANUFACTURER + " " + Build.MODEL; }
    }

    private class Client extends WebViewClient {
        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && req.isForMainFrame()) showError();
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int code, String desc, String url) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && url != null && url.startsWith(playerUrl())) showError();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            return false; // stay inside the app
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // The page crashed (low memory etc.): start fresh instead of showing a frozen screen
            handler.post(MainActivity.this::recreate);
            return true;
        }
    }

    /** First start without internet (no saved copy yet): show a message and keep retrying. */
    private void showError() {
        if (showingError) return;
        showingError = true;
        String html = "<html><body style='margin:0;background:#0b0c10;color:#ddd;font-family:sans-serif;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;text-align:center'>"
                + "<div><div style='font-size:5vmin;font-weight:700;color:#fff'>Display NOW</div>"
                + "<div style='font-size:3.2vmin;margin-top:2vmin'>इंटरनेट से कनेक्ट हो रहा है…<br>Connecting to the internet…</div>"
                + "<div style='font-size:2.2vmin;color:#888;margin-top:2vmin'>TV का Wi-Fi देखें · Check the TV's Wi-Fi</div></div></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        handler.postDelayed(() -> { showingError = false; web.loadUrl(playerUrl()); }, 15000);
    }

    // ---------- auto start after the TV powers on ----------
    private void askForAutoStartPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;   // older Android: boot start works without it
        if (Settings.canDrawOverlays(this)) return;
        SharedPreferences p = getSharedPreferences("dn", MODE_PRIVATE);
        if (p.getInt("askedOverlay", 0) >= 3) return;                // don't nag forever
        p.edit().putInt("askedOverlay", p.getInt("askedOverlay", 0) + 1).apply();
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            new AlertDialog.Builder(this)
                .setTitle("TV चालू होते ही ऐप खुले? · Start with the TV?")
                .setMessage("अगली स्क्रीन पर Display NOW को \"Display over other apps\" की अनुमति दें। "
                        + "इसके बाद TV चालू होते ही ऐप अपने-आप खुलेगा।\n\n"
                        + "On the next screen allow Display NOW to \"Display over other apps\" so it opens by itself when the TV turns on.")
                .setPositiveButton("ठीक है · OK", (d, w) -> openOverlaySettings())
                .setNegativeButton("बाद में · Later", null)
                .show();
        }, 2500);
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
            catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "Settings › Apps › Special app access › Display over other apps › Display NOW", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- kiosk behaviour ----------
    private void hideSystemUi() {
        web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); hideSystemUi(); }

    @Override
    protected void onPause() { web.onPause(); super.onPause(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // BACK twice within 2 s leaves the app, so a stray press doesn't stop the display
            long now = System.currentTimeMillis();
            if (now - lastBack < 2000) { finish(); return true; }
            lastBack = now;
            Toast.makeText(this, "बाहर जाने के लिए फिर से BACK दबाएँ · Press BACK again to exit", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_F5) {
            web.reload();                                  // MENU = reload the display
            Toast.makeText(this, "Reloading…", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
