package com.stimulo.app.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.stimulo.app.data.entity.ScheduleEntity;
import com.stimulo.app.data.repository.ScheduleRepository;
import com.stimulo.app.scheduling.ScheduleManager;
import java.util.List;

public class ScheduleViewModel extends AndroidViewModel {
    private final ScheduleRepository repository;
    private final LiveData<List<ScheduleEntity>> schedules;
    private final ScheduleManager scheduleManager;

    public ScheduleViewModel(@NonNull Application application) {
        super(application);
        repository = new ScheduleRepository(application);
        schedules = repository.getAllSchedules();
        scheduleManager = new ScheduleManager(application);
    }

    public LiveData<List<ScheduleEntity>> getSchedules() {
        return schedules;
    }

    public void saveSchedule(ScheduleEntity schedule) {
        repository.insert(schedule, id -> {
            schedule.id = id;
            scheduleManager.schedule(schedule);
        });
    }

    public void deleteSchedule(ScheduleEntity schedule) {
        scheduleManager.cancel(schedule.id);
        repository.delete(schedule);
    }

    public void toggleActive(ScheduleEntity schedule) {
        schedule.isActive = !schedule.isActive;
        repository.update(schedule);
        if (schedule.isActive) {
            scheduleManager.schedule(schedule);
        } else {
            scheduleManager.cancel(schedule.id);
        }
    }
}
