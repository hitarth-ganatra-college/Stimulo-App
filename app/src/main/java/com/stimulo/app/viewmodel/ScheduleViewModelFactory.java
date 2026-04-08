package com.stimulo.app.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class ScheduleViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;

    public ScheduleViewModelFactory(Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ScheduleViewModel.class)) {
            //noinspection unchecked
            return (T) new ScheduleViewModel(application);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
