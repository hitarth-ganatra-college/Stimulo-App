package com.stimulo.app;

import android.app.Application;
import com.stimulo.app.notification.NotificationHelper;

public class StimApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
    }
}
