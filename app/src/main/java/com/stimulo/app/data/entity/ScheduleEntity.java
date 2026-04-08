package com.stimulo.app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "schedules")
public class ScheduleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String description;
    public String category;            // e.g. "Study", "Health", "Work"
    public long triggerTimeMillis;     // UTC millis of NEXT trigger
    public String repeatType;          // RepeatType.name()
    public String weekdays;            // CSV of ISO day numbers 1=Mon..7=Sun, e.g. "1,3,5"
    public int repeatCount;            // legacy COUNT_BASED: total times
    public int remainingCount;         // legacy COUNT_BASED: how many remain
    public int intervalMinutes;        // 0 = none; >0 = repeat every N minutes per occurrence
    public boolean isActive;
    public String espCommand;          // e.g. "BUZZ:500:1"
    public long createdAt;
    public long updatedAt;
    public String timezoneId;          // e.g. "America/New_York"
    public int hourOfDay;              // stored for daily / weekday rescheduling
    public int minuteOfHour;           // stored for daily / weekday rescheduling
}
