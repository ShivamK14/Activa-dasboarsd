package com.example.activadasboard.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_history")
public class SearchHistory {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String placeId;
    public String placeName;
    public String address;
    public double latitude;
    public double longitude;
    public long timestamp;
    public int searchCount;
    
    public SearchHistory() {}
    
    @Ignore
    public SearchHistory(String placeId, String placeName, String address, 
                        double latitude, double longitude) {
        this.placeId = placeId;
        this.placeName = placeName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = System.currentTimeMillis();
        this.searchCount = 1;
    }
} 