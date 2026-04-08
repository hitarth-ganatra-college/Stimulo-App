package com.stimulo.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * AlarmReceiver - placeholder for exact alarm support.
 * Can be wired to AlarmManager for exact timing on Android 12+
 * if WorkManager precision is insufficient.
 *
 * TODO: Wire to AlarmManager.setExactAndAllowWhileIdle() if needed
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "AlarmReceiver triggered");
        // TODO: Forward to ScheduleTriggerWorker or TriggerForegroundService
    }
}
