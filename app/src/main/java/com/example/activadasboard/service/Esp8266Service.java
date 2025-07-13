package com.example.activadasboard.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.FormBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Headers;
import retrofit2.Response;

import com.example.activadasboard.data.AppDatabase;
import com.example.activadasboard.data.DashboardData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Esp8266Service {
    private static final String TAG = "Esp8266Service";
    private static final String ESP_SSID = "Activa_Dashboard";
    private static final String ESP_PASSWORD = "12345678";
    private static final String ESP_BASE_URL = "http://192.168.4.1";
    private static final int CONNECTION_TIMEOUT = 60000; // Increased to 60 seconds
    private static final int MAX_RETRIES = 5;
    private static final int RETRY_DELAY = 5000; // Increased to 5 seconds
    private static final int CONNECTION_CHECK_INTERVAL = 5000; // Reduced to 5 seconds for faster detection
    private static final int SOCKET_TIMEOUT = 30000; // Increased to 30 seconds
    private static final int CONNECT_TIMEOUT = 60; // Increased to 60 seconds
    private static final int READ_TIMEOUT = 60; // Increased to 60 seconds
    private static final int WRITE_TIMEOUT = 60; // Increased to 60 seconds
    private static final int RECONNECT_COOLDOWN = 10000; // Increased to 10 seconds
    private static final int PING_TIMEOUT = 10000; // Increased to 10 seconds
    private static final int NAVIGATION_UPDATE_INTERVAL = 1000;
    private static final int MIN_REQUEST_INTERVAL = 1000; // Increased to 1 second

    // Add background update handler
    private final Handler backgroundHandler;
    private final HandlerThread backgroundThread;
    private boolean isDashboardUpdateRunning = false;
    private boolean isNavigationUpdateRunning = false;
    private JSONObject lastNavigationData = null;

    // Singleton instance
    private static volatile Esp8266Service INSTANCE = null;

    // Member variables
    private final Context appContext;
    private final WifiManager wifiManager;
    private final Handler handler;
    private final OkHttpClient client;
    private final Esp8266Api api;
    private final AppDatabase database;
    private final ExecutorService databaseExecutor;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wifiWakeLock;
    private PowerManager.WakeLock cpuWakeLock;
    private BroadcastReceiver wifiStateReceiver;
    private BroadcastReceiver screenStateReceiver;
    private Runnable connectionChecker;
    private OnConnectionListener connectionListener;
    private OnDataListener dataListener;
    private boolean isConnected = false;
    private boolean isReconnecting = false;
    private boolean isStickyConnection = true;
    private boolean isWifiLockEnabled = true;
    private long lastReconnectAttempt = 0;
    private int currentRetry = 0;
    private static long lastDashboardRequestTime = 0;
    private static long lastNavigationUpdateTime = 0;
    private static final int WIFI_LOCK_TIMEOUT = 10 * 60 * 1000; // 10 minutes
    private static final int PING_INTERVAL = 5000; // 5 seconds
    private Handler pingHandler;
    private Runnable pingRunnable;

    public interface OnConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    public interface OnDataListener {
        void onDashboardData(JSONObject data);
        void onError(String error);
    }

    public interface VersionCallback {
        void onSuccess(String version);
        void onError(String error);
    }

    public interface FirmwareUpdateCallback {
        void onProgress(int progress);
        void onSuccess();
        void onError(String error);
    }

    public interface OnSyncListener {
        void onSyncSuccess(JSONObject response);
        void onSyncError(String error);
    }

    // Private constructor for singleton
    private Esp8266Service(Context context) {
        this.appContext = context.getApplicationContext();
        this.wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.database = AppDatabase.getDatabase(appContext);
        this.databaseExecutor = Executors.newSingleThreadExecutor();

        // Initialize background thread
        backgroundThread = new HandlerThread("Esp8266UpdateThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        
        // Initialize ping handler
        pingHandler = new Handler(Looper.getMainLooper());
        pingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isStickyConnection && isConnected) {
                    // Send a ping to keep the connection alive
                    pingEsp8266();
                }
                pingHandler.postDelayed(this, PING_INTERVAL);
            }
        };

        // Initialize OkHttpClient with longer timeouts
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true); // Enable automatic retries
        
        this.client = builder.build();
        
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(ESP_BASE_URL)
            .client(client)
            .build();
        
        this.api = retrofit.create(Esp8266Api.class);

        // Initialize wake locks
        PowerManager powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        wifiWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ActivaDashboard:WifiLock"
        );
        wifiWakeLock.setReferenceCounted(false);
        
        cpuWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
            "ActivaDashboard:CpuLock"
        );
        cpuWakeLock.setReferenceCounted(false);
        
        // Initialize WiFi lock with high perf mode - using the direct approach
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ActivaDashboard:HighPerfWifi");
        wifiLock.setReferenceCounted(false);
        // Acquire WiFi lock immediately
        wifiLock.acquire();

        // Initialize connection checker with better error handling
        connectionChecker = new Runnable() {
            @Override
            public void run() {
                if (!isConnected || isReconnecting) {
                    // Try to reconnect if not connected
                    if (!isReconnecting) {
                        Log.d(TAG, "Connection checker: attempting reconnect");
                        connect();
                    }
                } else {
                    // Check if still connected to ESP8266 network
                    verifyConnection();
                }
                
                // Ensure wake locks are held
                if (isStickyConnection) {
                    if (!wifiWakeLock.isHeld()) {
                        wifiWakeLock.acquire(WIFI_LOCK_TIMEOUT);
                        Log.d(TAG, "Connection checker: re-acquired WiFi wake lock");
                    }
                    if (!cpuWakeLock.isHeld()) {
                        cpuWakeLock.acquire();
                        Log.d(TAG, "Connection checker: re-acquired CPU wake lock");
                    }
                    if (!wifiLock.isHeld()) {
                        wifiLock.acquire();
                        Log.d(TAG, "Connection checker: re-acquired WiFi lock");
                    }
                }
                
                // Schedule next check
                handler.postDelayed(this, CONNECTION_CHECK_INTERVAL);
            }
        };

        // Initialize WiFi state receiver with better reconnection logic
        wifiStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (networkInfo != null) {
                        if (networkInfo.isConnected()) {
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            if (wifiInfo != null) {
                                String ssid = wifiInfo.getSSID();
                                if (ssid != null) {
                                    ssid = ssid.replace("\"", "");
                                    Log.d(TAG, "Connected to WiFi network: " + ssid);
                                    
                                    if (ssid.equals(ESP_SSID)) {
                                        Log.d(TAG, "Connected to ESP8266 network");
                                        if (!isConnected && !isReconnecting) {
                                            connect();
                                        }
                                    } else if (isStickyConnection && isWifiLockEnabled) {
                                        // If we're not on the ESP network but sticky connection is enabled,
                                        // try to connect to ESP network (only if WiFi lock is enabled)
                                        Log.d(TAG, "Not on ESP network, but WiFi lock is enabled - attempting to reconnect");
                                        connectToEspNetwork();
                                    } else if (!isWifiLockEnabled) {
                                        Log.d(TAG, "WiFi lock disabled - allowing other WiFi connections");
                                    }
                                }
                            }
                        } else if (isStickyConnection && isWifiLockEnabled) {
                            // WiFi disconnected, try to reconnect if sticky and WiFi lock is enabled
                            Log.d(TAG, "WiFi disconnected, attempting to reconnect to ESP network");
                            connectToEspNetwork();
                        } else if (!isWifiLockEnabled) {
                            Log.d(TAG, "WiFi disconnected, but WiFi lock is disabled - allowing manual connection");
                        }
                    }
                }
            }
        };

        // Initialize screen state receiver
        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    Log.d(TAG, "Screen turned off, ensuring WiFi connection");
                    // Screen turned off, ensure WiFi connection is maintained
                    ensureWifiConnection();
                    
                    // Force immediate reconnection attempt
                    if (!isConnected) {
                        Log.d(TAG, "Not connected when screen off, forcing immediate reconnect");
                        connect();
                    }
                    
                    // Schedule additional connection checks (only if WiFi lock is enabled)
                    if (isWifiLockEnabled) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (isStickyConnection && !isConnected && isWifiLockEnabled) {
                                    Log.d(TAG, "Screen off - periodic connection check");
                                    connect();
                                    handler.postDelayed(this, 10000); // Check every 10 seconds
                                }
                            }
                        }, 10000);
                    } else {
                        Log.d(TAG, "WiFi lock disabled - skipping periodic connection checks");
                    }
                    
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    Log.d(TAG, "Screen turned on, checking connection");
                    // Screen turned on, check connection
                    if (!isConnected) {
                        connect();
                    }
                    
                    // Ensure wake locks are still held
                    if (!wifiWakeLock.isHeld()) {
                        wifiWakeLock.acquire(WIFI_LOCK_TIMEOUT);
                    }
                    if (!cpuWakeLock.isHeld()) {
                        cpuWakeLock.acquire();
                    }
                    if (!wifiLock.isHeld()) {
                        wifiLock.acquire();
                    }
                }
            }
        };

        // Register the receivers for more WiFi states
        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        appContext.registerReceiver(wifiStateReceiver, wifiFilter);

        // Register screen state receiver
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        appContext.registerReceiver(screenStateReceiver, screenFilter);

        // Start connection checker
        startConnectionChecker();
        
        // Start ping mechanism
        pingHandler.post(pingRunnable);
    }

    // Public singleton getter
    public static Esp8266Service getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (Esp8266Service.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Esp8266Service(context);
                }
            }
        }
        return INSTANCE;
    }

    public void cleanup() {
        stopDashboardUpdates();
        stopNavigationUpdates();
        backgroundThread.quitSafely();
        
        if (pingHandler != null && pingRunnable != null) {
            pingHandler.removeCallbacks(pingRunnable);
        }
        
        if (wifiWakeLock != null && wifiWakeLock.isHeld()) {
            wifiWakeLock.release();
        }
        
        if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
            cpuWakeLock.release();
        }
        
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        
        if (!isStickyConnection) {
            if (wifiStateReceiver != null) {
                try {
                    appContext.unregisterReceiver(wifiStateReceiver);
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering WiFi receiver", e);
                }
            }
            if (screenStateReceiver != null) {
                try {
                    appContext.unregisterReceiver(screenStateReceiver);
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering screen state receiver", e);
                }
            }
            handler.removeCallbacks(connectionChecker);
            disconnect();
        }
        
        databaseExecutor.shutdown();
    }

    private OnConnectionListener getConnectionListener() {
        synchronized (this) {
            return connectionListener;
        }
    }

    public void setConnectionListener(OnConnectionListener listener) {
        synchronized (this) {
            this.connectionListener = listener;
        }
        Log.d(TAG, "Connection listener " + (listener != null ? "set" : "cleared"));
    }

    private OnDataListener getDataListener() {
        synchronized (this) {
            return dataListener;
        }
    }

    public void setDataListener(OnDataListener listener) {
        synchronized (this) {
            this.dataListener = listener;
        }
        Log.d(TAG, "Data listener " + (listener != null ? "set" : "cleared"));
    }

    private void connectToEspNetwork() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            // Wait for WiFi to enable
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Check if already connected to ESP network
        WifiInfo currentWifi = wifiManager.getConnectionInfo();
        String currentSsid = currentWifi != null ? currentWifi.getSSID().replace("\"", "") : "";
        if (ESP_SSID.equals(currentSsid)) {
            Log.d(TAG, "Already connected to ESP network");
            return;
        }

        // Find existing network configuration
        WifiConfiguration existingConfig = null;
        try {
            for (WifiConfiguration conf : wifiManager.getConfiguredNetworks()) {
                if (conf.SSID != null && conf.SSID.equals("\"" + ESP_SSID + "\"")) {
                    existingConfig = conf;
                    break;
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission required to access configured networks: " + e.getMessage());
            // Notify user about permission requirement
            if (getConnectionListener() != null) {
                handler.post(() -> getConnectionListener().onError("Location permission required for WiFi access. Please grant location permission in app settings."));
            }
            // Continue without checking existing networks - will create new configuration
        }

        int netId;
        if (existingConfig != null) {
            netId = existingConfig.networkId;
            Log.d(TAG, "Found existing network configuration");
        } else {
            // Create new configuration
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + ESP_SSID + "\"";
            conf.preSharedKey = "\"" + ESP_PASSWORD + "\"";
            
            // Set security type
            conf.allowedProtocols.clear();
            conf.allowedKeyManagement.clear();
            conf.allowedPairwiseCiphers.clear();
            conf.allowedGroupCiphers.clear();
            
            conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            
            try {
                netId = wifiManager.addNetwork(conf);
                Log.d(TAG, "Added new network configuration, netId: " + netId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to add network configuration", e);
                netId = -1;
            }
        }

        if (netId == -1) {
            Log.e(TAG, "Failed to add network configuration");
            return;
        }

        // Disconnect current network
        try {
            wifiManager.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "Error disconnecting from current network", e);
        }

        // Enable our network
        boolean enabled = false;
        try {
            enabled = wifiManager.enableNetwork(netId, true);
            Log.d(TAG, "Network enabled: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Error enabling network", e);
        }

        // Reconnect
        boolean reconnected = false;
        try {
            reconnected = wifiManager.reconnect();
            Log.d(TAG, "Network reconnected: " + reconnected);
        } catch (Exception e) {
            Log.e(TAG, "Error reconnecting to network", e);
        }

        // Wait for connection
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void connect() {
        if (isConnected) {
            if (getConnectionListener() != null) {
                handler.post(() -> getConnectionListener().onConnected());
            }
            return;
        }

        if (isReconnecting) {
            Log.d(TAG, "Already attempting to reconnect, skipping");
            return;
        }

        isReconnecting = true;

        // Run connection in background thread
        new Thread(() -> {
            try {
                // Acquire wake locks
                if (!wifiWakeLock.isHeld()) {
                    wifiWakeLock.acquire(WIFI_LOCK_TIMEOUT);
                }
                if (!cpuWakeLock.isHeld()) {
                    cpuWakeLock.acquire();
                }
                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                }

                // Disable WiFi power saving
                disableWifiPowerSave();

                // Connect to ESP network
                connectToEspNetwork();

                // Verify WiFi connection
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String currentSsid = wifiInfo != null ? wifiInfo.getSSID().replace("\"", "") : "";
                
                if (!ESP_SSID.equals(currentSsid)) {
                    Log.e(TAG, "Failed to connect to ESP network");
                    handleConnectionError("Could not connect to ESP network");
                    return;
                }

                // Wait for network to stabilize
                Thread.sleep(2000);

                // Try to connect to ESP service
                try {
                    Response<ResponseBody> statusResponse = api.getStatus().execute();
                    if (!statusResponse.isSuccessful() || statusResponse.body() == null) {
                        throw new IOException("Status check failed");
                    }
                    
                    isConnected = true;
                    currentRetry = 0;
                    isReconnecting = false;
                    
                    Log.d(TAG, "Successfully connected to ESP8266");
                    if (getConnectionListener() != null) {
                        handler.post(() -> getConnectionListener().onConnected());
                    }
                    
                    // Start background updates
                    startBackgroundUpdates();
                    
                    // Start ping mechanism to keep connection alive
                    if (pingHandler != null && pingRunnable != null) {
                        pingHandler.removeCallbacks(pingRunnable);
                        pingHandler.post(pingRunnable);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Status check failed", e);
                    handleConnectionError("ESP8266 not responding");
                }
            } catch (Exception e) {
                Log.e(TAG, "Connection error", e);
                handleConnectionError("Connection failed: " + e.getMessage());
            }
        }).start();
    }

    public void setStickyConnection(boolean sticky) {
        this.isStickyConnection = sticky;
        if (sticky && !isConnected) {
            connect();
        }
    }
    
    public void setWifiLockEnabled(boolean enabled) {
        this.isWifiLockEnabled = enabled;
        Log.d(TAG, "WiFi lock " + (enabled ? "enabled" : "disabled"));
        
        if (!enabled) {
            // Release WiFi locks when disabled
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                Log.d(TAG, "WiFi lock released");
            }
            if (wifiWakeLock != null && wifiWakeLock.isHeld()) {
                wifiWakeLock.release();
                Log.d(TAG, "WiFi wake lock released");
            }
        } else {
            // Re-acquire locks when enabled
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
                Log.d(TAG, "WiFi lock re-acquired");
            }
            if (wifiWakeLock != null && !wifiWakeLock.isHeld()) {
                wifiWakeLock.acquire(WIFI_LOCK_TIMEOUT);
                Log.d(TAG, "WiFi wake lock re-acquired");
            }
        }
    }
    
    public boolean isWifiLockEnabled() {
        return isWifiLockEnabled;
    }

    private void verifyConnection() {
        if (!isConnected || isReconnecting) {
            return;
        }

        new Thread(() -> {
            try {
                // Add delay before verification
                Thread.sleep(1000);

                // Try to get status first as it's a simpler endpoint
                try {
                    Response<ResponseBody> statusResponse = api.getStatus().execute();
                    if (!statusResponse.isSuccessful() || statusResponse.body() == null) {
                        Log.e(TAG, "Status check failed");
                        if (isStickyConnection) {
                            handleConnectionError("ESP8266 not responding");
                        } else {
                            disconnect();
                        }
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Status check failed", e);
                    if (isStickyConnection) {
                        handleConnectionError("ESP8266 not responding");
                    } else {
                        disconnect();
                    }
                    return;
                }

                // After a successful status check in verifyConnection(), log success:
                Log.d(TAG, "Connection verification successful");
            } catch (Exception e) {
                Log.e(TAG, "Connection verification failed", e);
                if (isStickyConnection) {
                    handleConnectionError("Connection lost: " + e.getMessage());
                } else {
                    disconnect();
                }
            }
        }).start();
    }

    private boolean pingEsp8266() {
        // Run ping on background thread to avoid NetworkOnMainThreadException
        final boolean[] result = {false};
        final CountDownLatch latch = new CountDownLatch(1);
        
        new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("192.168.4.1", 80), PING_TIMEOUT);
                socket.close();
                result[0] = true;
            } catch (Exception e) {
                Log.e(TAG, "Ping failed", e);
                result[0] = false;
            } finally {
                latch.countDown();
            }
        }).start();
        
        try {
            latch.await(PING_TIMEOUT + 1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Ping interrupted", e);
            Thread.currentThread().interrupt();
        }
        
        return result[0];
    }

    private void handleConnectionError(String error) {
        Log.e(TAG, "Connection error: " + error);
        isConnected = false;
        
        if (getConnectionListener() != null) {
            handler.post(() -> getConnectionListener().onError(error));
        }
        
        if (!isStickyConnection) {
            isReconnecting = false;
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastReconnectAttempt) < RECONNECT_COOLDOWN) {
            Log.d(TAG, "Skipping reconnect - in cooldown");
            isReconnecting = false;
            return;
        }
        
        if (currentRetry < MAX_RETRIES) {
            currentRetry++;
            lastReconnectAttempt = currentTime;
            
            Log.d(TAG, "Attempting to reconnect (attempt " + currentRetry + " of " + MAX_RETRIES + ")");
            
            // Schedule reconnect attempt
            handler.postDelayed(() -> {
                isReconnecting = false; // Reset flag before trying again
                connect();
            }, RETRY_DELAY * currentRetry); // Exponential backoff
        } else {
            Log.e(TAG, "Max reconnection attempts reached");
            currentRetry = 0;
            isReconnecting = false;
            
            // Reset and try again after cooldown
            handler.postDelayed(() -> {
                currentRetry = 0;
                isReconnecting = false;
                connect();
            }, RECONNECT_COOLDOWN * 2);
        }
    }

    public void disconnect() {
        isConnected = false;
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
            cpuWakeLock.release();
        }
        if (getConnectionListener() != null) {
            handler.post(() -> getConnectionListener().onDisconnected());
        }
    }

    public void fetchDashboardData() {
        if (!isConnected) {
            final OnDataListener currentListener = getDataListener();
            if (currentListener != null) {
                handler.post(() -> {
                    try {
                        if (currentListener != null) {
                            currentListener.onError("Not connected to ESP8266");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error notifying data listener", e);
                    }
                });
            }
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDashboardRequestTime < MIN_REQUEST_INTERVAL) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL - (currentTime - lastDashboardRequestTime));
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for rate limit", e);
            }
        }
        lastDashboardRequestTime = System.currentTimeMillis();

        // First verify connection is still valid
        new Thread(() -> {
            try {
                Response<ResponseBody> statusResponse = api.getStatus().execute();
                if (!statusResponse.isSuccessful()) {
                    Log.e(TAG, "Status check failed before dashboard request");
                    handleConnectionError("Lost connection to ESP8266");
                    return;
                }

                // Now fetch dashboard data with retries
                int retryCount = 0;
                while (retryCount < 3) {
                    try {
                        Response<ResponseBody> response = api.getDashboardData().execute();
                        if (response.isSuccessful() && response.body() != null) {
                            String jsonString = response.body().string();
                            JSONObject data = new JSONObject(jsonString);
                            
                            // Save to database
                            saveToDatabase(data);
                            
                            // Safely notify listener with proper null check and synchronization
                            final OnDataListener currentListener = getDataListener();
                            if (currentListener != null) {
                                handler.post(() -> {
                                    try {
                                        if (currentListener != null) {
                                            currentListener.onDashboardData(data);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error notifying data listener", e);
                                    }
                                });
                            }
                            return;
                        }
                        retryCount++;
                        if (retryCount < 3) {
                            Thread.sleep(1000);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error fetching dashboard data (attempt " + (retryCount + 1) + ")", e);
                        retryCount++;
                        if (retryCount < 3) {
                            Thread.sleep(1000);
                        }
                    }
                }
                
                // Safely notify listener about failure
                final OnDataListener currentListener = getDataListener();
                if (currentListener != null) {
                    handler.post(() -> {
                        try {
                            if (currentListener != null) {
                                currentListener.onError("Failed to fetch data after 3 attempts");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error notifying data listener of failure", e);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in dashboard data thread", e);
                // Safely notify listener about error
                final OnDataListener currentListener = getDataListener();
                if (currentListener != null) {
                    handler.post(() -> {
                        try {
                            if (currentListener != null) {
                                currentListener.onError("Error: " + e.getMessage());
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error notifying data listener of exception", ex);
                        }
                    });
                }
            }
        }).start();
    }

    private void saveToDatabase(JSONObject data) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                DashboardData dashboardData = new DashboardData();
                dashboardData.speed = data.optDouble("speed", 0.0);
                dashboardData.fuelPercentage = data.optDouble("fuel", 0.0);
                dashboardData.fuelLiters = data.optDouble("fuel_liters", 0.0);
                dashboardData.instantEconomy = data.optDouble("fuel_economy", 0.0);
                dashboardData.totalDistance = data.optDouble("odometer", 0.0);
                dashboardData.trip1Distance = data.optDouble("trip1", 0.0);
                dashboardData.trip1Fuel = data.optDouble("trip1_fuel_used", 0.0);
                dashboardData.trip1Average = data.optDouble("trip1_fuel_avg", 0.0);
                dashboardData.trip1Started = data.optBoolean("trip1_started", false);
                dashboardData.trip2Distance = data.optDouble("trip2", 0.0);
                dashboardData.trip2Fuel = data.optDouble("trip2_fuel_used", 0.0);
                dashboardData.trip2Average = data.optDouble("trip2_fuel_avg", 0.0);
                dashboardData.trip2Started = data.optBoolean("trip2_started", false);
                dashboardData.fuelFillAverage = data.optDouble("fuel_fill_avg", 0.0);
                dashboardData.fuelFillDistance = data.optDouble("fuel_fill_distance", 0.0);
                dashboardData.lastFuelFill = data.optDouble("last_fuel_fill", 0.0);
                dashboardData.fuelFillStarted = data.optBoolean("fuel_fill_started", false);
                dashboardData.fuelUsedSinceFill = data.optDouble("fuel_used_since_fill", 0.0);

                database.dashboardDao().insert(dashboardData);

                // Clean up old data (keep last 7 days)
                long oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
                database.dashboardDao().deleteOldData(oneWeekAgo);
            } catch (Exception e) {
                Log.e(TAG, "Error saving to database", e);
            } finally {
                executor.shutdown();
            }
        });
    }

    public void startTrip(int tripNumber) {
        if (!isConnected) {
            return;
        }
        api.startTrip(tripNumber).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                final OnDataListener currentListener = getDataListener();
                if (currentListener != null) {
                    handler.post(() -> fetchDashboardData());
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // Silent error handling
            }
        });
    }

    public void resetTrip(int tripNumber) {
        if (!isConnected) {
            return;
        }
        api.resetTrip(tripNumber).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    startTrip(tripNumber);
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // Silent error handling
            }
        });
    }

    public void resetAllTrips() {
        if (!isConnected) {
            connect();
            return;
        }

        new Thread(() -> {
            int retryCount = 0;
            boolean success = false;
            
            while (!success && retryCount < 3) {
                try {
                    ResponseBody response = api.getStatus().execute().body();
                    if (response == null) {
                        disconnect();
                        connect();
                        Thread.sleep(2000);
                        continue;
                    }

                    ResponseBody resetResponse = api.resetAllTrips().execute().body();
                    if (resetResponse != null) {
                        success = true;
                        handler.postDelayed(this::fetchDashboardData, 1000);
                    } else {
                        retryCount++;
                        if (retryCount < 3) {
                            Thread.sleep(1000);
                        }
                    }
                } catch (Exception e) {
                    retryCount++;
                    if (retryCount < 3) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (!success) {
                disconnect();
                connect();
            }
        }).start();
    }

    public void resetOdometer() {
        if (!isConnected) {
            return;
        }

        api.resetOdometer().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    handler.postDelayed(() -> fetchDashboardData(), 500);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // Silent error handling
            }
        });
    }

    public void resetFuelFill() {
        if (!isConnected) {
            return;
        }

        api.resetFuelFill().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    handler.postDelayed(() -> fetchDashboardData(), 500);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // Silent error handling
            }
        });
    }

    public void uploadFirmware(String username, String password, okhttp3.MultipartBody.Part firmwareFile, FirmwareUpdateCallback callback) {
        if (!isConnected) {
            callback.onError("Not connected to ESP8266");
            return;
        }

        String auth = username + ":" + password;
        String encodedAuth = android.util.Base64.encodeToString(
            auth.getBytes(), android.util.Base64.NO_WRAP);

        api.uploadFirmware("Basic " + encodedAuth, firmwareFile).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to upload firmware: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError("Failed to upload firmware: " + t.getMessage());
            }
        });
    }

    public boolean isConnected() {
        return isConnected;
    }
    
    public String getConnectionStatus() {
        if (!isConnected) {
            return "Disconnected";
        }
        
        // Check WiFi connection
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String currentSsid = wifiInfo != null ? wifiInfo.getSSID().replace("\"", "") : "";
        
        if (ESP_SSID.equals(currentSsid)) {
            return "Connected to ESP8266 (" + ESP_SSID + ")";
        } else {
            return "Connected but wrong network (" + currentSsid + ")";
        }
    }
    
    public boolean hasLocationPermission() {
        return appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == 
               android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    
    public String getPermissionStatus() {
        if (!hasLocationPermission()) {
            return "Location permission required for WiFi network access. Go to Settings > Apps > Activa Dashboard > Permissions to grant location permission.";
        }
        return "All permissions granted";
    }
    
    public boolean canAccessWifiNetworks() {
        return hasLocationPermission();
    }
    
    public boolean shouldShowPermissionRequest() {
        return !hasLocationPermission() && !hasShownPermissionDialog();
    }
    
    private boolean hasShownPermissionDialog() {
        // This could be stored in SharedPreferences for persistence
        return false; // For now, always show if permission is missing
    }

    private void startConnectionChecker() {
        handler.removeCallbacks(connectionChecker);
        connectionChecker = new Runnable() {
            @Override
            public void run() {
                if (isConnected && !isReconnecting) {
                    // Check if still connected to ESP8266 network
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo == null) {
                        Log.e(TAG, "WiFiInfo is null - WiFi may be disabled or permissions not granted");
                        handleConnectionError("WiFi not available");
                        return;
                    }

                    String ssid = wifiInfo.getSSID();
                    if (ssid == null) {
                        Log.e(TAG, "SSID is null - WiFi may be disabled or permissions not granted");
                        handleConnectionError("WiFi not available");
                        return;
                    }

                    // Remove quotes and trim
                    ssid = ssid.replace("\"", "").trim();
                    Log.d(TAG, "Current SSID: " + ssid + ", Expected SSID: " + ESP_SSID);
                    
                    boolean isConnectedToEsp = ssid.equals(ESP_SSID);
                    Log.d(TAG, "Is connected to ESP: " + isConnectedToEsp);
                    
                    if (!isConnectedToEsp) {
                        Log.d(TAG, "Lost connection to ESP8266 network");
                        handleConnectionError("Lost WiFi connection");
                    } else {
                        // Only verify connection if we're not already reconnecting
                        verifyConnection();
                    }
                }
                // Schedule next check
                handler.postDelayed(this, CONNECTION_CHECK_INTERVAL);
            }
        };
        handler.post(connectionChecker);
    }

    public void syncData(String data, OnSyncListener listener) {
        if (!isConnected) {
            if (listener != null) {
                listener.onSyncError("Not connected to ESP8266");
            }
            return;
        }

        // Rate limit navigation updates
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNavigationUpdateTime < NAVIGATION_UPDATE_INTERVAL) {
            if (listener != null) {
                listener.onSyncError("Too many requests");
            }
            return;
        }
        lastNavigationUpdateTime = currentTime;

        // Create request body
        RequestBody body = RequestBody.create(
            MediaType.parse("application/json"), data);

        // Send using Retrofit API
        api.sendNavigationData(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseString = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseString);
                        if (listener != null) {
                            handler.post(() -> listener.onSyncSuccess(jsonResponse));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing navigation response", e);
                        if (listener != null) {
                            handler.post(() -> listener.onSyncError("Error parsing response: " + e.getMessage()));
                        }
                    }
                } else {
                    Log.e(TAG, "Navigation sync failed with code: " + response.code());
                    if (listener != null) {
                        handler.post(() -> listener.onSyncError("Sync failed: " + response.code()));
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Navigation sync network error", t);
                if (listener != null) {
                    handler.post(() -> listener.onSyncError("Network error: " + t.getMessage()));
                }
            }
        });
    }

    // Add methods for backlight control
    public void toggleBacklight() {
        // Send GET request to /lcd-backlight?state=on or off (implement toggle logic)
        // For simplicity, toggle between on and off; in practice, query current state if needed
        String state = "on";  // Default or query logic here
        api.setBacklightState(state).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Backlight toggled");
                } else {
                    Log.e(TAG, "Toggle failed");
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Toggle failure: " + t.getMessage());
            }
        });
    }

    public void setBacklightIntensity(int intensity) {
        api.setBacklightIntensity(String.valueOf(intensity)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Backlight intensity set to " + intensity);
                } else {
                    Log.e(TAG, "Intensity set failed");
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Intensity failure: " + t.getMessage());
            }
        });
    }

    public void restartLcd() {
        if (!isConnected) return;
        
        new Thread(() -> {
            try {
                Response<ResponseBody> response = api.restartLcd().execute();
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Failed to restart LCD");
                    final OnDataListener currentListener = getDataListener();
                    if (currentListener != null) {
                        handler.post(() -> {
                            try {
                                if (currentListener != null) {
                                    currentListener.onError("Failed to restart LCD");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error notifying data listener", e);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error restarting LCD", e);
                final OnDataListener currentListener = getDataListener();
                if (currentListener != null) {
                    handler.post(() -> {
                        try {
                            if (currentListener != null) {
                                currentListener.onError("Error restarting LCD: " + e.getMessage());
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error notifying data listener", ex);
                        }
                    });
                }
            }
        }).start();
    }
    
    public void setAutoRestartLcd(boolean enabled) {
        if (!isConnected) return;
        
        new Thread(() -> {
            try {
                Response<ResponseBody> response = api.setAutoRestart(enabled).execute();
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Failed to set LCD auto-restart");
                    final OnDataListener currentListener = getDataListener();
                    if (currentListener != null) {
                        handler.post(() -> {
                            try {
                                if (currentListener != null) {
                                    currentListener.onError("Failed to set LCD auto-restart");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error notifying data listener", e);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting LCD auto-restart", e);
                final OnDataListener currentListener = getDataListener();
                if (currentListener != null) {
                    handler.post(() -> {
                        try {
                            if (currentListener != null) {
                                currentListener.onError("Error setting LCD auto-restart: " + e.getMessage());
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error notifying data listener", ex);
                        }
                    });
                }
            }
        }).start();
    }

    // Add method to start background updates
    public void startBackgroundUpdates() {
        startDashboardUpdates();
    }

    private void startDashboardUpdates() {
        if (isDashboardUpdateRunning) return;
        isDashboardUpdateRunning = true;

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isDashboardUpdateRunning) return;

                fetchDashboardData();
                backgroundHandler.postDelayed(this, MIN_REQUEST_INTERVAL);
            }
        });
    }

    public void stopDashboardUpdates() {
        isDashboardUpdateRunning = false;
    }

    public void updateNavigationData(JSONObject navigationData) {
        lastNavigationData = navigationData;
        if (!isNavigationUpdateRunning) {
            startNavigationUpdates();
        }
    }

    private void startNavigationUpdates() {
        if (isNavigationUpdateRunning) return;
        isNavigationUpdateRunning = true;

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isNavigationUpdateRunning || lastNavigationData == null) {
                    isNavigationUpdateRunning = false;
                    return;
                }

                syncData(lastNavigationData.toString(), new OnSyncListener() {
                    @Override
                    public void onSyncSuccess(JSONObject response) {
                        Log.d(TAG, "Navigation data sent successfully");
                    }

                    @Override
                    public void onSyncError(String error) {
                        Log.e(TAG, "Error sending navigation data: " + error);
                    }
                });

                backgroundHandler.postDelayed(this, NAVIGATION_UPDATE_INTERVAL);
            }
        });
    }

    public void stopNavigationUpdates() {
        isNavigationUpdateRunning = false;
        lastNavigationData = null;
    }

    private void ensureWifiConnection() {
        Log.d(TAG, "Ensuring WiFi connection - acquiring all wake locks");
        
        // Acquire wake locks when screen goes off with longer timeouts
        if (!wifiWakeLock.isHeld()) {
            wifiWakeLock.acquire(WIFI_LOCK_TIMEOUT);
            Log.d(TAG, "WiFi wake lock acquired");
        }
        if (!cpuWakeLock.isHeld()) {
            cpuWakeLock.acquire();
            Log.d(TAG, "CPU wake lock acquired");
        }
        if (!wifiLock.isHeld()) {
            wifiLock.acquire();
            Log.d(TAG, "WiFi lock acquired");
        }

        // Ensure WiFi is enabled and not in power save mode
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            Log.d(TAG, "WiFi enabled");
        }

        // Try to disable WiFi power saving using multiple methods
        disableWifiPowerSave();

        // Check if connected to ESP network
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String currentSsid = wifiInfo != null ? wifiInfo.getSSID().replace("\"", "") : "";
        
        Log.d(TAG, "Current SSID: " + currentSsid + ", Expected SSID: " + ESP_SSID);
        
        if (!ESP_SSID.equals(currentSsid)) {
            Log.d(TAG, "Not connected to ESP network when screen off, reconnecting...");
            connectToEspNetwork();
        } else {
            Log.d(TAG, "Is connected to ESP: " + isConnected);
        }
        
        // Ensure WiFi lock is held and schedule periodic re-acquisition
        if (!wifiLock.isHeld()) {
            wifiLock.acquire();
            Log.d(TAG, "WiFi lock re-acquired in ensureWifiConnection");
        }
        
        // Schedule periodic wake lock refresh
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isStickyConnection) {
                    // Re-acquire wake locks periodically to prevent timeout
                    if (!wifiWakeLock.isHeld()) {
                        wifiWakeLock.acquire(WIFI_LOCK_TIMEOUT);
                    }
                    if (!cpuWakeLock.isHeld()) {
                        cpuWakeLock.acquire();
                    }
                    if (!wifiLock.isHeld()) {
                        wifiLock.acquire();
                    }
                    
                    // Schedule next refresh
                    handler.postDelayed(this, WIFI_LOCK_TIMEOUT / 2); // Refresh every 5 minutes
                }
            }
        }, WIFI_LOCK_TIMEOUT / 2);
    }
    
    private void disableWifiPowerSave() {
        Log.d(TAG, "Attempting to optimize WiFi power settings for ESP8266 connection...");
        
        // Method 1: Try to disable WiFi power saving using reflection (vendor-specific)
        try {
            Class<?> wifiManagerClass = wifiManager.getClass();
            java.lang.reflect.Method setWifiPowerSaveMethod = wifiManagerClass.getMethod("setWifiPowerSave", boolean.class);
            setWifiPowerSaveMethod.invoke(wifiManager, false);
            Log.d(TAG, "✓ WiFi power save disabled via reflection");
        } catch (Exception e) {
            Log.d(TAG, "ℹ WiFi power save via reflection not available: " + e.getMessage());
        }
        
        // Method 2: Try to disable WiFi power saving using system settings
        try {
            android.provider.Settings.Global.putInt(appContext.getContentResolver(), 
                "wifi_sleep_policy", 2); // Never sleep
            Log.d(TAG, "✓ WiFi sleep policy set to never sleep");
        } catch (SecurityException e) {
            Log.d(TAG, "ℹ WiFi sleep policy requires WRITE_SECURE_SETTINGS permission (system app only)");
        } catch (Exception e) {
            Log.d(TAG, "ℹ Could not set WiFi sleep policy: " + e.getMessage());
        }
        
        // Method 3: Try to disable WiFi power saving using secure settings
        try {
            android.provider.Settings.Secure.putInt(appContext.getContentResolver(), 
                "wifi_sleep_policy", 2); // Never sleep
            Log.d(TAG, "✓ WiFi sleep policy set to never sleep (secure)");
        } catch (SecurityException e) {
            Log.d(TAG, "ℹ WiFi sleep policy (secure) requires WRITE_SECURE_SETTINGS permission (system app only)");
        } catch (Exception e) {
            Log.d(TAG, "ℹ Could not set WiFi sleep policy (secure): " + e.getMessage());
        }
        
        // Method 4: Try to disable WiFi power saving using shell command (requires root)
        try {
            java.lang.Process process = Runtime.getRuntime().exec("su -c 'settings put global wifi_sleep_policy 2'");
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                Log.d(TAG, "✓ WiFi sleep policy set via shell command (root)");
            } else {
                Log.d(TAG, "ℹ WiFi sleep policy via shell requires root access");
            }
        } catch (Exception e) {
            Log.d(TAG, "ℹ WiFi sleep policy via shell not available: " + e.getMessage());
        }
        
        // Note: These warnings are expected on Android 11+ without system permissions
        // The app will still work with WiFi locks and wake locks for connection stability
        Log.d(TAG, "WiFi power optimization complete - using WiFi locks and wake locks for connection stability");
    }
} 