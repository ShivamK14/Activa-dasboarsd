package com.example.activadasboard;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import com.example.activadasboard.service.DashboardForegroundService;
import com.example.activadasboard.service.Esp8266Service;

public class ActivaDashboardApplication extends Application {
    private static final String TAG = "ActivaDashboardApp";
    private static Esp8266Service esp8266Service;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application created");
        initializeEsp8266Service();
        startDashboardService();
    }

    private void initializeEsp8266Service() {
        if (esp8266Service == null) {
            esp8266Service = Esp8266Service.getInstance(this.getApplicationContext());
            esp8266Service.setStickyConnection(true); // Enable sticky connection mode
            esp8266Service.setConnectionListener(new Esp8266Service.OnConnectionListener() {
                @Override
                public void onConnected() {
                    Log.d(TAG, "ESP8266 connected");
                }

                @Override
                public void onDisconnected() {
                    Log.d(TAG, "ESP8266 disconnected");
                    // Attempt to reconnect if disconnected
                    esp8266Service.connect();
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "ESP8266 error: " + error);
                    // Attempt to reconnect on error
                    esp8266Service.connect();
                }
            });
        }
    }

    private void startDashboardService() {
        Intent serviceIntent = new Intent(this, DashboardForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    public static Esp8266Service getEsp8266Service() {
        return esp8266Service;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (esp8266Service != null) {
            esp8266Service.cleanup();
        }
        stopService(new Intent(this, DashboardForegroundService.class));
    }
} 