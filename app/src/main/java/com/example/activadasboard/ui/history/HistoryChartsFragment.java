package com.example.activadasboard.ui.history;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.activadasboard.R;
import com.example.activadasboard.data.DashboardData;
import com.example.activadasboard.databinding.FragmentHistoryChartsBinding;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryChartsFragment extends Fragment {
    private FragmentHistoryChartsBinding binding;
    private HistoryViewModel viewModel;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(HistoryViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryChartsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupCharts();
        observeData();
    }

    private void setupCharts() {
        setupChart(binding.speedChart, "Speed (km/h)", Color.BLUE);
        setupChart(binding.economyChart, "Fuel Economy (km/L)", Color.GREEN);
        setupChart(binding.fuelLevelChart, "Fuel Level (%)", Color.RED);
        setupChart(binding.distanceChart, "Distance (km)", Color.MAGENTA);
    }

    private void setupChart(com.github.mikephil.charting.charts.LineChart chart, String label, int color) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGranularity(1f);

        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(true);
    }

    private void observeData() {
        viewModel.getHistoricalData().observe(getViewLifecycleOwner(), this::updateCharts);
    }

    private void updateCharts(List<DashboardData> data) {
        if (data == null || data.isEmpty()) return;

        // Create time labels
        List<String> timeLabels = new ArrayList<>();
        for (DashboardData point : data) {
            timeLabels.add(dateFormat.format(new Date(point.timestamp)));
        }

        // Update X-axis labels
        XAxis xAxis = binding.speedChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(timeLabels));

        // Update charts
        updateSpeedChart(data);
        updateEconomyChart(data);
        updateFuelLevelChart(data);
        updateDistanceChart(data);
    }

    private void updateSpeedChart(List<DashboardData> data) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            entries.add(new Entry(i, (float) data.get(i).speed));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Speed");
        dataSet.setColor(Color.BLUE);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        binding.speedChart.setData(new LineData(dataSet));
        binding.speedChart.invalidate();
    }

    private void updateEconomyChart(List<DashboardData> data) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            entries.add(new Entry(i, (float) data.get(i).instantEconomy));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Fuel Economy");
        dataSet.setColor(Color.GREEN);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        binding.economyChart.setData(new LineData(dataSet));
        binding.economyChart.invalidate();
    }

    private void updateFuelLevelChart(List<DashboardData> data) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            entries.add(new Entry(i, (float) data.get(i).fuelPercentage));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Fuel Level");
        dataSet.setColor(Color.RED);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        binding.fuelLevelChart.setData(new LineData(dataSet));
        binding.fuelLevelChart.invalidate();
    }

    private void updateDistanceChart(List<DashboardData> data) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            entries.add(new Entry(i, (float) data.get(i).totalDistance));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Distance");
        dataSet.setColor(Color.MAGENTA);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        binding.distanceChart.setData(new LineData(dataSet));
        binding.distanceChart.invalidate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 