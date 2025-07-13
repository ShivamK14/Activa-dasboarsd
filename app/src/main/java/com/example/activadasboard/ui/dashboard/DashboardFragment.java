package com.example.activadasboard.ui.dashboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import androidx.core.app.ActivityCompat;
import com.example.activadasboard.service.Esp8266Service;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.EditText;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.activadasboard.databinding.FragmentDashboardBinding;
import com.example.activadasboard.R;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;
import com.google.android.material.button.MaterialButton;

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private DashboardViewModel dashboardViewModel;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private Esp8266Service esp8266Service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL = 1000; // 1 second

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModel
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        // Initialize ESP8266 service
        esp8266Service = Esp8266Service.getInstance(requireContext());
        setupEsp8266Listeners();

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        setupLocationCallback();

        // Setup UI observers
        setupObservers();

        // Setup click listeners
        setupClickListeners();

        // Start periodic updates
        // startPeriodicUpdates(); // Removed as per edit hint

        return root;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Setup connect button
        binding.connectEspButton.setOnClickListener(v -> {
            if (esp8266Service != null) {
                if (!esp8266Service.isConnected()) {
                    esp8266Service.connect();
                    updateConnectButtonState(true);
                } else {
                    esp8266Service.disconnect();
                    updateConnectButtonState(false);
                }
            }
        });

        // Update button text based on initial connection state
        updateConnectButtonState(esp8266Service != null && esp8266Service.isConnected());
    }

    private void updateConnectButtonState(boolean isConnected) {
        if (binding != null) {
            binding.connectEspButton.setText(isConnected ? "Disconnect ESP" : "Connect ESP");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (esp8266Service != null) {
            esp8266Service.startBackgroundUpdates();
        }
        startLocationUpdates();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocationUpdates();
        // Don't stop dashboard updates here - let them continue in background
    }

    private void setupEsp8266Listeners() {
        esp8266Service.setDataListener(new Esp8266Service.OnDataListener() {
            @Override
            public void onDashboardData(JSONObject data) {
                // Multiple safety checks to prevent crashes
                if (!isAdded() || binding == null || getActivity() == null || getActivity().isFinishing()) {
                    Log.d("DashboardFragment", "Ignoring dashboard data - fragment not ready");
                    return;
                }
                
                try {
                    Log.d("DashboardFragment", "Received dashboard data: " + data.toString());
                    
                    dashboardViewModel.setIsConnected(true);
                    updateConnectButtonState(true);
                    
                    double speed = data.optDouble("speed", 0.0);
                    double trip1 = data.optDouble("trip1", 0.0);
                    double trip2 = data.optDouble("trip2", 0.0);
                    double odometer = data.optDouble("odometer", 0.0);
                    
                    // Get fuel data
                    double fuelLiters = data.optDouble("fuelLiters", 0.0);
                    double fuelFillDistance = data.optDouble("fuelFillDistance", 0.0);
                    double fuelFillAverage = data.optDouble("fuelFillAverage", 0.0);
                    double economy = data.optDouble("instantEconomy", 0.0);
                    
                    Log.d("DashboardFragment", String.format("Updating UI - Speed: %.1f, Trip1: %.1f, Trip2: %.1f, Odo: %.1f, Fuel: %.1fL, FillDist: %.1fkm, FillAvg: %.1fkm/L, Economy: %.1fkm/L",
                        speed, trip1, trip2, odometer, fuelLiters, fuelFillDistance, fuelFillAverage, economy));
                    
                    dashboardViewModel.setCurrentSpeed(speed);
                    dashboardViewModel.setTrip1Distance(trip1);
                    dashboardViewModel.setTrip2Distance(trip2);
                    dashboardViewModel.setOdometer(odometer);
                    
                    // Update UI immediately with additional safety check
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        getActivity().runOnUiThread(() -> {
                            // Final safety check before updating UI
                            if (binding != null && isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                                binding.speedValue.setText(String.format("%.1f", speed));
                                binding.trip1DistanceValue.setText(String.format("%.1f km", trip1));
                                binding.trip2DistanceValue.setText(String.format("%.1f km", trip2));
                                binding.odometerValue.setText(String.format("%.1f km", odometer));
                                
                                // Update fuel data
                                binding.fuelValue.setText(String.format("%.1f", fuelLiters));
                                binding.fuelFillDistanceValue.setText(String.format("%.1f km", fuelFillDistance));
                                binding.fuelFillAverageValue.setText(String.format("%.1f km/L", fuelFillAverage));
                                binding.economyValue.setText(String.format("%.1f km/L", economy));
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e("DashboardFragment", "Error parsing dashboard data", e);
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        getActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), "Error updating dashboard", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }

            @Override
            public void onError(String error) {
                // Multiple safety checks to prevent crashes
                if (!isAdded() || binding == null || getActivity() == null || getActivity().isFinishing()) {
                    Log.d("DashboardFragment", "Ignoring error - fragment not ready: " + error);
                    return;
                }
                
                Log.e("DashboardFragment", "Error getting dashboard data: " + error);
                dashboardViewModel.setIsConnected(false);
                updateConnectButtonState(false);
                
                if (getActivity() != null && !getActivity().isFinishing()) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "Connection error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });

        esp8266Service.setConnectionListener(new Esp8266Service.OnConnectionListener() {
            @Override
            public void onConnected() {
                if (!isAdded() || binding == null) return;
                
                dashboardViewModel.setIsConnected(true);
                updateConnectButtonState(true);
                esp8266Service.startBackgroundUpdates(); // Start updates when connected
            }

            @Override
            public void onDisconnected() {
                if (!isAdded() || binding == null) return;
                
                dashboardViewModel.setIsConnected(false);
                updateConnectButtonState(false);
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || binding == null) return;
                
                dashboardViewModel.setIsConnected(false);
                updateConnectButtonState(false);
                Log.e("DashboardFragment", "Connection error: " + error);
            }
        });
    }

    private void setupObservers() {
        // Observe connection status
        dashboardViewModel.getConnectionStatus().observe(getViewLifecycleOwner(), status -> {
            if (binding == null) return;
            binding.espStatus.setText(status);
            binding.espStatus.setTextColor(getResources().getColor(
                status.contains("Connected") ? R.color.primary : R.color.text_secondary, 
                null));
        });

        // Observe trip distances
        dashboardViewModel.getTrip1Distance().observe(getViewLifecycleOwner(), distance -> {
            if (binding == null) return;
            binding.trip1DistanceValue.setText(String.format("%.1f km", distance));
        });

        dashboardViewModel.getCurrentSpeed().observe(getViewLifecycleOwner(), speed -> {
            if (binding == null) return;
            binding.speedValue.setText(String.format("%.1f", speed));
        });

        dashboardViewModel.getOdometer().observe(getViewLifecycleOwner(), value -> {
            if (binding == null) return;
            binding.odometerValue.setText(String.format("%.1f km", value));
        });
    }

    private void setupClickListeners() {
        binding.resetOdometerButton.setOnClickListener(v -> {
            esp8266Service.resetOdometer();
            Toast.makeText(requireContext(), "Odometer reset attempted", Toast.LENGTH_SHORT).show();
        });

        binding.restartLcdButton.setOnClickListener(v -> {
            esp8266Service.restartLcd();
            Toast.makeText(requireContext(), "LCD restart requested", Toast.LENGTH_SHORT).show();
        });

        binding.startGpsButton.setOnClickListener(v -> startLocationUpdates());
        binding.stopGpsButton.setOnClickListener(v -> stopLocationUpdates());
    }

    // Remove startPeriodicUpdates() since we now use background updates

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    binding.gpsStatusText.setText("GPS: Active");
                    binding.gpsStatusText.setTextColor(getResources().getColor(R.color.primary, null));
                }
            }
        };
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), 
            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        binding.gpsStatusText.setText("GPS: Starting...");
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            binding.gpsStatusText.setText("GPS: Stopped");
            binding.gpsStatusText.setTextColor(getResources().getColor(R.color.text_secondary, null));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        stopLocationUpdates();
        if (esp8266Service != null) {
            // Clear the data listener to prevent crashes
            esp8266Service.setDataListener(null);
            esp8266Service.setConnectionListener(null);
        }
        binding = null;
    }
}