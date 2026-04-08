package com.stimulo.app.model;

public enum RepeatType {
    NONE,        // one-time ("Once")
    DAILY,       // repeat every day at the same time
    WEEKDAYS,    // repeat on selected days of the week
    COUNT_BASED  // legacy — treated as DAILY on playback
}
