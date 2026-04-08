package com.stimulo.app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "schedules")
public class ScheduleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String description;
    public long triggerTimeMillis;     // UTC millis of NEXT trigger
    public String repeatType;          // RepeatType.name()
    public int repeatCount;            // for COUNT_BASED: total times; decremented each trigger
    public int remainingCount;         // how many triggers remain
    public boolean isActive;
    public String espCommand;          // e.g. "BUZZ:500:1"
    public long createdAt;
    public long updatedAt;
    public String timezoneId;          // e.g. "America/New_York"
    public int hourOfDay;              // stored for daily rescheduling
    public int minuteOfHour;           // stored for daily rescheduling
}
