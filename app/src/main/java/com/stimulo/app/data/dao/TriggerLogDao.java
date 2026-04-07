package com.stimulo.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.stimulo.app.data.entity.TriggerLogEntity;
import java.util.List;

@Dao
public interface TriggerLogDao {
    @Insert
    void insert(TriggerLogEntity log);

    @Query("SELECT * FROM trigger_logs WHERE scheduleId = :scheduleId ORDER BY triggerTime DESC")
    List<TriggerLogEntity> getLogsForSchedule(long scheduleId);

    @Query("UPDATE trigger_logs SET status = :status, espResponse = :response WHERE id = :id")
    void updateStatus(long id, String status, String response);
}
