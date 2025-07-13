package com.example.activadasboard.ui.history;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.example.activadasboard.R;
import com.example.activadasboard.data.DashboardDataManager;
import com.example.activadasboard.databinding.FragmentHistoryBinding;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.concurrent.TimeUnit;

public class HistoryFragment extends Fragment {
    private static final String TAG = "HistoryFragment";
    private FragmentHistoryBinding binding;
    private HistoryViewModel historyViewModel;
    private HistoryAdapter adapter;
    private DashboardDataManager dataManager;
    private HistoryPagerAdapter pagerAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        historyViewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        dataManager = new DashboardDataManager(requireContext());
        dataManager.insertDummyData();
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupViewPager();
        setupFiltering();
        setupDateRangePicker();

        return root;
    }

    private void setupViewPager() {
        pagerAdapter = new HistoryPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText("List");
                            break;
                        case 1:
                            tab.setText("Charts");
                            break;
                        case 2:
                            tab.setText("Summary");
                            break;
                    }
                }).attach();

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                binding.viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupFiltering() {
        binding.filterChipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_all) {
                historyViewModel.fetchAllHistoricalData();
            } else if (checkedId == R.id.chip_fuel_fills) {
                dataManager.getFuelFillEvents();
            } else if (checkedId == R.id.chip_high_speed) {
                dataManager.getHighSpeedEvents(80.0f);
            } else if (checkedId == R.id.chip_efficient) {
                dataManager.getEfficientTrips(20.0f);
            }
        });
    }

    private void setupDateRangePicker() {
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> dateRangePicker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Select Date Range")
                        .build();

        binding.btnDateRange.setOnClickListener(v -> {
            dateRangePicker.show(getChildFragmentManager(), "DATE_RANGE_PICKER");
        });

        dateRangePicker.addOnPositiveButtonClickListener(selection -> {
            long startTime = selection.first;
            long endTime = selection.second;
            historyViewModel.fetchHistoricalData(startTime, endTime);
            historyViewModel.fetchDailySummaries(startTime, endTime);
            historyViewModel.fetchHourlySummaries(startTime, endTime);
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Add loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        
        historyViewModel.getHistoricalData().observe(getViewLifecycleOwner(), data -> {
            Log.d(TAG, "Received historical data, size: " + (data != null ? data.size() : 0));
            binding.progressBar.setVisibility(View.GONE);
            
            if (data == null || data.isEmpty()) {
                binding.emptyView.setVisibility(View.VISIBLE);
                binding.viewPager.setVisibility(View.GONE);
                Toast.makeText(getContext(), "No historical data available", Toast.LENGTH_SHORT).show();
            } else {
                binding.emptyView.setVisibility(View.GONE);
                binding.viewPager.setVisibility(View.VISIBLE);
            }
        });

        // Fetch all data to ensure we're not having a time-range issue
        Log.d(TAG, "Fetching all historical data");
        historyViewModel.fetchAllHistoricalData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 