package com.example.activadasboard;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;

import com.example.activadasboard.databinding.ActivityMainBinding;
import com.example.activadasboard.data.VehicleDataStorage;
import com.example.activadasboard.data.VehicleData;
import com.example.activadasboard.service.DashboardForegroundService;
import com.google.android.material.navigation.NavigationView;
import android.os.Handler;
import android.widget.Button;
import android.widget.Switch;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String ESP_IP_ADDRESS = "192.168.4.1";
    private static final int BATTERY_OPTIMIZATION_REQUEST = 1001;
    private static final int LOCATION_PERMISSION_REQUEST = 1002;
    private ActivityMainBinding binding;
    private VehicleDataStorage dataStorage;
    private PowerManager powerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dataStorage = VehicleDataStorage.getInstance(this);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        // Request permissions
        requestLocationPermissions();
        requestBatteryOptimizationPermissions();

        // Set up the toolbar
        setSupportActionBar(binding.toolbar);

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_dashboard, R.id.navigation_map, R.id.navigation_settings)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // Add button and switch to your layout if not already
        Button restartLcdButton = findViewById(R.id.restart_lcd_button);
        Switch autoRestartSwitch = findViewById(R.id.auto_restart_switch);
        
        if (restartLcdButton != null) {
            restartLcdButton.setOnClickListener(v -> {
                // Send HTTP GET request to /restart-lcd endpoint
                new Thread(() -> {
                    try {
                        URL url = new URL("http://" + ESP_IP_ADDRESS + "/restart-lcd");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        int responseCode = conn.getResponseCode();
                        runOnUiThread(() -> {
                            if (responseCode == 200) {
                                Toast.makeText(this, "LCD restarted manually", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Failed to restart LCD", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            });
        }
        
        if (autoRestartSwitch != null) {
            autoRestartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Enable auto-restart every 5 minutes
                    Timer timer = new Timer();
                    timer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            // Send HTTP GET request to /restart-lcd
                            new Thread(() -> {
                                try {
                                    URL url = new URL("http://" + ESP_IP_ADDRESS + "/restart-lcd");
                                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                    conn.setRequestMethod("GET");
                                    int responseCode = conn.getResponseCode();
                                    // No UI update needed here, as it's background
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                    }, 0, 300000);  // Every 5 minutes (300000 ms)
                } else {
                    // Disable auto-restart
                    Toast.makeText(this, "Auto-restart disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void requestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                    .setTitle("Location Permission Required")
                    .setMessage("This app needs location permission to access WiFi network information. This is required by Android to connect to your ESP8266 dashboard.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this, 
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                            LOCATION_PERMISSION_REQUEST);
                    })
                    .setNegativeButton("Later", null)
                    .show();
            }
        }
    }

    private void requestBatteryOptimizationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                new AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("This app needs to run in the background to maintain connection with your dashboard. Please disable battery optimization for this app.")
                    .setPositiveButton("Settings", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + packageName));
                        startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST);
                    })
                    .setNegativeButton("Later", null)
                    .show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted. WiFi connection will work properly.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Location permission denied. Some WiFi features may not work.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BATTERY_OPTIMIZATION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                    Toast.makeText(this, "Battery optimization disabled. App will run in background.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Battery optimization still enabled. App may be killed in background.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure service is running
        startDashboardService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't stop the service when app goes to background
    }

    private void startDashboardService() {
        Intent serviceIntent = new Intent(this, DashboardForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}