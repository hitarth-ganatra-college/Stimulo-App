package com.stimulo.app.data.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.stimulo.app.data.dao.ScheduleDao;
import com.stimulo.app.data.dao.TriggerLogDao;
import com.stimulo.app.data.db.AppDatabase;
import com.stimulo.app.data.entity.ScheduleEntity;
import com.stimulo.app.data.entity.TriggerLogEntity;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScheduleRepository {
    private final ScheduleDao scheduleDao;
    private final TriggerLogDao triggerLogDao;
    private final LiveData<List<ScheduleEntity>> allSchedules;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ScheduleRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        scheduleDao = db.scheduleDao();
        triggerLogDao = db.triggerLogDao();
        allSchedules = scheduleDao.getAllSchedules();
    }

    public LiveData<List<ScheduleEntity>> getAllSchedules() {
        return allSchedules;
    }

    public void insert(ScheduleEntity schedule, Callback<Long> callback) {
        executor.execute(() -> {
            long id = scheduleDao.insert(schedule);
            if (callback != null) callback.onResult(id);
        });
    }

    public void update(ScheduleEntity schedule) {
        executor.execute(() -> scheduleDao.update(schedule));
    }

    public void delete(ScheduleEntity schedule) {
        executor.execute(() -> scheduleDao.delete(schedule));
    }

    public void getById(long id, Callback<ScheduleEntity> callback) {
        executor.execute(() -> {
            ScheduleEntity entity = scheduleDao.getById(id);
            if (callback != null) callback.onResult(entity);
        });
    }

    public void getAllActiveSync(Callback<List<ScheduleEntity>> callback) {
        executor.execute(() -> {
            List<ScheduleEntity> list = scheduleDao.getAllActiveSchedulesSync();
            if (callback != null) callback.onResult(list);
        });
    }

    public void deactivate(long id) {
        executor.execute(() -> scheduleDao.deactivate(id));
    }

    public void updateNextTrigger(long id, long nextMillis, int remaining) {
        executor.execute(() -> scheduleDao.updateNextTrigger(id, nextMillis, remaining, System.currentTimeMillis()));
    }

    public void insertTriggerLog(TriggerLogEntity log) {
        executor.execute(() -> triggerLogDao.insert(log));
    }

    public interface Callback<T> {
        void onResult(T result);
    }
}
