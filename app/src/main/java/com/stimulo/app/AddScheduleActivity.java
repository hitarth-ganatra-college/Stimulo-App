package com.stimulo.app;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.stimulo.app.data.entity.ScheduleEntity;
import com.stimulo.app.databinding.ActivityAddScheduleBinding;
import com.stimulo.app.model.RepeatType;
import com.stimulo.app.viewmodel.ScheduleViewModel;
import com.stimulo.app.viewmodel.ScheduleViewModelFactory;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class AddScheduleActivity extends AppCompatActivity {
    private ActivityAddScheduleBinding binding;
    private ScheduleViewModel viewModel;
    private int selectedHour = -1;
    private int selectedMinute = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddScheduleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Add Schedule");
        }

        viewModel = new ViewModelProvider(this,
                new ScheduleViewModelFactory(getApplication()))
                .get(ScheduleViewModel.class);

        setupRepeatOptions();
        setupTimePicker();
        setupSaveButton();
    }

    private void setupRepeatOptions() {
        binding.radioGroupRepeat.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_count) {
                binding.layoutRepeatCount.setVisibility(View.VISIBLE);
            } else {
                binding.layoutRepeatCount.setVisibility(View.GONE);
            }
        });
        binding.radioOnce.setChecked(true);
        binding.layoutRepeatCount.setVisibility(View.GONE);
    }

    private void setupTimePicker() {
        binding.btnPickTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            int hour = selectedHour >= 0 ? selectedHour : cal.get(Calendar.HOUR_OF_DAY);
            int minute = selectedMinute >= 0 ? selectedMinute : cal.get(Calendar.MINUTE);

            TimePickerDialog dialog = new TimePickerDialog(this, (view, h, m) -> {
                selectedHour = h;
                selectedMinute = m;
                binding.tvSelectedTime.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
                binding.tvSelectedTime.setVisibility(View.VISIBLE);
            }, hour, minute, true);
            dialog.show();
        });
    }

    private void setupSaveButton() {
        binding.btnSave.setOnClickListener(v -> saveSchedule());
    }

    private void saveSchedule() {
        String name = binding.etName.getText().toString().trim();
        String description = binding.etDescription.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            binding.tilName.setError("Event name is required");
            return;
        }
        binding.tilName.setError(null);

        if (selectedHour < 0) {
            Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show();
            return;
        }

        RepeatType repeatType;
        int repeatCount = 1;

        if (binding.radioDaily.isChecked()) {
            repeatType = RepeatType.DAILY;
        } else if (binding.radioCount.isChecked()) {
            String countStr = binding.etRepeatCount.getText().toString().trim();
            if (TextUtils.isEmpty(countStr) || Integer.parseInt(countStr) < 1) {
                binding.tilRepeatCount.setError("Enter a valid count (≥ 1)");
                return;
            }
            binding.tilRepeatCount.setError(null);
            repeatCount = Integer.parseInt(countStr);
            repeatType = RepeatType.COUNT_BASED;
        } else {
            repeatType = RepeatType.NONE;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, selectedHour);
        cal.set(Calendar.MINUTE, selectedMinute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // If time already passed today, schedule for tomorrow
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        ScheduleEntity schedule = new ScheduleEntity();
        schedule.name = name;
        schedule.description = description;
        schedule.triggerTimeMillis = cal.getTimeInMillis();
        schedule.repeatType = repeatType.name();
        schedule.repeatCount = repeatCount;
        schedule.remainingCount = repeatCount;
        schedule.isActive = true;
        schedule.hourOfDay = selectedHour;
        schedule.minuteOfHour = selectedMinute;
        schedule.espCommand = "BUZZ:500:1";
        schedule.createdAt = System.currentTimeMillis();
        schedule.updatedAt = System.currentTimeMillis();
        schedule.timezoneId = TimeZone.getDefault().getID();

        viewModel.saveSchedule(schedule);

        Toast.makeText(this, "Schedule saved!", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
