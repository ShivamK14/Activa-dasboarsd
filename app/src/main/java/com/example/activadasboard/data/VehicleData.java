package com.example.activadasboard.data;

public class VehicleData {
    private float odo;
    private float trip;
    private float fuelLevel;
    private ConnectionStatus connectionStatus;

    public VehicleData(float odo, float trip, float fuelLevel, ConnectionStatus connectionStatus) {
        this.odo = odo;
        this.trip = trip;
        this.fuelLevel = fuelLevel;
        this.connectionStatus = connectionStatus;
    }

    public float getOdo() {
        return odo;
    }

    public float getTrip() {
        return trip;
    }

    public float getFuelLevel() {
        return fuelLevel;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public enum ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        CONNECTING
    }
} 