package com.example.activadasboard.ui.history;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.activadasboard.data.DashboardData;
import com.example.activadasboard.data.DashboardDataManager;
import com.example.activadasboard.data.TripSummary;

import java.util.List;

public class HistoryViewModel extends AndroidViewModel {

    private final DashboardDataManager dataManager;
    private final MutableLiveData<List<DashboardData>> historicalData = new MutableLiveData<>();
    private final MutableLiveData<TripSummary> tripSummary = new MutableLiveData<>();
    private final MutableLiveData<List<TripSummary>> dailySummaries = new MutableLiveData<>();
    private final MutableLiveData<List<TripSummary>> hourlySummaries = new MutableLiveData<>();

    public HistoryViewModel(Application application) {
        super(application);
        dataManager = new DashboardDataManager(application);
        dataManager.setListener(new DashboardDataManager.OnDataUpdateListener() {
            @Override
            public void onDataUpdated(List<DashboardData> data) {
                historicalData.postValue(data);
            }

            @Override
            public void onSummaryUpdated(TripSummary summary) {
                tripSummary.postValue(summary);
            }

            @Override
            public void onFuelSummaryUpdated(com.example.activadasboard.data.FuelSummary summary) {
                // Not used in this view model
            }

            @Override
            public void onDailySummariesUpdated(List<TripSummary> summaries) {
                dailySummaries.postValue(summaries);
            }

            @Override
            public void onHourlySummariesUpdated(List<TripSummary> summaries) {
                hourlySummaries.postValue(summaries);
            }
        });
    }

    public LiveData<List<DashboardData>> getHistoricalData() {
        return historicalData;
    }

    public LiveData<TripSummary> getTripSummary() {
        return tripSummary;
    }

    public LiveData<List<TripSummary>> getDailySummaries() {
        return dailySummaries;
    }

    public LiveData<List<TripSummary>> getHourlySummaries() {
        return hourlySummaries;
    }

    public void fetchAllHistoricalData() {
        dataManager.getAllHistoricalData();
    }

    public void fetchHistoricalData(long startTime, long endTime) {
        dataManager.getHistoricalData(startTime, endTime);
    }

    public void fetchDailySummaries(long startTime, long endTime) {
        dataManager.getDailySummaries(startTime, endTime);
    }

    public void fetchHourlySummaries(long startTime, long endTime) {
        dataManager.getHourlySummaries(startTime, endTime);
    }
} 