package com.example.activadasboard.data;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;

public class DashboardDataManager {
    private static final String TAG = "DashboardDataManager";
    private static final int DATA_RETENTION_DAYS = 30; // Keep data for 30 days
    private final AppDatabase database;
    private final ExecutorService executor;
    private OnDataUpdateListener listener;
    private final Random random = new Random();

    public interface OnDataUpdateListener {
        void onDataUpdated(List<DashboardData> data);
        void onSummaryUpdated(TripSummary summary);
        void onFuelSummaryUpdated(FuelSummary summary);
        void onDailySummariesUpdated(List<TripSummary> summaries);
        void onHourlySummariesUpdated(List<TripSummary> summaries);
    }

    public DashboardDataManager(Context context) {
        database = AppDatabase.getDatabase(context);
        executor = Executors.newSingleThreadExecutor();
    }

    public void setListener(OnDataUpdateListener listener) {
        this.listener = listener;
    }

    // Enhanced data retrieval methods
    public void getDataInRange(long startTime, long endTime) {
        Log.d(TAG, "Fetching data in range: " + startTime + " to " + endTime);
        executor.execute(() -> {
            List<DashboardData> data = database.dashboardDao().getDataInRange(startTime, endTime);
            if (listener != null) {
                listener.onDataUpdated(data);
            }
        });
    }

    public void getFuelFillEvents() {
        executor.execute(() -> {
            List<DashboardData> data = database.dashboardDao().getFuelFillEvents();
            if (listener != null) {
                listener.onDataUpdated(data);
            }
        });
    }

    public void getHighSpeedEvents(float minSpeed) {
        executor.execute(() -> {
            List<DashboardData> data = database.dashboardDao().getHighSpeedEvents(minSpeed);
            if (listener != null) {
                listener.onDataUpdated(data);
            }
        });
    }

    public void getEfficientTrips(float minEconomy) {
        executor.execute(() -> {
            List<DashboardData> data = database.dashboardDao().getEfficientTrips(minEconomy);
            if (listener != null) {
                listener.onDataUpdated(data);
            }
        });
    }

    public void getLongTrips(double minDistance) {
        executor.execute(() -> {
            List<DashboardData> data = database.dashboardDao().getLongTrips(minDistance);
            if (listener != null) {
                listener.onDataUpdated(data);
            }
        });
    }

    // Data visualization methods
    public void getDailySummaries(long startTime, long endTime) {
        executor.execute(() -> {
            List<TripSummary> summaries = database.dashboardDao().getDailySummaries(startTime, endTime);
            if (listener != null) {
                listener.onDailySummariesUpdated(summaries);
            }
        });
    }

    public void getHourlySummaries(long startTime, long endTime) {
        executor.execute(() -> {
            List<TripSummary> summaries = database.dashboardDao().getHourlySummaries(startTime, endTime);
            if (listener != null) {
                listener.onHourlySummariesUpdated(summaries);
            }
        });
    }

    // Data retention methods
    public void cleanup() {
        executor.execute(() -> {
            long cutoffTime = System.currentTimeMillis() - (DATA_RETENTION_DAYS * 24 * 60 * 60 * 1000L);
            database.dashboardDao().deleteOldData(cutoffTime);
        });
    }

