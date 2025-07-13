package com.example.activadasboard.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

public class Esp8266Service {
    private static final String TAG = "Esp8266Service";
    private static final String BASE_URL = "http://192.168.4.1";
    private static final String ESP_SSID = "Activa_Dashboard";
    private static final int CONNECTION_TIMEOUT = 3000;
    private static final int READ_TIMEOUT = 3000;
    private static final int WRITE_TIMEOUT = 3000;
    private static final int ERROR_DISPLAY_DURATION = 2000;
    private static final int DNS_TIMEOUT = 1000;
    private static final int CONNECTION_CHECK_INTERVAL = 1000;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY = 1000;
    private static final int CONNECTION_VERIFICATION_TIMEOUT = 5000;

    private final OkHttpClient client;
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final WifiManager wifiManager;
    private final TelephonyManager telephonyManager;
    private final Handler mainHandler;
    private Network currentNetwork;
    private boolean isConnected = false;
    private boolean isInitialized = false;
    private long lastConnectionCheck = 0;
    private int retryCount = 0;
    private boolean isEspReachable = false;
    private long lastReachabilityCheck = 0;

    public Esp8266Service(Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .dns(hostname -> {
                    try {
                        InetAddress[] addresses = InetAddress.getAllByName(hostname);
                        if (addresses.length > 0) {
                            return java.util.Arrays.asList(addresses);
                        }
                    } catch (UnknownHostException e) {
                        Log.w(TAG, "DNS lookup failed for " + hostname + ": " + e.getMessage());
                    }
                    return java.util.Collections.emptyList();
                })
                .build();

        initializeService();
    }

    private void initializeService() {
        try {
            setupNetworkCallback();
            isInitialized = true;
            Log.d(TAG, "Service initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize service: " + e.getMessage());
            isInitialized = false;
            showError("Failed to initialize network service. Please restart the app.");
        }
    }

    private void setupNetworkCallback() {
        if (connectivityManager == null) {
            Log.e(TAG, "ConnectivityManager is null");
            return;
        }

        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);

