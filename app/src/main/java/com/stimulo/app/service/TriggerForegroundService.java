package com.stimulo.app.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import com.stimulo.app.notification.NotificationHelper;
import com.stimulo.app.scheduling.ScheduleTriggerWorker;

public class TriggerForegroundService extends Service {
    private static final String TAG = "TriggerFgService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NotificationHelper.NOTIF_FOREGROUND,
                NotificationHelper.buildForegroundNotification(this));

        if (intent != null) {
            long scheduleId = intent.getLongExtra(ScheduleTriggerWorker.KEY_SCHEDULE_ID, -1);
            String name = intent.getStringExtra(ScheduleTriggerWorker.KEY_SCHEDULE_NAME);
            String espCommand = intent.getStringExtra(ScheduleTriggerWorker.KEY_ESP_COMMAND);
            Log.i(TAG, "TriggerForegroundService started for schedule " + scheduleId + " (" + name + ")");
            // TODO: Optionally perform BLE scan/connect here for ESP32 communication
            // The actual ESP command is already sent from ScheduleTriggerWorker
        }

        // Stop self after brief foreground window
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
