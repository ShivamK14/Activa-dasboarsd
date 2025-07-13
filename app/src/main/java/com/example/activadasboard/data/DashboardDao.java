package com.example.activadasboard.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface DashboardDao {
    @Insert
    void insert(DashboardData data);

    @Query("SELECT * FROM dashboard_data ORDER BY timestamp DESC LIMIT 1")
    DashboardData getLatestData();

    @Query("SELECT * FROM dashboard_data ORDER BY timestamp DESC LIMIT :limit")
    List<DashboardData> getRecentData(int limit);

    @Query("SELECT * FROM dashboard_data WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    List<DashboardData> getDataSince(long startTime);

    @Query("SELECT * FROM dashboard_data ORDER BY timestamp DESC")
    List<DashboardData> getAllData();

    // New methods for enhanced historical data retrieval
    @Query("SELECT * FROM dashboard_data WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    List<DashboardData> getDataInRange(long startTime, long endTime);

    @Query("SELECT * FROM dashboard_data WHERE fuelFillStarted = 1 ORDER BY timestamp DESC")
    List<DashboardData> getFuelFillEvents();

    @Query("SELECT * FROM dashboard_data WHERE speed > :minSpeed ORDER BY timestamp DESC")
    List<DashboardData> getHighSpeedEvents(float minSpeed);

    @Query("SELECT * FROM dashboard_data WHERE instantEconomy > :minEconomy ORDER BY timestamp DESC")
    List<DashboardData> getEfficientTrips(float minEconomy);

    @Query("SELECT * FROM dashboard_data WHERE totalDistance >= :minDistance ORDER BY timestamp DESC")
    List<DashboardData> getLongTrips(double minDistance);

    // Methods for data visualization
    @Query("SELECT AVG(speed) as avgSpeed, " +
           "AVG(fuelPercentage) as avgFuel, " +
           "AVG(instantEconomy) as avgEconomy, " +
           "MAX(totalDistance) - MIN(totalDistance) as distanceTraveled, " +
           "COUNT(*) as dataPoints " +
           "FROM dashboard_data " +
           "WHERE timestamp >= :startTime AND timestamp <= :endTime " +
           "GROUP BY strftime('%Y-%m-%d', datetime(timestamp/1000, 'unixepoch'))")
    List<TripSummary> getDailySummaries(long startTime, long endTime);

    @Query("SELECT AVG(speed) as avgSpeed, " +
           "AVG(fuelPercentage) as avgFuel, " +
           "AVG(instantEconomy) as avgEconomy, " +
           "MAX(totalDistance) - MIN(totalDistance) as distanceTraveled, " +
           "COUNT(*) as dataPoints " +
           "FROM dashboard_data " +
           "WHERE timestamp >= :startTime AND timestamp <= :endTime " +
           "GROUP BY strftime('%Y-%m-%d %H', datetime(timestamp/1000, 'unixepoch'))")
    List<TripSummary> getHourlySummaries(long startTime, long endTime);

    // Data retention methods
    @Query("DELETE FROM dashboard_data WHERE timestamp < :timestamp")
    void deleteOldData(long timestamp);

    @Query("SELECT COUNT(*) FROM dashboard_data")
    int getDataCount();

    @Query("SELECT MIN(timestamp) FROM dashboard_data")
    long getOldestDataTimestamp();

    @Query("SELECT MAX(timestamp) FROM dashboard_data")
    long getNewestDataTimestamp();

    // Existing methods...
    @Query("SELECT AVG(speed) as avgSpeed, AVG(fuelPercentage) as avgFuel, " +
           "MAX(totalDistance) - MIN(totalDistance) as distanceTraveled, " +
           "AVG(instantEconomy) as avgEconomy " +
           "FROM dashboard_data " +
           "WHERE timestamp >= :startTime AND timestamp <= :endTime")
    TripSummary getTripSummary(long startTime, long endTime);

    @Query("SELECT * FROM dashboard_data " +
           "WHERE timestamp >= :startTime AND timestamp <= :endTime " +
           "ORDER BY timestamp ASC")
    List<DashboardData> getTripData(long startTime, long endTime);

    @Query("SELECT AVG(speed) as avgSpeed, AVG(fuelPercentage) as avgFuel, " +
           "MAX(totalDistance) - MIN(totalDistance) as distanceTraveled, " +
           "AVG(instantEconomy) as avgEconomy " +
           "FROM dashboard_data " +
           "WHERE timestamp >= :startTime")
    TripSummary getCurrentTripSummary(long startTime);

    @Query("SELECT * FROM dashboard_data " +
           "WHERE timestamp >= :startTime " +
           "ORDER BY timestamp ASC")
    List<DashboardData> getCurrentTripData(long startTime);

    @Query("SELECT AVG(fuelFillAverage) as avgFuelEconomy, " +
           "SUM(fuelUsedSinceFill) as totalFuelUsed, " +
           "MAX(fuelFillDistance) as totalDistance " +
           "FROM dashboard_data " +
           "WHERE timestamp >= :startTime AND fuelFillStarted = 1")
    FuelSummary getFuelSummary(long startTime);
} 