        NetworkRequest networkRequest = builder.build();
        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                currentNetwork = network;
                isConnected = true;
                Log.d(TAG, "Network available");
                checkEspConnection();
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                if (currentNetwork != null && currentNetwork.equals(network)) {
                    currentNetwork = null;
                    isConnected = false;
                    Log.d(TAG, "Network lost");
                }
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                isConnected = false;
                Log.d(TAG, "Network unavailable");
            }
        };

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            Log.d(TAG, "Network callback registered successfully");
        } catch (Exception e) {
            Log.w(TAG, "Failed to register network callback: " + e.getMessage());
        }
    }

    private boolean verifyConnection() {
        // Check if we're connected to the right network first
        if (!isConnectedToEsp()) {
            Log.d(TAG, "Not connected to ESP8266 WiFi network");
            return false;
        }

        // Check if we've recently verified reachability
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastReachabilityCheck < CONNECTION_CHECK_INTERVAL) {
            return isEspReachable;
        }

        // Try multiple connection methods
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                // Method 1: Try direct socket connection
                try (Socket socket = new Socket()) {
                    socket.connect(new java.net.InetSocketAddress("192.168.4.1", 80), CONNECTION_TIMEOUT);
                    Log.d(TAG, "Direct socket connection successful");
                    isEspReachable = true;
                    lastReachabilityCheck = currentTime;
                    return true;
                }
            } catch (IOException e) {
                Log.w(TAG, "Socket connection attempt " + (i + 1) + " failed: " + e.getMessage());
            }

            try {
                // Method 2: Try HTTP request
                Request request = new Request.Builder()
                        .url(BASE_URL)
                        .get()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "HTTP connection successful");
                        isEspReachable = true;
                        lastReachabilityCheck = currentTime;
                        return true;
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "HTTP connection attempt " + (i + 1) + " failed: " + e.getMessage());
            }

            if (i < MAX_RETRIES - 1) {
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        isEspReachable = false;
        lastReachabilityCheck = currentTime;
        return false;
    }

    private void checkEspConnection() {
        if (System.currentTimeMillis() - lastConnectionCheck < CONNECTION_CHECK_INTERVAL) {
            return;
        }
        lastConnectionCheck = System.currentTimeMillis();

        new Thread(() -> {
            boolean isReachable = verifyConnection();
            Log.d(TAG, "ESP8266 reachable: " + isReachable);
            if (!isReachable) {
                showError("Cannot reach ESP8266. Please check your connection to " + ESP_SSID);
            }
        }).start();
    }

    public boolean isConnectedToEsp() {
        if (!isInitialized) {
            Log.e(TAG, "Service not initialized");
            return false;
        }

        if (!isConnected) {
            Log.d(TAG, "Not connected to any network");
            return false;
        }

        try {
            if (wifiManager == null) {
                Log.e(TAG, "WifiManager is null");
                return false;
            }

            android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) {
                Log.d(TAG, "WifiInfo is null");
                return false;
            }

            String ssid = wifiInfo.getSSID();
            if (ssid == null) {
                Log.d(TAG, "SSID is null");
                return false;
            }

            ssid = ssid.replace("\"", "");
            boolean isEspConnected = ESP_SSID.equals(ssid);
            Log.d(TAG, "Connected to ESP: " + isEspConnected + " (SSID: " + ssid + ")");
            
            if (isEspConnected) {
                // Only check reachability if we're connected to the right network
                checkEspConnection();
            }
            
            return isEspConnected;
        } catch (Exception e) {
            Log.w(TAG, "Error checking WiFi connection: " + e.getMessage());
            return false;
        }
    }

    public void uploadFirmware(Uri fileUri, String username, String password, 
                             OnProgressListener progressListener, 
                             OnCompleteListener completeListener) {
        if (!isInitialized) {
            String errorMessage = "Service not initialized. Please restart the app.";
            Log.e(TAG, errorMessage);
            showErrorOnMainThread(errorMessage, completeListener);
            return;
        }

        if (!isConnectedToEsp()) {
            String errorMessage = "Not connected to ESP8266. Please connect to " + ESP_SSID + " first.";
            Log.e(TAG, errorMessage);
            showErrorOnMainThread(errorMessage, completeListener);
            return;
        }

        // Verify ESP is reachable before attempting upload
        if (!verifyConnection()) {
            String errorMessage = "Cannot reach ESP8266. Please check your connection and try again.";
            Log.e(TAG, errorMessage);
            showErrorOnMainThread(errorMessage, completeListener);
            return;
        }

        new Thread(() -> {
            try {
                RequestBody fileBody = new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return MediaType.parse("application/octet-stream");
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws IOException {
                        try (Source source = Okio.source(context.getContentResolver().openInputStream(fileUri))) {
                            sink.writeAll(source);
                        }
                    }
                };

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("update", getFileName(fileUri), fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url(BASE_URL + "/update")
                        .header("Authorization", "Basic " + 
                                android.util.Base64.encodeToString(
                                        (username + ":" + password).getBytes(),
                                        android.util.Base64.NO_WRAP))
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        showSuccessOnMainThread(completeListener);
                    } else {
                        String errorMessage = "Update failed: " + response.code();
                        Log.e(TAG, errorMessage);
                        showErrorOnMainThread(errorMessage, completeListener);
                    }
                }
            } catch (IOException e) {
                String errorMessage = "Connection error: " + e.getMessage();
                Log.e(TAG, errorMessage);
                showErrorOnMainThread(errorMessage, completeListener);
            }
        }).start();
    }

    private void showError(String message) {
        mainHandler.post(() -> {
            try {
                if (context != null) {
                    Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
                    toast.show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing toast: " + e.getMessage());
            }
        });
    }

    private void showErrorOnMainThread(String message, OnCompleteListener listener) {
        mainHandler.post(() -> {
            showError(message);
            if (listener != null) {
                listener.onError(message);
            }
        });
    }

    private void showSuccessOnMainThread(OnCompleteListener listener) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onSuccess();
            }
        });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error getting file name: " + e.getMessage());
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "firmware.bin";
    }

    public interface OnProgressListener {
        void onProgress(int progress);
    }

    public interface OnCompleteListener {
        void onSuccess();
        void onError(String error);
    }
} 