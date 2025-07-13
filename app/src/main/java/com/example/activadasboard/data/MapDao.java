package com.example.activadasboard.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MapDao {
    
    // Search History operations
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    LiveData<List<SearchHistory>> getRecentSearches();
    
    @Query("SELECT * FROM search_history WHERE placeName LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' ORDER BY searchCount DESC, timestamp DESC")
    LiveData<List<SearchHistory>> searchHistory(String query);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSearchHistory(SearchHistory searchHistory);
    
    @Query("UPDATE search_history SET searchCount = searchCount + 1, timestamp = :timestamp WHERE placeId = :placeId")
    void incrementSearchCount(String placeId, long timestamp);
    
    @Query("SELECT * FROM search_history WHERE placeId = :placeId LIMIT 1")
    SearchHistory getSearchHistoryByPlaceId(String placeId);
    
    @Delete
    void deleteSearchHistory(SearchHistory searchHistory);
    
    @Query("DELETE FROM search_history WHERE timestamp < :timestamp")
    void deleteOldSearchHistory(long timestamp);
    
    // Offline Directions operations
    @Query("SELECT * FROM offline_directions WHERE (originPlaceId = :originPlaceId AND destinationPlaceId = :destinationPlaceId) OR (originPlaceId = :destinationPlaceId AND destinationPlaceId = :originPlaceId) ORDER BY usageCount DESC, timestamp DESC LIMIT 1")
    OfflineDirections getOfflineDirections(String originPlaceId, String destinationPlaceId);
    
    @Query("SELECT * FROM offline_directions ORDER BY usageCount DESC, timestamp DESC LIMIT 10")
    LiveData<List<OfflineDirections>> getRecentDirections();
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOfflineDirections(OfflineDirections offlineDirections);
    
    @Query("UPDATE offline_directions SET usageCount = usageCount + 1, timestamp = :timestamp WHERE id = :id")
    void incrementUsageCount(int id, long timestamp);
    
    @Delete
    void deleteOfflineDirections(OfflineDirections offlineDirections);
    
    @Query("DELETE FROM offline_directions WHERE timestamp < :timestamp")
    void deleteOldOfflineDirections(long timestamp);
    
    // Utility queries
    @Query("SELECT COUNT(*) FROM search_history")
    int getSearchHistoryCount();
    
    @Query("SELECT COUNT(*) FROM offline_directions")
    int getOfflineDirectionsCount();
    
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    List<SearchHistory> getAllSearchHistory();
    
    @Query("SELECT * FROM offline_directions ORDER BY timestamp DESC")
    List<OfflineDirections> getAllOfflineDirections();
} 