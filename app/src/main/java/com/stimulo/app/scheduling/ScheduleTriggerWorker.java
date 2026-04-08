package com.stimulo.app.scheduling;

import android.content.Context;
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

import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    private static final String ESP_MAC_ADDRESS = "80:F3:DA:99:A0:3E";
    private static final long ACK_WAIT_SECONDS = 8L;
    private static final int DEFAULT_DURATION_MS = 500;

    private final Esp32Communicator esp32Communicator;

    public ScheduleTriggerWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.esp32Communicator = new StubEsp32Communicator(context, ESP_MAC_ADDRESS);
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

        if (scheduleId <= 0) {
            Log.e(TAG, "Invalid scheduleId: " + scheduleId);
            return Result.failure();
        }

        NotificationHelper.showTriggerNotification(getApplicationContext(), scheduleId, name, desc);

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

        TriggerLogEntity log = new TriggerLogEntity();
        log.scheduleId = scheduleId;
        log.triggerTime = System.currentTimeMillis();
        log.status = "SENT";
        log.espResponse = "";
        long logId = db.triggerLogDao().insert(log);

        String cmd = normalizeCommand(scheduleId, espCommand);
        Log.i(TAG, "Sending ESP cmd for scheduleId=" + scheduleId + ": " + cmd);

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] acked = {false};
        final String[] responseOrError = {""};

        esp32Communicator.sendBuzzCommand(scheduleId, cmd, new Esp32Communicator.AckCallback() {
            @Override
            public void onAck(long id, String response) {
                acked[0] = true;
                responseOrError[0] = (response == null || response.isEmpty()) ? "ACK" : response;
                latch.countDown();
            }

            @Override
            public void onFailure(long id, String error) {
                acked[0] = false;
                responseOrError[0] = (error == null || error.isEmpty()) ? "Unknown BLE failure" : error;
                latch.countDown();
            }
        });

        boolean callbackReceived;
        try {
            callbackReceived = latch.await(ACK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callbackReceived = false;
            responseOrError[0] = "Interrupted while waiting ACK";
        }

        if (!callbackReceived) {
            acked[0] = false;
            responseOrError[0] = "ACK timeout after " + ACK_WAIT_SECONDS + "s";
        }

        db.triggerLogDao().updateStatus(
                logId,
                acked[0] ? "ACK" : "FAILED",
                responseOrError[0]
        );

        if (acked[0]) {
            Log.i(TAG, "ESP32 ACK scheduleId=" + scheduleId + " response=" + responseOrError[0]);
        } else {
            Log.e(TAG, "ESP32 FAILED scheduleId=" + scheduleId + ", reason=" + responseOrError[0]);
        }

        handleRepeat(db, scheduleId, repeatTypeStr, remainingCount, hour, minute);

        return acked[0] ? Result.success() : Result.retry();
    }

    private String normalizeCommand(long scheduleId, String espCommand) {
        if (espCommand != null && !espCommand.trim().isEmpty()) {
            String c = espCommand.trim();

            // Convert old format BUZZ:<duration>:<x> -> BUZZ:<scheduleId>:<duration>
            String[] parts = c.split(":");
            if (parts.length == 3 && "BUZZ".equalsIgnoreCase(parts[0])) {
                try {
                    int duration = Integer.parseInt(parts[1]);
                    return "BUZZ:" + scheduleId + ":" + duration;
                } catch (Exception ignored) { }
            }
            return c;
        }
        return "BUZZ:" + scheduleId + ":" + DEFAULT_DURATION_MS;
    }

    private void handleRepeat(AppDatabase db, long scheduleId, String repeatTypeStr, int remainingCount, int hour, int minute) {
        RepeatType repeatType = RepeatType.NONE;
        if (repeatTypeStr != null) {
            try {
                repeatType = RepeatType.valueOf(repeatTypeStr);
            } catch (IllegalArgumentException ignored) {}
        }

        ScheduleEntity schedule = db.scheduleDao().getById(scheduleId);
        if (schedule == null) return;

        if (repeatType == RepeatType.DAILY) {
            long nextTrigger = getNextDailyTrigger(hour, minute);
            schedule.triggerTimeMillis = nextTrigger;
            schedule.updatedAt = System.currentTimeMillis();
            db.scheduleDao().update(schedule);
            new ScheduleManager(getApplicationContext()).schedule(schedule);

        } else if (repeatType == RepeatType.COUNT_BASED) {
            int newRemaining = remainingCount - 1;
            if (newRemaining > 0) {
                long nextTrigger = getNextDailyTrigger(hour, minute);
                schedule.triggerTimeMillis = nextTrigger;
                schedule.remainingCount = newRemaining;
                schedule.updatedAt = System.currentTimeMillis();
                db.scheduleDao().update(schedule);
                new ScheduleManager(getApplicationContext()).schedule(schedule);
            } else {
                schedule.isActive = false;
                schedule.updatedAt = System.currentTimeMillis();
                db.scheduleDao().update(schedule);
            }

        } else {
            schedule.isActive = false;
            schedule.updatedAt = System.currentTimeMillis();
            db.scheduleDao().update(schedule);
        }
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