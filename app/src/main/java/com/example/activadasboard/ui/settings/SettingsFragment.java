package com.example.activadasboard.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.activadasboard.databinding.FragmentSettingsBinding;
import com.example.activadasboard.ui.map.MapViewModel;
import com.example.activadasboard.ui.map.SearchHistoryAdapter;
import com.example.activadasboard.data.DataBackupManager;
import com.example.activadasboard.data.SearchHistory;
import com.example.activadasboard.service.Esp8266Service;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private MapViewModel mapViewModel;
    private DataBackupManager backupManager;
    private SearchHistoryAdapter searchHistoryAdapter;
    private Esp8266Service esp8266Service;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModel and Backup Manager
        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        mapViewModel.initializeDataManager(requireContext());
        backupManager = new DataBackupManager(requireContext());
        esp8266Service = Esp8266Service.getInstance(requireContext());

        // Setup RecyclerView for search history
        setupSearchHistoryRecyclerView();

        // Setup click listeners
        setupClickListeners();

        // Update counts and data
        updateDataCounts();
        setupObservers();
        
        // Update WiFi status
        updateWifiStatus();
        
        // Setup WiFi lock switch
        setupWifiLockSwitch();

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (backupManager != null) {
            backupManager.shutdown();
        }
        binding = null;
    }
    
    private void setupSearchHistoryRecyclerView() {
        searchHistoryAdapter = new SearchHistoryAdapter();
        binding.searchHistoryRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        binding.searchHistoryRecycler.setAdapter(searchHistoryAdapter);

        // Setup click listener for search history items
        searchHistoryAdapter.setOnSearchHistoryClickListener(new SearchHistoryAdapter.OnSearchHistoryClickListener() {
            @Override
            public void onSearchHistoryClick(SearchHistory searchHistory) {
                // Show details or navigate to map with this location
                showSearchHistoryDetails(searchHistory);
            }

            @Override
            public void onSearchHistoryLongClick(SearchHistory searchHistory) {
                // Show delete confirmation dialog
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete Search History")
                        .setMessage("Remove this search from history?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            mapViewModel.deleteSearchHistory(searchHistory);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void setupObservers() {
        // Observe search history changes
        mapViewModel.getRecentSearches().observe(getViewLifecycleOwner(), searchHistoryList -> {
            searchHistoryAdapter.setSearchHistory(searchHistoryList);
        });
    }

    private void showSearchHistoryDetails(SearchHistory searchHistory) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(searchHistory.placeName)
                .setMessage("Address: " + searchHistory.address + "\n" +
                        "Coordinates: " + searchHistory.latitude + ", " + searchHistory.longitude + "\n" +
                        "Searched: " + searchHistory.searchCount + " time(s)")
                .setPositiveButton("OK", null)
                .show();
    }

    private void setupClickListeners() {
        // binding.clearSearchHistoryButton.setOnClickListener(v -> clearSearchHistory());
        // binding.clearOfflineDirectionsButton.setOnClickListener(v -> clearOfflineDirections());
        binding.exportDataButton.setOnClickListener(v -> exportAllData());
    }
    
    private void updateDataCounts() {
        int searchHistoryCount = mapViewModel.getSearchHistoryCount();
        int offlineDirectionsCount = mapViewModel.getOfflineDirectionsCount();
        
//        binding.searchHistoryCount.setText(String.format("%d saved searches", searchHistoryCount));
//        binding.offlineDirectionsCount.setText(String.format("%d saved routes", offlineDirectionsCount));
    }
    
    private void updateWifiStatus() {
        if (esp8266Service != null) {
            String status = esp8266Service.getConnectionStatus();
            String permissionStatus = esp8266Service.getPermissionStatus();
            
            StringBuilder statusText = new StringBuilder();
            
            // Connection status
            if (status.startsWith("Connected to ESP8266")) {
                statusText.append("✓ ").append(status);
            } else if (status.startsWith("Connected but wrong network")) {
                statusText.append("⚠ ").append(status);
            } else {
                statusText.append("⚠ ").append(status);
            }
            
            // Permission status
            if (!permissionStatus.equals("All permissions granted")) {
                statusText.append("\n⚠ ").append(permissionStatus);
            }
            
            binding.wifiStatus.setText(statusText.toString());
        } else {
            binding.wifiStatus.setText("Service not available");
        }
    }
    
    private void setupWifiLockSwitch() {
        if (esp8266Service != null) {
            // Set initial state
            binding.wifiLockSwitch.setChecked(esp8266Service.isWifiLockEnabled());
            
            // Setup listener
            binding.wifiLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                esp8266Service.setWifiLockEnabled(isChecked);
                Toast.makeText(requireContext(), 
                    isChecked ? "WiFi locked to ESP8266 Dashboard" : "WiFi lock disabled - you can now connect to other networks", 
                    Toast.LENGTH_LONG).show();
            });
        }
    }
    
    // private void clearSearchHistory() {
    //     new androidx.appcompat.app.AlertDialog.Builder(requireContext())
    //             .setTitle("Clear Search History")
    //             .setMessage("This will delete all saved search history. This action cannot be undone.")
    //             .setPositiveButton("Clear All", (dialog, which) -> {
    //                 mapViewModel.cleanupOldSearchHistory(0); // Clear all
    //                 updateDataCounts();
    //                 Toast.makeText(requireContext(), "Search history cleared", Toast.LENGTH_SHORT).show();
    //             })
    //             .setNegativeButton("Cancel", null)
    //             .show();
    // }
    
    // private void clearOfflineDirections() {
    //     new androidx.appcompat.app.AlertDialog.Builder(requireContext())
    //             .setTitle("Clear Offline Directions")
    //             .setMessage("This will delete all saved offline directions. This action cannot be undone.")
    //             .setPositiveButton("Clear All", (dialog, which) -> {
    //                 mapViewModel.cleanupOldOfflineDirections(0); // Clear all
    //                 updateDataCounts();
    //                 Toast.makeText(requireContext(), "Offline directions cleared", Toast.LENGTH_SHORT).show();
    //             })
    //             .setNegativeButton("Cancel", null)
    //             .show();
    // }
    
    private void exportAllData() {
        binding.exportDataButton.setEnabled(false);
        binding.backupStatus.setText("Exporting data...");
        
        backupManager.exportAllData(new DataBackupManager.BackupCallback() {
            @Override
            public void onSuccess(String filePath) {
                requireActivity().runOnUiThread(() -> {
                    binding.exportDataButton.setEnabled(true);
                    binding.backupStatus.setText("Export successful! File: " + filePath);
                    Toast.makeText(requireContext(), "Data exported successfully", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    binding.exportDataButton.setEnabled(true);
                    binding.backupStatus.setText("Export failed: " + error);
                    Toast.makeText(requireContext(), "Export failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
} 