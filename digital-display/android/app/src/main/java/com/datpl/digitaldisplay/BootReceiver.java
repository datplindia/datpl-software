package com.datpl.digitaldisplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Opens Digital Display when the TV finishes starting (and after the app is updated). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            context.startActivity(open);
        } catch (Exception ignored) {
            // Android 10+ blocks this until "Display over other apps" is allowed; the app asks for it on first start.
        }
    }
}
