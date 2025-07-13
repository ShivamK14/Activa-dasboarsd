package com.example.activadasboard.data;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataBackupManager {
    private static final String TAG = "DataBackupManager";
    private final Context context;
    private final MapDataManager mapDataManager;
    private final DashboardDao dashboardDao;
    private final ExecutorService executorService;
    
    public DataBackupManager(Context context) {
        this.context = context;
        this.mapDataManager = new MapDataManager(context);
        this.dashboardDao = AppDatabase.getDatabase(context).dashboardDao();
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    public interface BackupCallback {
        void onSuccess(String filePath);
        void onError(String error);
    }
    
    public void exportAllData(BackupCallback callback) {
        executorService.execute(() -> {
            try {
                // Create backup directory
                File backupDir = new File(context.getExternalFilesDir(null), "backups");
                if (!backupDir.exists()) {
                    backupDir.mkdirs();
                }
                
                // Create backup file with timestamp
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File backupFile = new File(backupDir, "activa_dashboard_backup_" + timestamp + ".json");
                
                // Create JSON object for all data
                JSONObject backupData = new JSONObject();
                backupData.put("backup_timestamp", System.currentTimeMillis());
                backupData.put("app_version", "1.0");
                
                // Export search history
                exportSearchHistory(backupData);
                
                // Export offline directions
                exportOfflineDirections(backupData);
                
                // Export dashboard data
                exportDashboardData(backupData);
                
                // Write to file
                FileWriter writer = new FileWriter(backupFile);
                writer.write(backupData.toString(2)); // Pretty print with 2 spaces
                writer.close();
                
                Log.d(TAG, "Backup created successfully: " + backupFile.getAbsolutePath());
                callback.onSuccess(backupFile.getAbsolutePath());
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating backup", e);
                callback.onError("Failed to create backup: " + e.getMessage());
            }
        });
    }
    
    private void exportSearchHistory(JSONObject backupData) throws Exception {
        JSONArray searchHistoryArray = new JSONArray();
        
        // Get all search history (we'll need to add a method to get all)
        List<SearchHistory> searchHistoryList = mapDataManager.getAllSearchHistory();
        for (SearchHistory history : searchHistoryList) {
            JSONObject historyObj = new JSONObject();
            historyObj.put("id", history.id);
            historyObj.put("placeId", history.placeId);
            historyObj.put("placeName", history.placeName);
            historyObj.put("address", history.address);
            historyObj.put("latitude", history.latitude);
            historyObj.put("longitude", history.longitude);
            historyObj.put("timestamp", history.timestamp);
            historyObj.put("searchCount", history.searchCount);
            searchHistoryArray.put(historyObj);
        }
        
        backupData.put("search_history", searchHistoryArray);
        Log.d(TAG, "Exported " + searchHistoryList.size() + " search history entries");
    }
    
    private void exportOfflineDirections(JSONObject backupData) throws Exception {
        JSONArray directionsArray = new JSONArray();
        
        // Get all offline directions
        List<OfflineDirections> directionsList = mapDataManager.getAllOfflineDirections();
        for (OfflineDirections directions : directionsList) {
            JSONObject directionsObj = new JSONObject();
            directionsObj.put("id", directions.id);
            directionsObj.put("originPlaceId", directions.originPlaceId);
            directionsObj.put("originName", directions.originName);
            directionsObj.put("originLatitude", directions.originLatitude);
            directionsObj.put("originLongitude", directions.originLongitude);
            directionsObj.put("destinationPlaceId", directions.destinationPlaceId);
            directionsObj.put("destinationName", directions.destinationName);
            directionsObj.put("destinationLatitude", directions.destinationLatitude);
            directionsObj.put("destinationLongitude", directions.destinationLongitude);
            directionsObj.put("routePolyline", directions.routePolyline);
            directionsObj.put("routeSummary", directions.routeSummary);
            directionsObj.put("durationSeconds", directions.durationSeconds);
            directionsObj.put("distanceMeters", directions.distanceMeters);
            directionsObj.put("timestamp", directions.timestamp);
            directionsObj.put("usageCount", directions.usageCount);
            directionsArray.put(directionsObj);
        }
        
        backupData.put("offline_directions", directionsArray);
        Log.d(TAG, "Exported " + directionsList.size() + " offline directions");
    }
    
    private void exportDashboardData(JSONObject backupData) throws Exception {
        JSONArray dashboardArray = new JSONArray();
        
        // Get all dashboard data
        List<DashboardData> dashboardList = dashboardDao.getAllData();
        for (DashboardData data : dashboardList) {
            JSONObject dataObj = new JSONObject();
            dataObj.put("id", data.id);
            dataObj.put("timestamp", data.timestamp);
            dataObj.put("speed", data.speed);
            dataObj.put("totalDistance", data.totalDistance);
            dataObj.put("fuelLiters", data.fuelLiters);
            dataObj.put("fuelFillDistance", data.fuelFillDistance);
            // dataObj.put("economy", data.economy); // Remove or comment out
            // dataObj.put("tripDistance", data.tripDistance); // Remove or comment out
            // dataObj.put("tripDuration", data.tripDuration); // Remove or comment out
            // dataObj.put("odometer", data.odometer); // Remove or comment out
            dashboardArray.put(dataObj);
        }
        
        backupData.put("dashboard_data", dashboardArray);
        Log.d(TAG, "Exported " + dashboardList.size() + " dashboard data entries");
    }
    
    public void importData(String filePath, BackupCallback callback) {
        executorService.execute(() -> {
            try {
                // TODO: Implement import functionality
                // This would read the JSON file and restore data to the database
                Log.d(TAG, "Import functionality not yet implemented");
                callback.onError("Import functionality not yet implemented");
            } catch (Exception e) {
                Log.e(TAG, "Error importing data", e);
                callback.onError("Failed to import data: " + e.getMessage());
            }
        });
    }
    
    public String getBackupDirectoryPath() {
        File backupDir = new File(context.getExternalFilesDir(null), "backups");
        return backupDir.getAbsolutePath();
    }
    
    public void cleanupOldBackups(int keepCount) {
        executorService.execute(() -> {
            try {
                File backupDir = new File(context.getExternalFilesDir(null), "backups");
                if (!backupDir.exists()) return;
                
                File[] backupFiles = backupDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (backupFiles == null || backupFiles.length <= keepCount) return;
                
                // Sort by modification time (oldest first)
                java.util.Arrays.sort(backupFiles, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
                
                // Delete oldest files
                int filesToDelete = backupFiles.length - keepCount;
                for (int i = 0; i < filesToDelete; i++) {
                    if (backupFiles[i].delete()) {
                        Log.d(TAG, "Deleted old backup: " + backupFiles[i].getName());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up old backups", e);
            }
        });
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
} 