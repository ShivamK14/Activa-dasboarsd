package com.example.activadasboard.ui.map;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.activadasboard.data.MapDataManager;
import com.example.activadasboard.data.OfflineDirections;
import com.example.activadasboard.data.SearchHistory;
import com.google.android.libraries.places.api.model.Place;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsStep;

import java.util.List;

public class MapViewModel extends ViewModel {
    private final MutableLiveData<Boolean> isNavigating = new MutableLiveData<>(false);
    private final MutableLiveData<DirectionsResult> currentDirections = new MutableLiveData<>();
    private final MutableLiveData<Place> currentDestination = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentStepIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Place> currentOrigin = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isOfflineMode = new MutableLiveData<>(false);
    
    private MapDataManager mapDataManager;

    public LiveData<Boolean> getIsNavigating() {
        return isNavigating;
    }

    public void setIsNavigating(boolean navigating) {
        isNavigating.setValue(navigating);
    }

    public LiveData<DirectionsResult> getCurrentDirections() {
        return currentDirections;
    }

    public void setCurrentDirections(DirectionsResult directions) {
        currentDirections.setValue(directions);
    }

    public LiveData<Place> getCurrentDestination() {
        return currentDestination;
    }

    public void setCurrentDestination(Place destination) {
        currentDestination.setValue(destination);
    }

    public LiveData<Integer> getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(int index) {
        currentStepIndex.setValue(index);
    }

    public void clearNavigation() {
        isNavigating.setValue(false);
        currentDirections.setValue(null);
        currentDestination.setValue(null);
        currentOrigin.setValue(null);
        currentStepIndex.setValue(0);
    }
    
    // Initialize data manager
    public void initializeDataManager(Context context) {
        if (mapDataManager == null) {
            mapDataManager = new MapDataManager(context);
        }
    }
    
    // Search History methods
    public LiveData<List<SearchHistory>> getRecentSearches() {
        return mapDataManager != null ? mapDataManager.getRecentSearches() : null;
    }
    
    public LiveData<List<SearchHistory>> searchHistory(String query) {
        return mapDataManager != null ? mapDataManager.searchHistory(query) : null;
    }
    
    public void addSearchHistory(String placeId, String placeName, String address, 
                                double latitude, double longitude) {
        if (mapDataManager != null) {
            mapDataManager.addSearchHistory(placeId, placeName, address, latitude, longitude);
        }
    }
    
    public void deleteSearchHistory(SearchHistory searchHistory) {
        if (mapDataManager != null) {
            mapDataManager.deleteSearchHistory(searchHistory);
        }
    }
    
    // Offline Directions methods
    public LiveData<List<OfflineDirections>> getRecentDirections() {
        return mapDataManager != null ? mapDataManager.getRecentDirections() : null;
    }
    
    public void getOfflineDirectionsAsync(String originPlaceId, String destinationPlaceId, 
                                        java.util.function.Consumer<OfflineDirections> callback) {
        if (mapDataManager != null) {
            mapDataManager.getOfflineDirectionsAsync(originPlaceId, destinationPlaceId, callback);
        } else {
            callback.accept(null);
        }
    }
    
    // Synchronous version for backward compatibility (use with caution)
    public OfflineDirections getOfflineDirections(String originPlaceId, String destinationPlaceId) {
        return mapDataManager != null ? 
            mapDataManager.getOfflineDirections(originPlaceId, destinationPlaceId) : null;
    }
    
    public void saveOfflineDirections(String originPlaceId, String originName,
                                    double originLatitude, double originLongitude,
                                    String destinationPlaceId, String destinationName,
                                    double destinationLatitude, double destinationLongitude,
                                    String routePolyline, String routeSummary,
                                    long durationSeconds, long distanceMeters, String stepsJson) {
        if (mapDataManager != null) {
            mapDataManager.saveOfflineDirections(originPlaceId, originName, originLatitude, originLongitude,
                destinationPlaceId, destinationName, destinationLatitude, destinationLongitude,
                routePolyline, routeSummary, durationSeconds, distanceMeters, stepsJson);
        }
    }
    
    public void incrementUsageCount(int directionsId) {
        if (mapDataManager != null) {
            mapDataManager.incrementUsageCount(directionsId);
        }
    }
    
    public void cleanupOldSearchHistory(long daysOld) {
        if (mapDataManager != null) {
            mapDataManager.cleanupOldSearchHistory(daysOld);
        }
    }
    
    public void cleanupOldOfflineDirections(long daysOld) {
        if (mapDataManager != null) {
            mapDataManager.cleanupOldOfflineDirections(daysOld);
        }
    }
    
    public int getSearchHistoryCount() {
        return mapDataManager != null ? mapDataManager.getSearchHistoryCount() : 0;
    }
    
    public int getOfflineDirectionsCount() {
        return mapDataManager != null ? mapDataManager.getOfflineDirectionsCount() : 0;
    }
    
    // Origin and Offline mode methods
    public LiveData<Place> getCurrentOrigin() {
        return currentOrigin;
    }
    
    public void setCurrentOrigin(Place origin) {
        currentOrigin.setValue(origin);
    }
    
    public LiveData<Boolean> getIsOfflineMode() {
        return isOfflineMode;
    }
    
    public void setIsOfflineMode(boolean offlineMode) {
        isOfflineMode.setValue(offlineMode);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        if (mapDataManager != null) {
            mapDataManager.shutdown();
        }
    }
} 