    public void setDataRetentionDays(int days) {
        executor.execute(() -> {
            long cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L);
            database.dashboardDao().deleteOldData(cutoffTime);
        });
    }

    public void getDataStats() {
        executor.execute(() -> {
            int count = database.dashboardDao().getDataCount();
            long oldest = database.dashboardDao().getOldestDataTimestamp();
            long newest = database.dashboardDao().getNewestDataTimestamp();
            Log.d(TAG, String.format("Data stats: count=%d, oldest=%d, newest=%d", count, oldest, newest));
        });
    }

    // Chart data creation methods
    public LineData createSpeedChartData(List<DashboardData> data) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            DashboardData point = data.get(i);
            entries.add(new Entry(i, (float) point.speed));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Speed (km/h)");
        dataSet.setColor(Color.BLUE);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        return new LineData(dataSet);
    }

    public LineData createFuelEconomyChartData(List<DashboardData> data) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            DashboardData point = data.get(i);
            entries.add(new Entry(i, (float) point.instantEconomy));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Fuel Economy (km/L)");
        dataSet.setColor(Color.GREEN);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        return new LineData(dataSet);
    }

    public LineData createFuelLevelChartData(List<DashboardData> data) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            DashboardData point = data.get(i);
            entries.add(new Entry(i, (float) point.fuelPercentage));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Fuel Level (%)");
        dataSet.setColor(Color.RED);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        return new LineData(dataSet);
    }

    public LineData createDistanceChartData(List<DashboardData> data) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            DashboardData point = data.get(i);
            entries.add(new Entry(i, (float) point.totalDistance));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Total Distance (km)");
        dataSet.setColor(Color.MAGENTA);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        return new LineData(dataSet);
    }

    // Existing methods...
    public void getHistoricalData(long startTime, long endTime) {
        Log.d(TAG, "Fetching historical data from " + startTime + " to " + endTime);
        executor.execute(() -> {
            List<DashboardData> data = database.dashboardDao().getTripData(startTime, endTime);
            Log.d(TAG, "Retrieved " + (data != null ? data.size() : 0) + " historical data points");
            if (listener != null) {
                listener.onDataUpdated(data);
            }
        });
    }

    public void getAllHistoricalData() {
        Log.d(TAG, "Fetching all historical data");
        executor.execute(() -> {
            List<DashboardData> data = database.dashboardDao().getAllData();
            Log.d(TAG, "Retrieved " + (data != null ? data.size() : 0) + " total data points");
            if (listener != null) {
                listener.onDataUpdated(data);
            }
        });
    }

    public void getCurrentTripData(long startTime) {
        executor.execute(() -> {
            List<DashboardData> data = database.dashboardDao().getCurrentTripData(startTime);
            if (listener != null) {
                listener.onDataUpdated(data);
            }
        });
    }

    public void getTripSummary(long startTime, long endTime) {
        executor.execute(() -> {
            TripSummary summary = database.dashboardDao().getTripSummary(startTime, endTime);
            if (listener != null) {
                listener.onSummaryUpdated(summary);
            }
        });
    }

    public void getFuelSummary(long startTime) {
        executor.execute(() -> {
            FuelSummary summary = database.dashboardDao().getFuelSummary(startTime);
            if (listener != null) {
                listener.onFuelSummaryUpdated(summary);
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }

    public void insertDummyData() {
        Log.d(TAG, "Inserting dummy data");
        executor.execute(() -> {
            long currentTime = System.currentTimeMillis();
            List<DashboardData> dummyData = new ArrayList<>();
            
            // Create 10 dummy entries over the last 24 hours
            for (int i = 0; i < 10; i++) {
                DashboardData data = new DashboardData();
                data.timestamp = currentTime - (i * 3600000); // Each entry 1 hour apart
                data.speed = 40 + random.nextFloat() * 60; // Random speed between 40-100 km/h
                data.totalDistance = i * 50.0; // Incrementing distance
                data.instantEconomy = 15 + random.nextFloat() * 10; // Random economy between 15-25 km/L
                data.fuelPercentage = 100 - (i * 5); // Decreasing fuel level
                data.fuelFillAverage = 20 + random.nextFloat() * 5; // Random fill average
                data.fuelUsedSinceFill = i * 2.0; // Incrementing fuel used
                data.fuelFillDistance = i * 100.0; // Incrementing fill distance
                data.fuelFillStarted = (i % 3 == 0); // Some fill events
                
                dummyData.add(data);
            }
            
            // Insert all dummy data
            for (DashboardData data : dummyData) {
                database.dashboardDao().insert(data);
            }
            
            Log.d(TAG, "Inserted " + dummyData.size() + " dummy data points");
            
            // Notify listener of the new data
            if (listener != null) {
                listener.onDataUpdated(dummyData);
            }
        });
    }
} 