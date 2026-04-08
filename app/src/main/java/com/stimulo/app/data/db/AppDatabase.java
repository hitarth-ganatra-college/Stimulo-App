package com.stimulo.app.data.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.stimulo.app.data.dao.ScheduleDao;
import com.stimulo.app.data.dao.TriggerLogDao;
import com.stimulo.app.data.entity.ScheduleEntity;
import com.stimulo.app.data.entity.TriggerLogEntity;

@Database(entities = {ScheduleEntity.class, TriggerLogEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract ScheduleDao scheduleDao();
    public abstract TriggerLogDao triggerLogDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "stimulo_db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
