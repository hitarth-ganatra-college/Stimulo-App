package com.stimulo.app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trigger_logs")
public class TriggerLogEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long scheduleId;
    public long triggerTime;
    public String status; // SENT, ACK, FAILED, RETRYING
    public String espResponse;
}
