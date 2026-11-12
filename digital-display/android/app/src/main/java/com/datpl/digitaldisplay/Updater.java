package com.datpl.digitaldisplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * App updates from the Digital Display website.
 * The website publishes version.json (written by the build) next to DigitalDisplay.apk.
 * When it lists a higher versionCode, the app downloads the new APK and hands it to Android's installer,
 * which asks for one "Install" press on the TV (Android does not allow ordinary apps to update silently).
 */
final class Updater {

    private final Activity act;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean busy = false;
    private AlertDialog dialog;
    /** Result of the last check: latest version on the website (0 / null = not checked yet). */
    volatile int latestCode = 0;
    volatile String latestName = null;

    /** e.g. "up to date" / "update 1.2.0 available" / "not checked yet" */
    String status() {
        if (latestName == null) return "not checked yet";
        return latestCode > currentCode(act) ? "update " + latestName + " available" : "latest version ✓";
    }

    Updater(Activity act) { this.act = act; }

    /** Site roots to try, e.g. https://display.datpl.com/ */
    private String[] roots() {
        return new String[] {
            root(act.getString(R.string.player_url)),
            root(act.getString(R.string.player_url_fallback))
        };
    }

    private static String root(String playerUrl) {
        return playerUrl.endsWith("tv/") ? playerUrl.substring(0, playerUrl.length() - 3) : playerUrl;
    }

    static int currentCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) { return 0; }
    }

    static String currentName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    /**
     * @param manual true when someone asked from the remote: always answer, and offer to install right away.
     *               false for the automatic check: download quietly and offer to install for one minute.
     */
    void check(final boolean manual) {
        if (busy) { if (manual) toast("Already checking for updates…"); return; }
        busy = true;
        if (manual) toast("Checking for updates…");
        new Thread(() -> {
            JSONObject info = null;
            String base = null;
            for (String r : roots()) {
                try {
                    info = new JSONObject(new String(get(r + "version.json?t=" + System.currentTimeMillis()), "UTF-8"));
                    base = r;
                    break;
                } catch (Exception ignored) { }
            }
            if (info == null) { done(manual ? "Could not reach the update server. Check the internet." : null); return; }

            int latest = info.optInt("versionCode", 0);
            final String name = info.optString("versionName", "?");
            latestCode = latest;
            latestName = name;
            final String notes = info.optString("notes", "");
            if (latest <= currentCode(act)) { done(manual ? "Digital Display is up to date (version " + currentName(act) + ")." : null); return; }

            try {
                if (manual) toastUi("Downloading version " + name + "…");
                String apkUrl = base + info.optString("apk", "DigitalDisplay.apk");
                File f = new File(act.getCacheDir(), "update.apk");
                download(apkUrl, f);
                ui.post(() -> { busy = false; offer(f, name, notes, manual); });
            } catch (Exception e) {
                done(manual ? "Download failed: " + e.getMessage() : null);
            }
        }).start();
    }

    private void done(String msg) {
        ui.post(() -> { busy = false; if (msg != null) toast(msg); });
    }

    private void offer(File apk, String name, String notes, boolean manual) {
        if (act.isFinishing()) return;
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        String msg = "Version " + name + " is ready (you have " + currentName(act) + ")."
                + (notes.isEmpty() ? "" : "\n\n" + notes)
                + "\n\nThe display restarts by itself after installing.";
        dialog = new AlertDialog.Builder(act)
                .setTitle("Update Digital Display?")
                .setMessage(msg)
                .setPositiveButton("Install now", (d, w) -> install(apk))
                .setNegativeButton("Later", null)
                .create();
        dialog.show();
        // An unattended TV should not keep a dialog over the display: close it after a minute.
        if (!manual) ui.postDelayed(() -> { if (dialog != null && dialog.isShowing()) dialog.dismiss(); }, 60000);
    }

    void install(File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
                toast("Allow Digital Display to install apps, then choose Check for updates again.");
                try {
                    act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + act.getPackageName())));
                } catch (ActivityNotFoundException e) {
                    toast("Settings › Apps › Special app access › Install unknown apps › Digital Display");
                }
                return;
            }
            PackageInstaller pi = act.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(act.getPackageName());
            int id = pi.createSession(params);
            PackageInstaller.Session s = pi.openSession(id);
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("app.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent done = PendingIntent.getBroadcast(act, id, new Intent(act, InstallReceiver.class), flags);
            s.commit(done.getIntentSender());
            s.close();
        } catch (Exception e) {
            toast("Could not install the update: " + e.getMessage());
        }
    }

    // ---------- small helpers ----------
    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        if (c.getResponseCode() != 200) { c.disconnect(); throw new Exception("HTTP " + c.getResponseCode()); }
        return c;
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally { c.disconnect(); }
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally { c.disconnect(); }
    }

    private void toast(String m) { Toast.makeText(act, m, Toast.LENGTH_LONG).show(); }
    private void toastUi(String m) { ui.post(() -> toast(m)); }
}
