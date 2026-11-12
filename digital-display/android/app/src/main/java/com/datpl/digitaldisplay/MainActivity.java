package com.datpl.digitaldisplay;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
 * Digital Display TV app: a full-screen, always-on window around the Digital Display player page.
 * The page itself (pairing code, playlist, offline copy) lives on the website, so content and
 * player updates arrive without reinstalling the app.
 */
public class MainActivity extends Activity {

    private WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBack = 0;
    private boolean showingError = false;
    private boolean useFallback = false;      // primary address unreachable -> try the backup address
    private Updater updater;
    private volatile String linkInfo = null;   // JSON from the player page: TV name, business, account e-mail
    private static final long UPDATE_EVERY_MS = 6 * 60 * 60 * 1000L;   // automatic update check every 6 hours
    private final Runnable autoUpdate = new Runnable() {
        @Override public void run() { updater.check(false); handler.postDelayed(this, UPDATE_EVERY_MS); }
    };

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
        s.setUserAgentString(s.getUserAgentString() + " DigitalDisplay/" + appVersion());

        web.addJavascriptInterface(new Bridge(), "DNApp");
        web.setWebViewClient(new Client());
        setContentView(web);
        hideSystemUi();

        web.loadUrl(playerUrl());
        askForAutoStartPermission();

