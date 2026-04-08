package com.stimulo.app.ui.dashboard;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.stimulo.app.data.entity.ScheduleEntity;
import com.stimulo.app.databinding.ItemScheduleBinding;
import com.stimulo.app.model.RepeatType;
import java.util.Date;

public class ScheduleAdapter extends ListAdapter<ScheduleEntity, ScheduleAdapter.ViewHolder> {
    public interface OnDeleteListener {
        void onDelete(ScheduleEntity s);
    }

    public interface OnToggleListener {
        void onToggle(ScheduleEntity s);
    }

    private final OnDeleteListener deleteListener;
    private final OnToggleListener toggleListener;

    public ScheduleAdapter(OnDeleteListener deleteListener, OnToggleListener toggleListener) {
        super(DIFF_CALLBACK);
        this.deleteListener = deleteListener;
        this.toggleListener = toggleListener;
    }

    private static final DiffUtil.ItemCallback<ScheduleEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ScheduleEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull ScheduleEntity a, @NonNull ScheduleEntity b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ScheduleEntity a, @NonNull ScheduleEntity b) {
                    return a.name.equals(b.name)
                            && a.triggerTimeMillis == b.triggerTimeMillis
                            && a.isActive == b.isActive
                            && a.repeatType.equals(b.repeatType)
                            && a.intervalMinutes == b.intervalMinutes
                            && java.util.Objects.equals(a.category, b.category)
                            && java.util.Objects.equals(a.weekdays, b.weekdays);
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemScheduleBinding binding = ItemScheduleBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemScheduleBinding binding;

        ViewHolder(ItemScheduleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ScheduleEntity schedule) {
            binding.tvName.setText(schedule.name);
            binding.tvDescription.setText(
                    schedule.description != null && !schedule.description.isEmpty()
                            ? schedule.description : "No description");

            // Category badge
            if (schedule.category != null && !schedule.category.isEmpty()) {
                binding.tvCategory.setText(schedule.category);
                binding.tvCategory.setVisibility(View.VISIBLE);
            } else {
                binding.tvCategory.setVisibility(View.GONE);
            }

            String timeStr = DateFormat.format("MMM dd, yyyy HH:mm",
                    new Date(schedule.triggerTimeMillis)).toString();
            binding.tvNextTrigger.setText("Next: " + timeStr);

            RepeatType rt = RepeatType.NONE;
            try {
                rt = RepeatType.valueOf(schedule.repeatType);
            } catch (IllegalArgumentException ignored) {
            }

            String repeatLabel;
            switch (rt) {
                case DAILY:
                    repeatLabel = "Daily";
                    break;
                case WEEKDAYS:
                    repeatLabel = formatWeekdays(schedule.weekdays);
                    break;
                case COUNT_BASED:
                    repeatLabel = "×" + schedule.remainingCount + " remaining";
                    break;
                default:
                    repeatLabel = "Once";
                    break;
            }

            if (schedule.intervalMinutes > 0) {
                repeatLabel += " · every " + formatInterval(schedule.intervalMinutes);
            }
            binding.tvRepeat.setText(repeatLabel);
            binding.switchActive.setChecked(schedule.isActive);
            // Clear previous listener to avoid spurious callbacks during bind
            binding.switchActive.setOnCheckedChangeListener(null);
            binding.switchActive.setOnCheckedChangeListener(
                    (btn, checked) -> toggleListener.onToggle(schedule));
            binding.btnDelete.setOnClickListener(v -> deleteListener.onDelete(schedule));
        }

        private String formatWeekdays(String weekdays) {
            if (weekdays == null || weekdays.isEmpty()) return "Weekdays";
            String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            StringBuilder sb = new StringBuilder();
            for (String part : weekdays.split(",")) {
                try {
                    int day = Integer.parseInt(part.trim());
                    if (day >= 1 && day <= 7) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(names[day - 1]);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return sb.length() > 0 ? sb.toString() : "Weekdays";
        }

        private String formatInterval(int minutes) {
            if (minutes < 60) return minutes + " min";
            int hours = minutes / 60;
            int rem = minutes % 60;
            return rem == 0 ? hours + " hr" : hours + "h " + rem + "m";
        }
    }
}
