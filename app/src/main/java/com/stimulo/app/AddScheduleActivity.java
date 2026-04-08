package com.stimulo.app;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.stimulo.app.data.entity.ScheduleEntity;
import com.stimulo.app.databinding.ActivityAddScheduleBinding;
import com.stimulo.app.model.RepeatType;
import com.stimulo.app.viewmodel.ScheduleViewModel;
import com.stimulo.app.viewmodel.ScheduleViewModelFactory;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AddScheduleActivity extends AppCompatActivity {

    private ActivityAddScheduleBinding binding;
    private ScheduleViewModel viewModel;

    /** Full date+time selected by the user; null if not yet chosen. */
    private Calendar selectedDateTime = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddScheduleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.add_schedule);
        }

        viewModel = new ViewModelProvider(this,
                new ScheduleViewModelFactory(getApplication()))
                .get(ScheduleViewModel.class);

        setupCategoryDropdown();
        setupDateTimePicker();
        setupRecurrenceChips();
        setupIntervalChips();
        binding.btnSave.setOnClickListener(v -> saveSchedule());
    }

    // ── Category ──────────────────────────────────────────────────────────────

    private void setupCategoryDropdown() {
        String[] categories = getResources().getStringArray(R.array.schedule_categories);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, categories);
        binding.etCategory.setAdapter(adapter);
        // Pre-select the first category so the field is never blank on save
        if (categories.length > 0) {
            binding.etCategory.setText(categories[0], false);
        }
    }

    // ── Date + Time (chained pickers) ─────────────────────────────────────────

    private void setupDateTimePicker() {
        binding.btnPickDatetime.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        Calendar initial = selectedDateTime != null ? selectedDateTime : Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, day) -> showTimePicker(year, month, day),
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        // Prevent picking a date in the past
        datePicker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePicker.show();
    }

    private void showTimePicker(int year, int month, int day) {
        Calendar initial = selectedDateTime != null ? selectedDateTime : Calendar.getInstance();
        TimePickerDialog timePicker = new TimePickerDialog(
                this,
                (view, hour, minute) -> {
                    selectedDateTime = Calendar.getInstance();
                    selectedDateTime.set(year, month, day, hour, minute, 0);
                    selectedDateTime.set(Calendar.MILLISECOND, 0);
                    updateDateTimeDisplay();
                },
                initial.get(Calendar.HOUR_OF_DAY),
                initial.get(Calendar.MINUTE),
                true /* 24h */);
        timePicker.show();
    }

    private void updateDateTimeDisplay() {
        if (selectedDateTime == null) return;
        String display = String.format(Locale.getDefault(),
                "%1$tb %1$td, %1$tY  %1$tH:%1$tM", selectedDateTime);
        binding.tvSelectedDatetime.setText(display);
        binding.tvSelectedDatetime.setVisibility(View.VISIBLE);
    }

    // ── Recurrence chips ──────────────────────────────────────────────────────

    private void setupRecurrenceChips() {
        binding.chipGroupRecurrence.setOnCheckedStateChangeListener((group, checkedIds) -> {
            boolean weekdaysMode = !checkedIds.isEmpty()
                    && checkedIds.get(0) == R.id.chip_weekdays_mode;
            binding.layoutWeekdays.setVisibility(weekdaysMode ? View.VISIBLE : View.GONE);
            // Clear weekday error when switching away
            if (!weekdaysMode) binding.tvWeekdayError.setVisibility(View.GONE);
        });
        // Default: "Once" selected
        binding.chipOnce.setChecked(true);
        binding.layoutWeekdays.setVisibility(View.GONE);
    }

    // ── Interval chips ────────────────────────────────────────────────────────

    private void setupIntervalChips() {
        binding.chipGroupInterval.setOnCheckedStateChangeListener((group, checkedIds) -> {
            boolean customMode = !checkedIds.isEmpty()
                    && checkedIds.get(0) == R.id.chip_interval_custom;
            binding.layoutCustomInterval.setVisibility(customMode ? View.VISIBLE : View.GONE);
        });
        binding.chipIntervalNone.setChecked(true);
        binding.layoutCustomInterval.setVisibility(View.GONE);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveSchedule() {
        // --- Validate name ---
        String name = binding.etName.getText() != null
                ? binding.etName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            binding.tilName.setError(getString(R.string.error_name_required));
            return;
        }
        binding.tilName.setError(null);

        // --- Validate date+time ---
        if (selectedDateTime == null) {
            Toast.makeText(this, R.string.error_datetime_required, Toast.LENGTH_SHORT).show();
            return;
        }
        // If the chosen time is in the past, move it to the next occurrence
        if (selectedDateTime.getTimeInMillis() <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.error_datetime_required, Toast.LENGTH_SHORT).show();
            return;
        }

        // --- Recurrence type ---
        RepeatType repeatType;
        String weekdays = null;
        int checkedRecurrenceId = binding.chipGroupRecurrence.getCheckedChipId();
        if (checkedRecurrenceId == R.id.chip_daily) {
            repeatType = RepeatType.DAILY;
        } else if (checkedRecurrenceId == R.id.chip_weekdays_mode) {
            repeatType = RepeatType.WEEKDAYS;
            weekdays = collectSelectedWeekdays();
            if (weekdays.isEmpty()) {
                binding.tvWeekdayError.setVisibility(View.VISIBLE);
                return;
            }
            binding.tvWeekdayError.setVisibility(View.GONE);
        } else {
            repeatType = RepeatType.NONE;
        }

        // --- Interval ---
        int intervalMinutes = 0;
        int checkedIntervalId = binding.chipGroupInterval.getCheckedChipId();
        if (checkedIntervalId == R.id.chip_interval_15) {
            intervalMinutes = 15;
        } else if (checkedIntervalId == R.id.chip_interval_30) {
            intervalMinutes = 30;
        } else if (checkedIntervalId == R.id.chip_interval_60) {
            intervalMinutes = 60;
        } else if (checkedIntervalId == R.id.chip_interval_custom) {
            String customStr = binding.etCustomInterval.getText() != null
                    ? binding.etCustomInterval.getText().toString().trim() : "";
            if (TextUtils.isEmpty(customStr)) {
                binding.layoutCustomInterval.setError(getString(R.string.error_custom_interval));
                return;
            }
            try {
                int val = Integer.parseInt(customStr);
                if (val < 1 || val > 1440) {
                    binding.layoutCustomInterval.setError(getString(R.string.error_custom_interval));
                    return;
                }
                intervalMinutes = val;
            } catch (NumberFormatException e) {
                binding.layoutCustomInterval.setError(getString(R.string.error_custom_interval));
                return;
            }
            binding.layoutCustomInterval.setError(null);
        }

        // --- Category ---
        String category = binding.etCategory.getText() != null
                ? binding.etCategory.getText().toString().trim() : "";
        if (TextUtils.isEmpty(category)) {
            String[] cats = getResources().getStringArray(R.array.schedule_categories);
            category = cats.length > 0 ? cats[0] : "Other";
        }

        // --- Build entity ---
        String description = binding.etDescription.getText() != null
                ? binding.etDescription.getText().toString().trim() : "";

        ScheduleEntity schedule = new ScheduleEntity();
        schedule.name = name;
        schedule.description = description;
        schedule.category = category;
        schedule.triggerTimeMillis = selectedDateTime.getTimeInMillis();
        schedule.repeatType = repeatType.name();
        schedule.weekdays = weekdays;
        schedule.intervalMinutes = intervalMinutes;
        schedule.repeatCount = 1;
        schedule.remainingCount = 1;
        schedule.isActive = true;
        schedule.hourOfDay = selectedDateTime.get(Calendar.HOUR_OF_DAY);
        schedule.minuteOfHour = selectedDateTime.get(Calendar.MINUTE);
        schedule.espCommand = "BUZZ:500:1";
        schedule.createdAt = System.currentTimeMillis();
        schedule.updatedAt = System.currentTimeMillis();
        schedule.timezoneId = TimeZone.getDefault().getID();

        viewModel.saveSchedule(schedule);
        Toast.makeText(this, R.string.msg_schedule_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Returns a CSV string of ISO weekday numbers (1=Mon … 7=Sun) for all checked chips,
     * or an empty string if none are checked.
     */
    private String collectSelectedWeekdays() {
        // Map of chip id → ISO day number
        int[] chipIds = {
                R.id.chip_mon, R.id.chip_tue, R.id.chip_wed, R.id.chip_thu,
                R.id.chip_fri, R.id.chip_sat, R.id.chip_sun
        };
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < chipIds.length; i++) {
            com.google.android.material.chip.Chip chip = binding.getRoot().findViewById(chipIds[i]);
            if (chip != null && chip.isChecked()) {
                selected.add(String.valueOf(i + 1)); // 1=Mon … 7=Sun
            }
        }
        return TextUtils.join(",", selected);
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

