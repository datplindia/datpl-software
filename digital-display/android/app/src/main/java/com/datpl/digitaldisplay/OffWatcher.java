package com.datpl.digitaldisplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Runs while the display is switched OFF from the dashboard: every 30 s it asks the server whether the
 * display was switched ON again, and then opens Digital Display. (The player page does this job while ON.)
 */
public class OffWatcher extends Service {

    private volatile boolean running = false;

    static void start(Context c) {
        Intent i = new Intent(c, OffWatcher.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }

    static void stop(Context c) { c.stopService(new Intent(c, OffWatcher.class)); }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Never let this helper crash the app (Android 14 is strict about background services):
        // if Android refuses, the app simply checks again the next time it is opened.
        try {
            Notification n;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel("dn", "Digital Display", NotificationManager.IMPORTANCE_MIN));
                n = new Notification.Builder(this, "dn").setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Digital Display is switched off").setContentText("Turn it on from the dashboard").build();
            } else {
                n = new Notification.Builder(this).setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Digital Display is switched off").setPriority(Notification.PRIORITY_MIN).build();
            }
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(1, n);
        } catch (Throwable t) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            new Thread(this::loop).start();
        }
        return START_STICKY;
    }

    private void loop() {
        while (running) {
            try {
                String mode = ask();
                if ("on".equals(mode)) {
                    running = false;
                    Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(open);
                    stopSelf();
                    return;
                }
            } catch (Exception ignored) { }
            try { Thread.sleep(30000); } catch (InterruptedException e) { return; }
        }
    }

    /** One check-in with the server; returns "on"/"off" (or null), and remembers mode + PIN. */
    private String ask() throws Exception {
        SharedPreferences p = getSharedPreferences("dn", MODE_PRIVATE);
        String secret = p.getString("secret", null);
        if (secret == null) return null;
        String version = "?";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        JSONObject body = new JSONObject();
        body.put("p_secret", secret);
        body.put("p_info", new JSONObject().put("app", version).put("off", true));
        HttpURLConnection c = (HttpURLConnection) new URL(getString(R.string.supabase_url) + "/rest/v1/rpc/dn_device_hello").openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("apikey", getString(R.string.supabase_key));
        c.setRequestProperty("Authorization", "Bearer " + getString(R.string.supabase_key));
        try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes("UTF-8")); }
        if (c.getResponseCode() != 200) { c.disconnect(); return null; }
        StringBuilder sb = new StringBuilder();
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
        } finally { c.disconnect(); }
        JSONObject r = new JSONObject(sb.toString());
        if (!"paired".equals(r.optString("status"))) return "on";   // unlinked TVs show their code screen
        String mode = r.optString("mode", "on");
        p.edit().putString("mode", mode).putString("pin", r.optString("pin", p.getString("pin", ""))).apply();
        return mode;
    }

    @Override
    public void onDestroy() { running = false; super.onDestroy(); }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