        updater = new Updater(this);
        handler.postDelayed(autoUpdate, 90 * 1000L);   // first check shortly after start
        Toast.makeText(this, "Digital Display " + appVersion(), Toast.LENGTH_SHORT).show();
    }

    private String playerUrl() { return getString(useFallback ? R.string.player_url_fallback : R.string.player_url); }

    private boolean isPlayer(String url) {
        return url != null && (url.startsWith(getString(R.string.player_url)) || url.startsWith(getString(R.string.player_url_fallback)));
    }

    /** The TV's identity lives in the app, so it stays the same whichever address the page loads from. */
    private String deviceSecret() {
        SharedPreferences p = getSharedPreferences("dn", MODE_PRIVATE);
        String sec = p.getString("secret", null);
        if (sec == null || sec.length() < 32) {
            byte[] b = new byte[32];
            new java.security.SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            sec = sb.toString();
            p.edit().putString("secret", sec).apply();
        }
        return sec;
    }

    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    /** Exposed to the page as window.DNApp */
    private class Bridge {
        @JavascriptInterface public String version() { return appVersion(); }
        @JavascriptInterface public String deviceSecret() { return MainActivity.this.deviceSecret(); }
        @JavascriptInterface public void checkUpdate() { handler.post(() -> updater.check(true)); }
        @JavascriptInterface public void setInfo(String json) { linkInfo = json; }
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && isPlayer(url)) showError();
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
                + "<div><div style='font-size:5vmin;font-weight:700;color:#fff'>Digital Display</div>"
                + "<div style='font-size:3.2vmin;margin-top:2vmin'>Connecting to the internet…</div>"
                + "<div style='font-size:2.2vmin;color:#888;margin-top:2vmin'>Please check the TV's Wi-Fi connection</div></div></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        // alternate between the main and the backup address until one answers
        handler.postDelayed(() -> { showingError = false; useFallback = !useFallback; web.loadUrl(playerUrl()); }, 15000);
    }

    // ---------- auto start after the TV powers on ----------
    private void askForAutoStartPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // older Android: starting after a full restart needs nothing, but standby/Home need "Open with the TV"
            SharedPreferences p = getSharedPreferences("dn", MODE_PRIVATE);
            if (!isHomeApp() && !p.getBoolean("homeHint", false)) {
                p.edit().putBoolean("homeHint", true).apply();
                handler.postDelayed(() -> { if (!isFinishing()) offerHome(); }, 3000);
            }
            return;
        }
        if (Settings.canDrawOverlays(this)) return;
        SharedPreferences p = getSharedPreferences("dn", MODE_PRIVATE);
        if (p.getInt("askedOverlay", 0) >= 3) return;                // don't nag forever
        p.edit().putInt("askedOverlay", p.getInt("askedOverlay", 0) + 1).apply();
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            new AlertDialog.Builder(this)
                .setTitle("Start with the TV?")
                .setMessage("On the next screen, allow Digital Display to \"Display over other apps\". "
                        + "Then the app opens by itself every time the TV is turned on.")
                .setPositiveButton("OK", (d, w) -> openOverlaySettings())
                .setNegativeButton("Later", null)
                .show();
        }, 2500);
    }

    private void offerHome() {
        new AlertDialog.Builder(this)
            .setTitle("Open Digital Display with the TV?")
            .setMessage("Make Digital Display open by itself whenever the TV is turned on.\n\nYou can change this any time: hold OK on the remote › Open with the TV.")
            .setPositiveButton("Yes", (d, w) -> becomeHome())
            .setNegativeButton("Not now", null)
            .show();
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
            catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "Settings › Apps › Special app access › Display over other apps › Digital Display", Toast.LENGTH_LONG).show();
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

    // ---------- remote menu: MENU key, or hold OK for 2 seconds ----------
    private AlertDialog menu;
    private boolean okHeldHandled = false;

    private void showMenu() {
        if (menu != null && menu.isShowing()) return;
        boolean home = isHomeApp();
        String[] items = {
            "Account & TV info",
            "Reload display",
            "Check for updates",
            home ? "Open with the TV: ON ✓  (turn off)" : "Open with the TV: OFF  (turn on)",
            "TV settings",
            "Exit app",
        };
        menu = new AlertDialog.Builder(this)
            .setTitle("Digital Display " + appVersion() + " — " + updater.status())
            .setItems(items, (d, which) -> {
                if (which == 0) showInfo();
                else if (which == 1) web.reload();
                else if (which == 2) updater.check(true);
                else if (which == 3) { if (home) stopBeingHome(); else becomeHome(); }
                else if (which == 4) openSettings(Settings.ACTION_SETTINGS);
                else finish();
            })
            .setNegativeButton("Close", null)
            .create();
        menu.show();
    }

    /** Which account this TV belongs to (from the player page), plus versions */
    private void showInfo() {
        StringBuilder m = new StringBuilder();
        try {
            org.json.JSONObject j = new org.json.JSONObject(linkInfo == null ? "{}" : linkInfo);
            if (j.optBoolean("linked")) {
                m.append("Account: ").append(j.optString("account", "-")).append('\n');
                m.append("Business: ").append(j.optString("business", "-")).append('\n');
                m.append("TV name: ").append(j.optString("tv", "-")).append('\n');
                m.append("Playing: ").append(j.optString("playlist", "-")).append("\n\n");
            } else if (linkInfo != null) {
                m.append("Not linked to an account yet.\nCode on screen: ").append(j.optString("code", "-")).append("\n\n");
            } else {
                m.append("Connecting… try again in a few seconds.\n\n");
            }
            m.append("App ").append(appVersion()).append(" (").append(updater.status()).append(")\n");
            if (j.has("player")) m.append("Player ").append(j.optString("player")).append('\n');
        } catch (Exception e) { m.append("App ").append(appVersion()); }
        m.append("Open with the TV: ").append(isHomeApp() ? "ON" : "OFF").append('\n');
        m.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE);
        new AlertDialog.Builder(this)
            .setTitle("Account & TV info")
            .setMessage(m.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    // ---------- "Open with the TV": Digital Display as the TV's home screen ----------
    private ComponentName homeAlias() { return new ComponentName(this, getPackageName() + ".HomeAlias"); }

    /** true when the TV's Home button / power-on opens this app */
    private boolean isHomeApp() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
    }

    private void becomeHome() {
        getPackageManager().setComponentEnabledSetting(homeAlias(),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        new AlertDialog.Builder(this)
            .setTitle("Open Digital Display with the TV")
            .setMessage("On the next screen choose \"Digital Display\", then \"Always\".\n\n"
                    + "After that the TV opens Digital Display when it is turned on, wakes from standby or Home is pressed.\n"
                    + "To use other TV apps later: hold OK › TV settings, or turn this option off.")
            .setPositiveButton("OK", (d, w) -> {
                try {
                    // the "choose home app" screen appears because a second home app is now available
                    startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ignored) { }
                handler.postDelayed(() -> {
                    if (!isHomeApp()) {
                        Toast.makeText(this, "Not set yet: Settings › Apps › Default apps › Home app › Digital Display", Toast.LENGTH_LONG).show();
                        openSettings(Build.VERSION.SDK_INT >= 21 ? Settings.ACTION_HOME_SETTINGS : Settings.ACTION_SETTINGS);
                    }
                }, 4000);
            })
            .setNegativeButton("Cancel", (d, w) -> getPackageManager().setComponentEnabledSetting(homeAlias(),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP))
            .show();
    }

    private void stopBeingHome() {
        getPackageManager().setComponentEnabledSetting(homeAlias(),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        Toast.makeText(this, "Turned off — the TV's own home screen opens on power-on again", Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) { }
    }

    private void openSettings(String action) {
        try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (ActivityNotFoundException ignored) { }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) okHeldHandled = false;
                else if (!okHeldHandled && e.getEventTime() - e.getDownTime() > 2000) {
                    okHeldHandled = true;
                    showMenu();
                    return true;
                }
                if (okHeldHandled) return true;
            } else if (e.getAction() == KeyEvent.ACTION_UP && okHeldHandled) {
                return true;   // don't pass the long press on to the page
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // BACK twice within 2 s leaves the app, so a stray press doesn't stop the display
            long now = System.currentTimeMillis();
            if (now - lastBack < 2000) {
                if (isHomeApp()) { showMenu(); return true; }   // home screen can't close; offer the menu instead
                finish(); return true;
            }
            lastBack = now;
            Toast.makeText(this, "Press BACK again to exit", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showMenu();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_F5) { web.reload(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
