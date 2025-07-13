package com.example.activadasboard.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class DashboardViewModel extends ViewModel {
    private final MutableLiveData<Boolean> isConnected;
    private final MutableLiveData<String> connectionStatus;
    private final MutableLiveData<Double> trip1Distance;
    private final MutableLiveData<Double> trip2Distance;
    private final MutableLiveData<Double> currentSpeed;
    private final MutableLiveData<Double> odometer;

    public DashboardViewModel() {
        isConnected = new MutableLiveData<>(false);
        connectionStatus = new MutableLiveData<>("ESP8266: Disconnected");
        trip1Distance = new MutableLiveData<>(0.0);
        trip2Distance = new MutableLiveData<>(0.0);
        currentSpeed = new MutableLiveData<>(0.0);
        odometer = new MutableLiveData<>(0.0);
    }

    public LiveData<Boolean> getIsConnected() {
        return isConnected;
    }

    public void setIsConnected(boolean connected) {
        isConnected.setValue(connected);
        connectionStatus.setValue("ESP8266: " + (connected ? "Connected" : "Disconnected"));
    }

    public LiveData<String> getConnectionStatus() {
        return connectionStatus;
    }

    public LiveData<Double> getTrip1Distance() {
        return trip1Distance;
    }

    public void setTrip1Distance(double distance) {
        trip1Distance.setValue(distance);
    }

    public LiveData<Double> getTrip2Distance() {
        return trip2Distance;
    }

    public void setTrip2Distance(double distance) {
        trip2Distance.setValue(distance);
    }

    public LiveData<Double> getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(double speed) {
        currentSpeed.setValue(speed);
    }

    public LiveData<Double> getOdometer() {
        return odometer;
    }

    public void setOdometer(double value) {
        odometer.setValue(value);
    }
}