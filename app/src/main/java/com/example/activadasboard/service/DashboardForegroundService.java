package com.example.activadasboard.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.activadasboard.MainActivity;
import com.example.activadasboard.R;

public class DashboardForegroundService extends Service {
    private static final String TAG = "DashboardService";
    private static final String CHANNEL_ID = "DashboardServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_UPDATE_INTERVAL = 30000; // 30 seconds
    private static final int MAX_NOTIFICATION_UPDATES = 3; // Limit notification updates
    private static final int SERVICE_RESTART_INTERVAL = 60000; // 1 minute
    
    private PowerManager.WakeLock wakeLock;
    private Esp8266Service esp8266Service;
    private NotificationManager notificationManager;
    private Handler notificationHandler;
    private Handler serviceHandler;
    private AlarmManager alarmManager;
    private static boolean isRunning = false;
    private int notificationUpdateCount = 0;
    private long lastNotificationTime = 0;
    private String lastNotificationText = "";
    private android.content.BroadcastReceiver screenStateReceiver;

    public static boolean isServiceRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Creating DashboardForegroundService");
        
        notificationManager = getSystemService(NotificationManager.class);
        notificationHandler = new Handler();
        serviceHandler = new Handler();
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        createNotificationChannel();
        
        // Get ESP8266 service instance
        esp8266Service = Esp8266Service.getInstance(this);
        
