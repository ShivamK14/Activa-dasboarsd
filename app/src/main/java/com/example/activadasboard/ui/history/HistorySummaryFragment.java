package com.example.activadasboard.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.activadasboard.R;
import com.example.activadasboard.data.DashboardDataManager;
import com.example.activadasboard.data.TripSummary;
import com.example.activadasboard.databinding.FragmentHistorySummaryBinding;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistorySummaryFragment extends Fragment {
    private FragmentHistorySummaryBinding binding;
    private HistoryViewModel viewModel;
    private DashboardDataManager dataManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(HistoryViewModel.class);
        dataManager = new DashboardDataManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHistorySummaryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupChart();
        setupDataManagement();
        observeData();
    }

    private void setupChart() {
        binding.dailySummaryChart.getDescription().setEnabled(false);
        binding.dailySummaryChart.setDrawGridBackground(false);
        binding.dailySummaryChart.setDrawBorders(false);
        binding.dailySummaryChart.setTouchEnabled(true);
        binding.dailySummaryChart.setDragEnabled(true);
        binding.dailySummaryChart.setScaleEnabled(true);
        binding.dailySummaryChart.setPinchZoom(true);

        XAxis xAxis = binding.dailySummaryChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        YAxis leftAxis = binding.dailySummaryChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGranularity(1f);

        binding.dailySummaryChart.getAxisRight().setEnabled(false);
        binding.dailySummaryChart.getLegend().setEnabled(true);
    }

    private void setupDataManagement() {
        binding.retentionSlider.addOnChangeListener((slider, value, fromUser) -> {
            int days = (int) value;
            binding.retentionText.setText(String.format(Locale.getDefault(), "Data Retention: %d days", days));
        });

        binding.btnCleanup.setOnClickListener(v -> {
            int days = (int) binding.retentionSlider.getValue();
            dataManager.setDataRetentionDays(days);
            Toast.makeText(requireContext(), "Data cleanup scheduled", Toast.LENGTH_SHORT).show();
        });
    }

    private void observeData() {
        viewModel.getDailySummaries().observe(getViewLifecycleOwner(), this::updateDailySummary);
        viewModel.getHistoricalData().observe(getViewLifecycleOwner(), this::updateTripStatistics);
    }

    private void updateDailySummary(List<TripSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) return;

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (int i = 0; i < summaries.size(); i++) {
            TripSummary summary = summaries.get(i);
            entries.add(new BarEntry(i, (float) summary.distanceTraveled));
            labels.add(dateFormat.format(new Date(currentTime - (i * 24 * 60 * 60 * 1000L))));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Daily Distance");
        dataSet.setColor(requireContext().getColor(R.color.colorPrimary));

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.8f);

        binding.dailySummaryChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.dailySummaryChart.setData(data);
        binding.dailySummaryChart.invalidate();
    }

    private void updateTripStatistics(List<com.example.activadasboard.data.DashboardData> data) {
        if (data == null || data.isEmpty()) return;

        double totalDistance = 0;
        double totalSpeed = 0;
        double totalEconomy = 0;
        double totalFuel = 0;

        for (com.example.activadasboard.data.DashboardData point : data) {
            totalDistance += point.totalDistance;
            totalSpeed += point.speed;
            totalEconomy += point.instantEconomy;
            totalFuel += point.fuelUsedSinceFill;
        }

        int count = data.size();
        binding.totalDistance.setText(String.format(Locale.getDefault(), "%.1f km", totalDistance));
        binding.avgSpeed.setText(String.format(Locale.getDefault(), "%.1f km/h", totalSpeed / count));
        binding.avgEconomy.setText(String.format(Locale.getDefault(), "%.1f km/L", totalEconomy / count));
        binding.totalFuel.setText(String.format(Locale.getDefault(), "%.1f L", totalFuel));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 