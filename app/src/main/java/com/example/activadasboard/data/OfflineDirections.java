package com.example.activadasboard.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "offline_directions")
public class OfflineDirections {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String originPlaceId;
    public String originName;
    public double originLatitude;
    public double originLongitude;
    
    public String destinationPlaceId;
    public String destinationName;
    public double destinationLatitude;
    public double destinationLongitude;
    
    public String routePolyline;
    public String routeSummary;
    public long durationSeconds;
    public long distanceMeters;
    public long timestamp;
    public int usageCount;
    
    // Store detailed step-by-step directions as JSON
    public String stepsJson;
    
    public OfflineDirections() {}
    
    @Ignore
    public OfflineDirections(String originPlaceId, String originName, 
                           double originLatitude, double originLongitude,
                           String destinationPlaceId, String destinationName,
                           double destinationLatitude, double destinationLongitude,
                           String routePolyline, String routeSummary,
                           long durationSeconds, long distanceMeters, String stepsJson) {
        this.originPlaceId = originPlaceId;
        this.originName = originName;
        this.originLatitude = originLatitude;
        this.originLongitude = originLongitude;
        this.destinationPlaceId = destinationPlaceId;
        this.destinationName = destinationName;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.routePolyline = routePolyline;
        this.routeSummary = routeSummary;
        this.durationSeconds = durationSeconds;
        this.distanceMeters = distanceMeters;
        this.stepsJson = stepsJson;
        this.timestamp = System.currentTimeMillis();
        this.usageCount = 1;
    }
} 