        // Acquire wake lock to keep CPU running
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
            "ActivaDashboard::ServiceWakeLock"
        );
        wakeLock.setReferenceCounted(false);
        
        // Set service as foreground immediately to prevent killing
        startForeground(NOTIFICATION_ID, createNotification("Starting dashboard service..."));
        
        // Register screen state receiver
        screenStateReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                if (android.content.Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    Log.d(TAG, "Service: Screen turned off, ensuring connection");
                    if (esp8266Service != null) {
                        esp8266Service.connect();
                    }
                    // Ensure wake lock is held
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire();
                    }
                } else if (android.content.Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    Log.d(TAG, "Service: Screen turned on, checking connection");
                    if (esp8266Service != null && !esp8266Service.isConnected()) {
                        esp8266Service.connect();
                    }
                }
            }
        };
        
        android.content.IntentFilter screenFilter = new android.content.IntentFilter();
        screenFilter.addAction(android.content.Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(android.content.Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateReceiver, screenFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting DashboardForegroundService");
        
        if (isRunning) {
            Log.d(TAG, "Service already running");
            return START_STICKY;
        }

        isRunning = true;

        // Acquire wake lock
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        // Update notification
        startForeground(NOTIFICATION_ID, createNotification("Initializing dashboard connection..."));

        // Set up ESP8266 connection listener
        esp8266Service.setConnectionListener(new Esp8266Service.OnConnectionListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "ESP8266 connected");
                updateNotification("Connected to dashboard");
                // Reset notification count on successful connection
                notificationUpdateCount = 0;
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "ESP8266 disconnected");
                updateNotification("Reconnecting to dashboard...");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "ESP8266 error: " + error);
                // Only show critical errors in notification
                if (error.contains("Could not connect") || error.contains("WiFi disabled") || error.contains("Lost WiFi connection")) {
                    updateNotification("Connection error: " + error);
                }
            }
        });

        // Ensure ESP8266 service is running with sticky connection
        esp8266Service.setStickyConnection(true);
        esp8266Service.connect();
        esp8266Service.startBackgroundUpdates();

        // Schedule periodic service health check
        scheduleServiceHealthCheck();

        // Schedule periodic notification cleanup
        notificationHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    notificationUpdateCount = 0; // Reset counter periodically
                    notificationHandler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL);
                }
            }
        }, NOTIFICATION_UPDATE_INTERVAL);

        // Schedule periodic service restart to ensure it stays alive
        schedulePeriodicServiceRestart();

        // Schedule periodic WiFi connection check
        scheduleWifiConnectionCheck();

        // Schedule periodic WiFi lock check
        scheduleWifiLockCheck();

        return START_STICKY;
    }

    private void scheduleServiceHealthCheck() {
        serviceHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    // Check if ESP8266 service is still connected
                    if (!esp8266Service.isConnected()) {
                        Log.w(TAG, "ESP8266 service disconnected, attempting reconnect");
                        esp8266Service.connect();
                    }
                    
                    // Schedule next health check
                    serviceHandler.postDelayed(this, SERVICE_RESTART_INTERVAL);
                }
            }
        }, SERVICE_RESTART_INTERVAL);
    }

    private void schedulePeriodicServiceRestart() {
        serviceHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    Log.d(TAG, "Performing periodic service restart to ensure persistence");
                    // Restart the service to ensure it stays alive
                    Intent restartIntent = new Intent(DashboardForegroundService.this, DashboardForegroundService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(restartIntent);
                    } else {
                        startService(restartIntent);
                    }
                    // Stop current instance
                    stopSelf();
                }
            }
        }, 30 * 60 * 1000); // Restart every 30 minutes
    }

    private void scheduleWifiConnectionCheck() {
        serviceHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    // Check WiFi connection every 15 seconds
                    Log.d(TAG, "Performing WiFi connection check");
                    if (esp8266Service != null) {
                        if (!esp8266Service.isConnected()) {
                            Log.w(TAG, "WiFi connection lost, attempting reconnect");
                            esp8266Service.connect();
                        } else {
                            Log.d(TAG, "WiFi connection is active");
                        }
                        
                        // Force a connection check even if connected
                        esp8266Service.connect();
                    }
                    
                    // Schedule next WiFi check
                    serviceHandler.postDelayed(this, 15000); // 15 seconds
                }
            }
        }, 15000); // Start first check after 15 seconds
    }

    private void scheduleWifiLockCheck() {
        serviceHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    // Check if WiFi lock is still held every 30 seconds
                    Log.d(TAG, "Checking WiFi lock status");
                    if (esp8266Service != null) {
                        // Force reconnection to ensure WiFi lock is maintained
                        esp8266Service.connect();
                        
                        // Also ensure our service wake lock is held
                        if (!wakeLock.isHeld()) {
                            wakeLock.acquire();
                            Log.d(TAG, "Service wake lock re-acquired");
                        }
                    }
                    
                    // Schedule next WiFi lock check
                    serviceHandler.postDelayed(this, 30000); // 30 seconds
                }
            }
        }, 30000); // Start first check after 30 seconds
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying DashboardForegroundService");
        isRunning = false;

        notificationHandler.removeCallbacksAndMessages(null);
        serviceHandler.removeCallbacksAndMessages(null);

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (esp8266Service != null) {
            esp8266Service.cleanup();
        }
        
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering screen state receiver", e);
            }
        }

        // Schedule service restart
        scheduleServiceRestart();

        super.onDestroy();
    }

    private void scheduleServiceRestart() {
        Intent restartIntent = new Intent(this, DashboardForegroundService.class);
        PendingIntent restartPendingIntent = PendingIntent.getService(
            this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5000, // Restart after 5 seconds
                restartPendingIntent
            );
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5000,
                restartPendingIntent
            );
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Task removed, scheduling restart");
        // Schedule service restart when task is removed
        Intent restartService = new Intent(getApplicationContext(), DashboardForegroundService.class);
        PendingIntent restartServicePI = PendingIntent.getService(
            getApplicationContext(), 1, restartService, PendingIntent.FLAG_IMMUTABLE);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                restartServicePI
            );
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                restartServicePI
            );
        }

        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Low memory warning, but keeping service alive");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.w(TAG, "Memory trim level: " + level + ", but keeping service alive");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Dashboard Service Channel",
                NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setShowBadge(false);
            serviceChannel.enableLights(false);
            serviceChannel.enableVibration(false);
            serviceChannel.setSound(null, null);

            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification(String status) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Activa Dashboard")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_dashboard_black_24dp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void updateNotification(String status) {
        if (!isRunning) return;

        // Check if this is a duplicate notification
        if (status.equals(lastNotificationText)) {
            return;
        }

        // Check if we've updated too frequently
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNotificationTime < 5000) { // 5 second minimum interval
            return;
        }

        // Check if we've hit the update limit
        if (notificationUpdateCount >= MAX_NOTIFICATION_UPDATES) {
            // Only update for important status changes
            if (!status.contains("Connected") && !status.contains("error")) {
                return;
            }
        }

        lastNotificationText = status;
        lastNotificationTime = currentTime;
        notificationUpdateCount++;

        notificationManager.notify(NOTIFICATION_ID, createNotification(status));
    }
} 