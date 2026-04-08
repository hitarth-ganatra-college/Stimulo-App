package com.stimulo.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.stimulo.app.data.entity.TriggerLogEntity;
import java.util.List;

@Dao
public interface TriggerLogDao {
    @Insert
    long insert(TriggerLogEntity log);

    @Query("UPDATE trigger_logs SET status = :status, espResponse = :espResponse WHERE id = :logId")
    void updateStatus(long logId, String status, String espResponse);

    @Query("SELECT * FROM trigger_logs WHERE scheduleId = :scheduleId ORDER BY triggerTime DESC")
    List<TriggerLogEntity> getLogsForSchedule(long scheduleId);

}
