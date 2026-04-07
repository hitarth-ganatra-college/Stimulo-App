package com.stimulo.app.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.stimulo.app.AddScheduleActivity;
import com.stimulo.app.databinding.FragmentDashboardBinding;
import com.stimulo.app.viewmodel.ScheduleViewModel;
import com.stimulo.app.viewmodel.ScheduleViewModelFactory;

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private ScheduleViewModel viewModel;
    private ScheduleAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this,
                new ScheduleViewModelFactory(requireActivity().getApplication()))
                .get(ScheduleViewModel.class);

        adapter = new ScheduleAdapter(
                schedule -> viewModel.deleteSchedule(schedule),
                schedule -> viewModel.toggleActive(schedule));
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        viewModel.getSchedules().observe(getViewLifecycleOwner(), schedules -> {
            adapter.submitList(schedules);
            binding.emptyView.setVisibility(schedules == null || schedules.isEmpty()
                    ? View.VISIBLE : View.GONE);
        });

        binding.fab.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AddScheduleActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
