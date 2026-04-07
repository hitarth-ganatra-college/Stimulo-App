package com.stimulo.app.scheduling;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.stimulo.app.data.db.AppDatabase;
import com.stimulo.app.data.entity.ScheduleEntity;
import com.stimulo.app.data.entity.TriggerLogEntity;
import com.stimulo.app.esp.Esp32Communicator;
import com.stimulo.app.esp.StubEsp32Communicator;
import com.stimulo.app.model.RepeatType;
import com.stimulo.app.notification.NotificationHelper;
import com.stimulo.app.service.TriggerForegroundService;
import java.util.Calendar;

public class ScheduleTriggerWorker extends Worker {
    private static final String TAG = "ScheduleTriggerWorker";

    public static final String KEY_SCHEDULE_ID = "schedule_id";
    public static final String KEY_SCHEDULE_NAME = "schedule_name";
    public static final String KEY_SCHEDULE_DESC = "schedule_desc";
    public static final String KEY_REPEAT_TYPE = "repeat_type";
    public static final String KEY_REMAINING_COUNT = "remaining_count";
    public static final String KEY_HOUR = "hour";
    public static final String KEY_MINUTE = "minute";
    public static final String KEY_ESP_COMMAND = "esp_command";

    private final Esp32Communicator esp32Communicator;

    public ScheduleTriggerWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        // TODO: Replace StubEsp32Communicator with actual implementation when ESP32 is ready
        this.esp32Communicator = new StubEsp32Communicator();
    }

    @NonNull
    @Override
    public Result doWork() {
        long scheduleId = getInputData().getLong(KEY_SCHEDULE_ID, -1);
        String name = getInputData().getString(KEY_SCHEDULE_NAME);
        String desc = getInputData().getString(KEY_SCHEDULE_DESC);
        String repeatTypeStr = getInputData().getString(KEY_REPEAT_TYPE);
        int remainingCount = getInputData().getInt(KEY_REMAINING_COUNT, 0);
        int hour = getInputData().getInt(KEY_HOUR, 0);
        int minute = getInputData().getInt(KEY_MINUTE, 0);
        String espCommand = getInputData().getString(KEY_ESP_COMMAND);

        Log.i(TAG, "Trigger fired for schedule " + scheduleId + " (" + name + ")");

        // 1. Show trigger notification
        NotificationHelper.showTriggerNotification(getApplicationContext(), scheduleId, name, desc);

        // 2. Start foreground service for the trigger window
        Intent serviceIntent = new Intent(getApplicationContext(), TriggerForegroundService.class);
        serviceIntent.putExtra(KEY_SCHEDULE_ID, scheduleId);
        serviceIntent.putExtra(KEY_SCHEDULE_NAME, name);
        serviceIntent.putExtra(KEY_ESP_COMMAND, espCommand != null ? espCommand : "BUZZ:500:1");
        getApplicationContext().startForegroundService(serviceIntent);

        // 3. Log trigger occurrence
        TriggerLogEntity log = new TriggerLogEntity();
        log.scheduleId = scheduleId;
        log.triggerTime = System.currentTimeMillis();
        log.status = "SENT";
        AppDatabase.getInstance(getApplicationContext()).triggerLogDao().insert(log);

        // 4. Send ESP32 command
        String cmd = (espCommand != null && !espCommand.isEmpty()) ? espCommand : "BUZZ:500:1";
        esp32Communicator.sendBuzzCommand(scheduleId, cmd, new Esp32Communicator.AckCallback() {
            @Override
            public void onAck(long id, String response) {
                Log.i(TAG, "ESP32 ACK for schedule " + id + ": " + response);
                // TODO: Update trigger log status to ACK
            }

            @Override
            public void onFailure(long id, String error) {
                Log.e(TAG, "ESP32 FAILED for schedule " + id + ": " + error);
                // TODO: Implement retry logic
            }
        });

        // 5. Handle repeat scheduling
        RepeatType repeatType = RepeatType.NONE;
        if (repeatTypeStr != null) {
            try {
                repeatType = RepeatType.valueOf(repeatTypeStr);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Unknown repeat type: " + repeatTypeStr);
            }
        }

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        ScheduleEntity schedule = db.scheduleDao().getById(scheduleId);

        if (schedule != null) {
            if (repeatType == RepeatType.DAILY) {
                long nextTrigger = getNextDailyTrigger(hour, minute);
                schedule.triggerTimeMillis = nextTrigger;
                schedule.updatedAt = System.currentTimeMillis();
                db.scheduleDao().update(schedule);
                new ScheduleManager(getApplicationContext()).schedule(schedule);
                Log.i(TAG, "Rescheduled daily trigger for " + scheduleId + " at " + nextTrigger);

            } else if (repeatType == RepeatType.COUNT_BASED) {
                int newRemaining = remainingCount - 1;
                if (newRemaining > 0) {
                    long nextTrigger = getNextDailyTrigger(hour, minute);
                    schedule.triggerTimeMillis = nextTrigger;
                    schedule.remainingCount = newRemaining;
                    schedule.updatedAt = System.currentTimeMillis();
                    db.scheduleDao().update(schedule);
                    new ScheduleManager(getApplicationContext()).schedule(schedule);
                    Log.i(TAG, "Rescheduled count-based trigger, remaining=" + newRemaining);
                } else {
                    schedule.isActive = false;
                    schedule.updatedAt = System.currentTimeMillis();
                    db.scheduleDao().update(schedule);
                    Log.i(TAG, "Count-based schedule " + scheduleId + " completed all repeats");
                }

            } else {
                // NONE - one-time, mark inactive
                schedule.isActive = false;
                schedule.updatedAt = System.currentTimeMillis();
                db.scheduleDao().update(schedule);
            }
        }

        return Result.success();
    }

    private long getNextDailyTrigger(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
