package com.example.activadasboard.data;

import android.content.Context;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapDataManager {
    private final MapDao mapDao;
    private final ExecutorService executorService;
    
    public MapDataManager(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        mapDao = database.mapDao();
        executorService = Executors.newSingleThreadExecutor();
    }
    
    // Search History operations
    public LiveData<List<SearchHistory>> getRecentSearches() {
        return mapDao.getRecentSearches();
    }
    
    public LiveData<List<SearchHistory>> searchHistory(String query) {
        return mapDao.searchHistory(query);
    }
    
    public void addSearchHistory(String placeId, String placeName, String address, 
                                double latitude, double longitude) {
        executorService.execute(() -> {
            SearchHistory existing = mapDao.getSearchHistoryByPlaceId(placeId);
            if (existing != null) {
                mapDao.incrementSearchCount(placeId, System.currentTimeMillis());
            } else {
                SearchHistory newSearch = new SearchHistory(placeId, placeName, address, latitude, longitude);
                mapDao.insertSearchHistory(newSearch);
            }
        });
    }
    
    public void deleteSearchHistory(SearchHistory searchHistory) {
        executorService.execute(() -> mapDao.deleteSearchHistory(searchHistory));
    }
    
    public void cleanupOldSearchHistory(long daysOld) {
        executorService.execute(() -> {
            long cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L);
            mapDao.deleteOldSearchHistory(cutoffTime);
        });
    }
    
    // Offline Directions operations
    public void saveOfflineDirections(String originPlaceId, String originName,
                                    double originLatitude, double originLongitude,
                                    String destinationPlaceId, String destinationName,
                                    double destinationLatitude, double destinationLongitude,
                                    String routePolyline, String routeSummary,
                                    long durationSeconds, long distanceMeters, String stepsJson) {
        executorService.execute(() -> {
            OfflineDirections directions = new OfflineDirections(
                originPlaceId, originName, originLatitude, originLongitude,
                destinationPlaceId, destinationName, destinationLatitude, destinationLongitude,
                routePolyline, routeSummary, durationSeconds, distanceMeters, stepsJson
            );
            mapDao.insertOfflineDirections(directions);
        });
    }
    
    public OfflineDirections getOfflineDirections(String originPlaceId, String destinationPlaceId) {
        try {
            android.util.Log.d("MapDataManager", "Looking for offline directions from " + originPlaceId + " to " + destinationPlaceId);
            // Use a synchronous executor to avoid main thread issues
            java.util.concurrent.CompletableFuture<OfflineDirections> future = new java.util.concurrent.CompletableFuture<>();
            executorService.execute(() -> {
                try {
                    OfflineDirections result = mapDao.getOfflineDirections(originPlaceId, destinationPlaceId);
                    if (result != null) {
                        android.util.Log.d("MapDataManager", "Found offline directions: " + result.destinationName);
                    } else {
                        android.util.Log.d("MapDataManager", "No offline directions found");
                    }
                    future.complete(result);
                } catch (Exception e) {
                    android.util.Log.e("MapDataManager", "Error getting offline directions", e);
                    future.complete(null);
                }
            });
            return future.get(2, java.util.concurrent.TimeUnit.SECONDS); // 2 second timeout
        } catch (Exception e) {
            android.util.Log.e("MapDataManager", "Error getting offline directions", e);
            return null;
        }
    }
    
    public void getOfflineDirectionsAsync(String originPlaceId, String destinationPlaceId, 
                                        java.util.function.Consumer<OfflineDirections> callback) {
        executorService.execute(() -> {
            try {
                android.util.Log.d("MapDataManager", "Looking for offline directions from " + originPlaceId + " to " + destinationPlaceId);
                OfflineDirections result = mapDao.getOfflineDirections(originPlaceId, destinationPlaceId);
                if (result != null) {
                    android.util.Log.d("MapDataManager", "Found offline directions: " + result.destinationName);
                } else {
                    android.util.Log.d("MapDataManager", "No offline directions found");
                }
                callback.accept(result);
            } catch (Exception e) {
                android.util.Log.e("MapDataManager", "Error getting offline directions", e);
                callback.accept(null);
            }
        });
    }
    
    public LiveData<List<OfflineDirections>> getRecentDirections() {
        return mapDao.getRecentDirections();
    }
    
    public void incrementUsageCount(int directionsId) {
        executorService.execute(() -> 
            mapDao.incrementUsageCount(directionsId, System.currentTimeMillis()));
    }
    
    public void deleteOfflineDirections(OfflineDirections offlineDirections) {
        executorService.execute(() -> mapDao.deleteOfflineDirections(offlineDirections));
    }
    
    public void cleanupOldOfflineDirections(long daysOld) {
        executorService.execute(() -> {
            long cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L);
            mapDao.deleteOldOfflineDirections(cutoffTime);
        });
    }
    
    // Utility methods
    public int getSearchHistoryCount() {
        try {
            return mapDao.getSearchHistoryCount();
        } catch (Exception e) {
            return 0;
        }
    }
    
    public int getOfflineDirectionsCount() {
        try {
            return mapDao.getOfflineDirectionsCount();
        } catch (Exception e) {
            return 0;
        }
    }
    
    public List<SearchHistory> getAllSearchHistory() {
        try {
            return mapDao.getAllSearchHistory();
        } catch (Exception e) {
            android.util.Log.e("MapDataManager", "Error getting all search history", e);
            return new java.util.ArrayList<>();
        }
    }
    
    public List<OfflineDirections> getAllOfflineDirections() {
        try {
            return mapDao.getAllOfflineDirections();
        } catch (Exception e) {
            android.util.Log.e("MapDataManager", "Error getting all offline directions", e);
            return new java.util.ArrayList<>();
        }
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
} 