package com.stimulo.app.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.stimulo.app.MainActivity;
import com.stimulo.app.R;

public class NotificationHelper {
    public static final String CHANNEL_TRIGGER = "stimulo_trigger";
    public static final String CHANNEL_FOREGROUND = "stimulo_fg";
    public static final int NOTIF_TRIGGER_BASE = 2000;
    public static final int NOTIF_FOREGROUND = 1;

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);

            NotificationChannel triggerChannel = new NotificationChannel(
                    CHANNEL_TRIGGER,
                    "Schedule Triggers",
                    NotificationManager.IMPORTANCE_HIGH
            );
            triggerChannel.setDescription("Notifications when a scheduled event fires");
            triggerChannel.enableVibration(true);
            triggerChannel.setShowBadge(true);
            nm.createNotificationChannel(triggerChannel);

            NotificationChannel fgChannel = new NotificationChannel(
                    CHANNEL_FOREGROUND,
                    "Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            fgChannel.setDescription("Keeps Stimulo running for timely triggers");
            nm.createNotificationChannel(fgChannel);
        }
    }

    public static Notification buildForegroundNotification(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
                .setContentTitle("Stimulo Running")
                .setContentText("Monitoring scheduled habits")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    public static void showTriggerNotification(Context context, long scheduleId, String name, String description) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, (int) scheduleId, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_TRIGGER)
                .setContentTitle("⏰ " + name)
                .setContentText(description != null && !description.isEmpty()
                        ? description : "Your scheduled event is now active")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_TRIGGER_BASE + (int) scheduleId, builder.build());
    }
}
