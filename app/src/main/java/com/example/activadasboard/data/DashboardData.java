package com.example.activadasboard.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "dashboard_data")
public class DashboardData {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public double speed;
    public double fuelPercentage;
    public double fuelLiters;
    public double instantEconomy;
    public double totalDistance;
    public double trip1Distance;
    public double trip1Fuel;
    public double trip1Average;
    public boolean trip1Started;
    public double trip2Distance;
    public double trip2Fuel;
    public double trip2Average;
    public boolean trip2Started;
    public double fuelFillAverage;
    public double fuelFillDistance;
    public double lastFuelFill;
    public boolean fuelFillStarted;
    public double fuelUsedSinceFill;
    public long timestamp;

    public DashboardData() {
        this.timestamp = System.currentTimeMillis();
    }
} 