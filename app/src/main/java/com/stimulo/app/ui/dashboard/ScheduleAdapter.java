package com.stimulo.app.ui.dashboard;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
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
                            && a.repeatType.equals(b.repeatType);
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
                case COUNT_BASED:
                    repeatLabel = "×" + schedule.remainingCount + " remaining";
                    break;
                default:
                    repeatLabel = "Once";
                    break;
            }
            binding.tvRepeat.setText(repeatLabel);
            binding.switchActive.setChecked(schedule.isActive);
            // Clear previous listener to avoid spurious callbacks during bind
            binding.switchActive.setOnCheckedChangeListener(null);
            binding.switchActive.setOnCheckedChangeListener(
                    (btn, checked) -> toggleListener.onToggle(schedule));
            binding.btnDelete.setOnClickListener(v -> deleteListener.onDelete(schedule));
        }
    }
}
