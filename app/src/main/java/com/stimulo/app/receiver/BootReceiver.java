package com.stimulo.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.stimulo.app.data.db.AppDatabase;
import com.stimulo.app.data.entity.ScheduleEntity;
import com.stimulo.app.scheduling.ScheduleManager;
import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            Log.i(TAG, "Boot detected - rescheduling active schedules");
            rescheduleAll(context);
        }
    }

    private void rescheduleAll(Context context) {
        new Thread(() -> {
            List<ScheduleEntity> schedules =
                    AppDatabase.getInstance(context).scheduleDao().getAllActiveSchedulesSync();
            ScheduleManager manager = new ScheduleManager(context);
            long now = System.currentTimeMillis();
            for (ScheduleEntity schedule : schedules) {
                if (schedule.triggerTimeMillis > now) {
                    manager.schedule(schedule);
                    Log.i(TAG, "Re-registered schedule " + schedule.id);
                } else {
                    Log.w(TAG, "Skipping past-due schedule " + schedule.id);
                }
            }
        }).start();
    }
}
