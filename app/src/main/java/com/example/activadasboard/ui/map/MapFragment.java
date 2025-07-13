package com.example.activadasboard.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.activadasboard.ActivaDashboardApplication;
import com.example.activadasboard.R;
import com.example.activadasboard.databinding.FragmentMapBinding;
import com.example.activadasboard.service.Esp8266Service;
import com.example.activadasboard.data.SearchHistory;
import com.example.activadasboard.data.OfflineDirections;
import com.example.activadasboard.ui.map.OfflineDirectionsConverter;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.DirectionsStep;
import com.google.maps.model.TravelMode;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

public class MapFragment extends Fragment implements OnMapReadyCallback {
    private FragmentMapBinding binding;
    private MapViewModel viewModel;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GeoApiContext geoApiContext;
    private AutocompleteSupportFragment autocompleteFragment;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private NavigationStepsAdapter navigationStepsAdapter;
    private SearchHistoryAdapter searchHistoryAdapter;
    private LocationCallback locationCallback;
    private Handler navigationHandler;
    private static final long NAVIGATION_UPDATE_INTERVAL = 1000; // 1 second
    
    // Add pending operations queue
    private DirectionsResult pendingDirectionsResult = null;
    private Place pendingDestination = null;
    private boolean isMapReady = false;
    private boolean isSearchHistoryVisible = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        viewModel.initializeDataManager(requireContext());

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), getString(R.string.google_maps_key));
        }

        // Initialize GeoApiContext for Directions API
        geoApiContext = new GeoApiContext.Builder()
                .apiKey(getString(R.string.google_maps_key))
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .build();

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        navigationHandler = new Handler(Looper.getMainLooper());

        // Setup map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Setup RecyclerViews
        navigationStepsAdapter = new NavigationStepsAdapter();
        binding.navigationStepsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.navigationStepsRecycler.setAdapter(navigationStepsAdapter);

        searchHistoryAdapter = new SearchHistoryAdapter();
        binding.searchHistoryRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchHistoryRecycler.setAdapter(searchHistoryAdapter);

        // Setup bottom sheet
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // Setup click listeners
        binding.fabMyLocation.setOnClickListener(v -> moveToCurrentLocation());
        binding.startNavigationButton.setOnClickListener(v -> startNavigation());
        binding.clearRouteButton.setOnClickListener(v -> clearRoute());
        binding.historyButton.setOnClickListener(v -> toggleSearchHistory());

        // Setup observers
        setupObservers();

        // Setup location callback
        setupLocationCallback();

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Setup Places Autocomplete after view is created
        setupPlacesAutocomplete();
    }

    private void setupObservers() {
        viewModel.getCurrentDirections().observe(getViewLifecycleOwner(), directions -> {
            if (directions != null) {
                if (isMapReady && mMap != null) {
                    drawRoute(directions);
                    if (viewModel.getCurrentDestination().getValue() != null) {
                        showNavigationDetails(viewModel.getCurrentDestination().getValue(), directions);
                    }
                } else {
                    // Store for later when map is ready
                    pendingDirectionsResult = directions;
                    pendingDestination = viewModel.getCurrentDestination().getValue();
                }
            }
        });

        viewModel.getIsNavigating().observe(getViewLifecycleOwner(), isNavigating -> {
            if (isNavigating) {
                startNavigationUpdates();
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                binding.startNavigationButton.setText("Stop Navigation");
                binding.startNavigationButton.setOnClickListener(v -> stopNavigation());
            } else {
                stopNavigationUpdates();
                binding.startNavigationButton.setText("Start Navigation");
                binding.startNavigationButton.setOnClickListener(v -> startNavigation());
            }
        });

        viewModel.getCurrentStepIndex().observe(getViewLifecycleOwner(), index -> {
            navigationStepsAdapter.setCurrentStep(index);
            // Scroll to the current step
            if (index >= 0) {
                binding.navigationStepsRecycler.smoothScrollToPosition(index);
            }
        });

        // Search History observers
        viewModel.getRecentSearches().observe(getViewLifecycleOwner(), searchHistoryList -> {
            searchHistoryAdapter.setSearchHistory(searchHistoryList);
        });

        // Setup search history adapter click listener
        searchHistoryAdapter.setOnSearchHistoryClickListener(new SearchHistoryAdapter.OnSearchHistoryClickListener() {
            @Override
            public void onSearchHistoryClick(SearchHistory searchHistory) {
                // Create a Place object from search history
                Place place = Place.builder()
                        .setId(searchHistory.placeId)
                        .setName(searchHistory.placeName)
                        .setAddress(searchHistory.address)
                        .setLatLng(new com.google.android.gms.maps.model.LatLng(
                                searchHistory.latitude, searchHistory.longitude))
                        .build();
                
                viewModel.setCurrentDestination(place);
                if (mMap != null) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15));
                }
                calculateDirections(place);
                hideSearchHistory();
            }

            @Override
            public void onSearchHistoryLongClick(SearchHistory searchHistory) {
                // Show delete confirmation dialog
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete Search History")
                        .setMessage("Remove this search from history?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            viewModel.deleteSearchHistory(searchHistory);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null && viewModel.getIsNavigating().getValue() == Boolean.TRUE) {
                    updateNavigation(location);
                    // Send navigation data to ESP8266
                    sendNavigationDataToEsp(location);
                }
            }
        };
    }

    private void sendNavigationDataToEsp(Location location) {
        DirectionsResult directions = viewModel.getCurrentDirections().getValue();
        if (directions == null || directions.routes.length == 0) return;

        DirectionsRoute route = directions.routes[0];
        DirectionsStep[] steps = route.legs[0].steps;
        int currentIndex = viewModel.getCurrentStepIndex().getValue() != null ? 
                          viewModel.getCurrentStepIndex().getValue() : 0;

        if (currentIndex >= steps.length) return;

        DirectionsStep currentStep = steps[currentIndex];
        
        try {
            // Create navigation data JSON
            JSONObject navigationData = new JSONObject();
            navigationData.put("latitude", location.getLatitude());
            navigationData.put("longitude", location.getLongitude());
            navigationData.put("speed", location.getSpeed() * 3.6); // Convert m/s to km/h
            navigationData.put("bearing", location.getBearing());
            navigationData.put("current_step", currentIndex);
            navigationData.put("total_steps", steps.length);
            navigationData.put("next_instruction", currentStep.htmlInstructions);
            navigationData.put("distance_to_next", currentStep.distance.humanReadable);
            navigationData.put("maneuver", currentStep.maneuver != null ? currentStep.maneuver : "straight");
            
            // Get the ESP8266 service from the application
            Esp8266Service esp8266Service = ActivaDashboardApplication.getEsp8266Service();
            if (esp8266Service != null) {
                esp8266Service.updateNavigationData(navigationData);
            }
        } catch (Exception e) {
            Log.e("MapFragment", "Error preparing navigation data", e);
        }
    }

    private void updateNavigation(Location location) {
        DirectionsResult directions = viewModel.getCurrentDirections().getValue();
        if (directions == null || directions.routes.length == 0) return;

        DirectionsRoute route = directions.routes[0];
        DirectionsStep[] steps = route.legs[0].steps;
        int currentIndex = viewModel.getCurrentStepIndex().getValue() != null ? 
                          viewModel.getCurrentStepIndex().getValue() : 0;

        if (currentIndex >= steps.length) {
            // Navigation completed
            stopNavigation();
            Toast.makeText(requireContext(), "You have reached your destination!", Toast.LENGTH_LONG).show();
            return;
        }

        DirectionsStep currentStep = steps[currentIndex];
        LatLng stepLocation = new LatLng(
                currentStep.endLocation.lat,
                currentStep.endLocation.lng
        );

        float[] results = new float[1];
        Location.distanceBetween(
                location.getLatitude(), location.getLongitude(),
                stepLocation.latitude, stepLocation.longitude,
                results
        );

        // If within 20 meters of the step end point, move to next step
        if (results[0] < 20 && currentIndex < steps.length - 1) {
            viewModel.setCurrentStepIndex(currentIndex + 1);
        }

        // Update camera to follow user with bearing
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(currentLatLng)
                .zoom(18)
                .bearing(location.getBearing())
                .tilt(45)
                .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    private void startNavigationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, NAVIGATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(NAVIGATION_UPDATE_INTERVAL)
                .setMaxUpdateDelayMillis(NAVIGATION_UPDATE_INTERVAL * 2) // Maximum delay between updates
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void stopNavigationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void setupPlacesAutocomplete() {
        autocompleteFragment = (AutocompleteSupportFragment)
                getChildFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        if (autocompleteFragment != null) {
            // Ensure the fragment is properly initialized
            autocompleteFragment.setPlaceFields(Arrays.asList(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS
            ));

            // Set the listener after ensuring the fragment is ready
            try {
                autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                    @Override
                    public void onPlaceSelected(@NonNull Place place) {
                        if (place.getLatLng() != null) {
                            // Save to search history
                            viewModel.addSearchHistory(
                                place.getId(),
                                place.getName(),
                                place.getAddress(),
                                place.getLatLng().latitude,
                                place.getLatLng().longitude
                            );
                            
                            viewModel.setCurrentDestination(place);
                            if (mMap != null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15));
                            }
                            calculateDirections(place);
                            hideSearchHistory();
                        }
                    }

                    @Override
                    public void onError(@NonNull Status status) {
                        Log.e("MapFragment", "Places Autocomplete error: " + status.getStatusMessage());
                        Toast.makeText(getContext(), "Search temporarily unavailable. Please try again.",
                                Toast.LENGTH_SHORT).show();
                        // Hide the autocomplete fragment on error
                        if (autocompleteFragment.getView() != null) {
                            autocompleteFragment.getView().setVisibility(View.GONE);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e("MapFragment", "Error setting up Places Autocomplete", e);
                // Fallback: disable the autocomplete fragment
                if (autocompleteFragment.getView() != null) {
                    autocompleteFragment.getView().setVisibility(View.GONE);
                }
                Toast.makeText(getContext(), "Search feature temporarily unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void calculateDirections(Place destination) {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // First check for offline directions before getting location
        String destinationPlaceId = destination.getId();
        Log.d("MapFragment", "Checking for offline directions to: " + destinationPlaceId);
        
        viewModel.getOfflineDirectionsAsync("current_location", destinationPlaceId, offlineDirections -> {
            requireActivity().runOnUiThread(() -> {
                if (offlineDirections != null) {
                    Log.d("MapFragment", "Found offline directions, using them");
                    // Use offline directions immediately
                    viewModel.setIsOfflineMode(true);
                    Toast.makeText(requireContext(), "Using offline directions", Toast.LENGTH_SHORT).show();
                    
                    DirectionsResult offlineResult = OfflineDirectionsConverter.convertToDirectionsResult(offlineDirections);
                    if (offlineResult != null) {
                        viewModel.setCurrentDirections(offlineResult);
                        viewModel.incrementUsageCount(offlineDirections.id);
                        Log.d("MapFragment", "Offline directions loaded successfully");
                        return; // Exit early, don't make online request
                    } else {
                        Log.e("MapFragment", "Failed to convert offline directions to DirectionsResult");
                    }
                } else {
                    Log.d("MapFragment", "No offline directions found for: " + destinationPlaceId);
                    // Continue with online request if no offline directions
                    proceedWithOnlineRequest(destination);
                }
            });
        });
        
        // Don't proceed with online request here, wait for async callback
        return;

    }
    
    private void proceedWithOnlineRequest(Place destination) {
        // Check if device is online before making online request
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection. Please check your network settings.", Toast.LENGTH_LONG).show();
            return;
        }

        // If no offline directions, proceed with online request
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        com.google.maps.model.LatLng origin =
                                new com.google.maps.model.LatLng(
                                        location.getLatitude(),
                                        location.getLongitude());
                        
                        com.google.maps.model.LatLng dest =
                                new com.google.maps.model.LatLng(
                                        destination.getLatLng().latitude,
                                        destination.getLatLng().longitude);

                        String originPlaceId = "current_location";
                        String destPlaceId = destination.getId();

                        new Thread(() -> {
                            try {
                                DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
                                        .mode(TravelMode.DRIVING)
                                        .origin(origin)
                                        .destination(dest)
                                        .await();

                                requireActivity().runOnUiThread(() -> {
                                    if (result.routes.length > 0) {
                                        viewModel.setCurrentDirections(result);
                                        
                                        // Save directions for offline use
                                        DirectionsRoute route = result.routes[0];
                                        String stepsJson = convertStepsToJson(route.legs[0].steps);
                                        viewModel.saveOfflineDirections(
                                            originPlaceId, "Current Location", location.getLatitude(), location.getLongitude(),
                                            destPlaceId, destination.getName(), destination.getLatLng().latitude, destination.getLatLng().longitude,
                                            route.overviewPolyline.getEncodedPath(), route.summary,
                                            route.legs[0].duration.inSeconds, route.legs[0].distance.inMeters,
                                            stepsJson
                                        );
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(),
                                            "Error getting directions. Check your internet connection.",
                                            Toast.LENGTH_SHORT).show();
                                    
                                    // Try to use offline directions as fallback
                                    viewModel.getOfflineDirectionsAsync(originPlaceId, destPlaceId, fallbackDirections -> {
                                        requireActivity().runOnUiThread(() -> {
                                            if (fallbackDirections != null) {
                                                viewModel.setIsOfflineMode(true);
                                                Toast.makeText(requireContext(), "Using offline directions", Toast.LENGTH_LONG).show();
                                                
                                                DirectionsResult fallbackResult = OfflineDirectionsConverter.convertToDirectionsResult(fallbackDirections);
                                                if (fallbackResult != null) {
                                                    viewModel.setCurrentDirections(fallbackResult);
                                                    viewModel.incrementUsageCount(fallbackDirections.id);
                                                }
                                            }
                                        });
                                    });
                                });
                            }
                        }).start();
                    }
                });
    }

    private void drawRoute(DirectionsResult result) {
        if (!isMapReady || mMap == null) {
            pendingDirectionsResult = result;
            return;
        }

        if (result.routes.length > 0) {
            DirectionsRoute route = result.routes[0];
            List<LatLng> path = new ArrayList<>();
            
            // Decode the polyline
            for (com.google.maps.model.LatLng point : route.overviewPolyline.decodePath()) {
                path.add(new LatLng(point.lat, point.lng));
            }

            try {
                // Clear previous polylines
                mMap.clear();

                // Draw new polyline
                PolylineOptions polylineOptions = new PolylineOptions()
                        .addAll(path)
                        .color(ContextCompat.getColor(requireContext(), R.color.primary))
                        .width(12);
                mMap.addPolyline(polylineOptions);

                // Add markers
                if (path.size() > 0) {
                    mMap.addMarker(new MarkerOptions().position(path.get(0)).title("Start"));
                    mMap.addMarker(new MarkerOptions().position(path.get(path.size() - 1)).title("Destination"));
                }
            } catch (Exception e) {
                Log.e("MapFragment", "Error drawing route", e);
            }
        }
    }

    private void showNavigationDetails(Place destination, DirectionsResult result) {
        if (result.routes.length > 0) {
            DirectionsRoute route = result.routes[0];
            
            // Update UI
            binding.destinationText.setText(destination.getName());
            binding.distanceDurationText.setText(String.format("%s (%s)",
                    route.legs[0].distance.humanReadable,
                    route.legs[0].duration.humanReadable));

            // Update steps
            List<DirectionsStep> steps = Arrays.asList(route.legs[0].steps);
            navigationStepsAdapter.setSteps(steps);
            viewModel.setCurrentStepIndex(0);

            // Show bottom sheet
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void startNavigation() {
        if (viewModel.getCurrentDirections().getValue() != null) {
            viewModel.setIsNavigating(true);
            Toast.makeText(requireContext(), "Navigation started", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Please select a destination first", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopNavigation() {
        viewModel.setIsNavigating(false);
        Toast.makeText(requireContext(), "Navigation stopped", Toast.LENGTH_SHORT).show();
    }

    private void clearRoute() {
        mMap.clear();
        viewModel.clearNavigation();
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        navigationStepsAdapter.clearSteps();
    }

    private void moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        isMapReady = true;
        enableMyLocation();
        
        // Process any pending operations
        if (pendingDirectionsResult != null) {
            drawRoute(pendingDirectionsResult);
            if (pendingDestination != null) {
                showNavigationDetails(pendingDestination, pendingDirectionsResult);
            }
            pendingDirectionsResult = null;
            pendingDestination = null;
        }
        
        // Restore navigation state if needed
        DirectionsResult directions = viewModel.getCurrentDirections().getValue();
        if (directions != null && directions != pendingDirectionsResult) {
            drawRoute(directions);
            Place destination = viewModel.getCurrentDestination().getValue();
            if (destination != null) {
                showNavigationDetails(destination, directions);
            }
            if (Boolean.TRUE.equals(viewModel.getIsNavigating().getValue())) {
                startNavigationUpdates();
            }
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            moveToCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel.getIsNavigating().getValue() == Boolean.TRUE) {
            startNavigationUpdates();
        }
        
        // Re-setup Places Autocomplete if needed (only if it's visible but not working)
        if (autocompleteFragment != null && autocompleteFragment.getView() != null && 
            autocompleteFragment.getView().getVisibility() == View.VISIBLE) {
            // The fragment is visible, so it should be working
            // No need to re-setup unless there was an error
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopNavigationUpdates();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopNavigationUpdates();
        isMapReady = false;
        mMap = null;
        
        // Clean up Places Autocomplete
        if (autocompleteFragment != null) {
            try {
                autocompleteFragment.setOnPlaceSelectedListener(null);
            } catch (Exception e) {
                Log.e("MapFragment", "Error cleaning up Places Autocomplete", e);
            }
            autocompleteFragment = null;
        }
        
        if (geoApiContext != null) {
            new Thread(() -> {
                try {
                    geoApiContext.shutdown();
                } catch (Exception e) {
                    Log.e("MapFragment", "Error shutting down GeoApiContext", e);
                }
            }).start();
        }
        binding = null;
    }
    
    // Search History methods
    private void toggleSearchHistory() {
        if (isSearchHistoryVisible) {
            hideSearchHistory();
        } else {
            showSearchHistory();
        }
    }
    
    private void showSearchHistory() {
        binding.searchHistoryRecycler.setVisibility(View.VISIBLE);
        isSearchHistoryVisible = true;
        binding.historyButton.setText("Hide");
    }
    
    private void hideSearchHistory() {
        binding.searchHistoryRecycler.setVisibility(View.GONE);
        isSearchHistoryVisible = false;
        binding.historyButton.setText("History");
    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }
    
    private String convertStepsToJson(DirectionsStep[] steps) {
        try {
            JSONArray stepsArray = new JSONArray();
            for (DirectionsStep step : steps) {
                JSONObject stepObj = new JSONObject();
                stepObj.put("htmlInstructions", step.htmlInstructions);
                stepObj.put("distance", step.distance.humanReadable);
                stepObj.put("duration", step.duration.humanReadable);
                stepObj.put("travelMode", step.travelMode.name());
                if (step.maneuver != null) {
                    stepObj.put("maneuver", step.maneuver.toString());
                }
                if (step.startLocation != null) {
                    stepObj.put("startLat", step.startLocation.lat);
                    stepObj.put("startLng", step.startLocation.lng);
                }
                if (step.endLocation != null) {
                    stepObj.put("endLat", step.endLocation.lat);
                    stepObj.put("endLng", step.endLocation.lng);
                }
                stepsArray.put(stepObj);
            }
            return stepsArray.toString();
        } catch (Exception e) {
            Log.e("MapFragment", "Error converting steps to JSON", e);
            return "[]";
        }
    }
} 