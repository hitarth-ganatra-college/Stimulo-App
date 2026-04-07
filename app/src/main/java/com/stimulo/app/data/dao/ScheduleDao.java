package com.stimulo.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.stimulo.app.data.entity.ScheduleEntity;
import java.util.List;

@Dao
public interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ScheduleEntity schedule);

    @Update
    void update(ScheduleEntity schedule);

    @Delete
    void delete(ScheduleEntity schedule);

    @Query("SELECT * FROM schedules WHERE isActive = 1 ORDER BY triggerTimeMillis ASC")
    LiveData<List<ScheduleEntity>> getAllActiveSchedules();

    @Query("SELECT * FROM schedules ORDER BY triggerTimeMillis ASC")
    LiveData<List<ScheduleEntity>> getAllSchedules();

    @Query("SELECT * FROM schedules WHERE id = :id")
    ScheduleEntity getById(long id);

    @Query("SELECT * FROM schedules WHERE isActive = 1")
    List<ScheduleEntity> getAllActiveSchedulesSync();

    @Query("UPDATE schedules SET isActive = 0 WHERE id = :id")
    void deactivate(long id);

    @Query("UPDATE schedules SET triggerTimeMillis = :nextMillis, remainingCount = :remaining, updatedAt = :now WHERE id = :id")
    void updateNextTrigger(long id, long nextMillis, int remaining, long now);
}
