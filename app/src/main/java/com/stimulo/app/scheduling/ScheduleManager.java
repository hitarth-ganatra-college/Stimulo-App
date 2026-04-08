package com.stimulo.app.scheduling;

import android.content.Context;
import android.util.Log;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.stimulo.app.data.entity.ScheduleEntity;
import java.util.concurrent.TimeUnit;

public class ScheduleManager {
    private static final String TAG = "ScheduleManager";
    private static final String WORK_TAG_PREFIX = "schedule_";

    private final Context context;

    public ScheduleManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void schedule(ScheduleEntity schedule) {
        if (!schedule.isActive) return;

        long now = System.currentTimeMillis();
        long delay = schedule.triggerTimeMillis - now;

        if (delay < 0) {
            Log.w(TAG, "Schedule " + schedule.id + " trigger time is in the past, skipping");
            return;
        }

        Data inputData = new Data.Builder()
                .putLong(ScheduleTriggerWorker.KEY_SCHEDULE_ID, schedule.id)
                .putString(ScheduleTriggerWorker.KEY_SCHEDULE_NAME, schedule.name)
                .putString(ScheduleTriggerWorker.KEY_SCHEDULE_DESC, schedule.description)
                .putString(ScheduleTriggerWorker.KEY_REPEAT_TYPE, schedule.repeatType)
                .putInt(ScheduleTriggerWorker.KEY_REMAINING_COUNT, schedule.remainingCount)
                .putInt(ScheduleTriggerWorker.KEY_HOUR, schedule.hourOfDay)
                .putInt(ScheduleTriggerWorker.KEY_MINUTE, schedule.minuteOfHour)
                .putString(ScheduleTriggerWorker.KEY_ESP_COMMAND, schedule.espCommand)
                .putString(ScheduleTriggerWorker.KEY_WEEKDAYS, schedule.weekdays)
                .putInt(ScheduleTriggerWorker.KEY_INTERVAL_MINUTES, schedule.intervalMinutes)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScheduleTriggerWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(WORK_TAG_PREFIX + schedule.id)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_TAG_PREFIX + schedule.id,
                ExistingWorkPolicy.REPLACE,
                workRequest
        );

        Log.i(TAG, "Scheduled work for schedule " + schedule.id + " in " + delay + "ms");
    }

    public void cancel(long scheduleId) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG_PREFIX + scheduleId);
        Log.i(TAG, "Cancelled work for schedule " + scheduleId);
    }
}
