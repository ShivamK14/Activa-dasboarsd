package com.example.activadasboard.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class VehicleDataStorage {
    private static final String TAG = "VehicleDataStorage";
    private static final String PREF_NAME = "vehicle_data";
    private static final String KEY_ODO = "odo";
    private static final String KEY_TRIP = "trip";
    private static final String KEY_TRIP2 = "trip2";
    private static final String KEY_FUEL_LEVEL = "fuel_level";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final String KEY_IS_CONNECTED = "is_connected";
    private static final String KEY_RESET_COUNT = "reset_count";
    private static final String KEY_TRIP1_RESET_COUNT = "trip1_reset_count";
    private static final String KEY_TRIP2_RESET_COUNT = "trip2_reset_count";

    private final SharedPreferences preferences;
    private static VehicleDataStorage instance;

    private VehicleDataStorage(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized VehicleDataStorage getInstance(Context context) {
        if (instance == null) {
            instance = new VehicleDataStorage(context);
        }
        return instance;
    }

    public void saveVehicleData(float odo, float trip1, float trip2, float fuelLevel, boolean isConnected) {
        // Always save data, regardless of connection status
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat(KEY_ODO, odo);
        editor.putFloat(KEY_TRIP, trip1);
        editor.putFloat(KEY_TRIP2, trip2);
        editor.putFloat(KEY_FUEL_LEVEL, fuelLevel);
        editor.putLong(KEY_LAST_UPDATE, System.currentTimeMillis());
        editor.putBoolean(KEY_IS_CONNECTED, isConnected);
        editor.apply();
        Log.d(TAG, "Saved vehicle data - ODO: " + odo + ", Trip1: " + trip1 + ", Trip2: " + trip2 + ", Fuel: " + fuelLevel);
    }

    public float getOdo() {
        return preferences.getFloat(KEY_ODO, 0f);
    }

    public float getTrip() {
        return preferences.getFloat(KEY_TRIP, 0f);
    }

    public float getTrip2() {
        return preferences.getFloat(KEY_TRIP2, 0f);
    }

    public float getFuelLevel() {
        return preferences.getFloat(KEY_FUEL_LEVEL, 0f);
    }

    public long getLastUpdateTime() {
        return preferences.getLong(KEY_LAST_UPDATE, 0);
    }

    public boolean isConnected() {
        return preferences.getBoolean(KEY_IS_CONNECTED, false);
    }

    public void clearData() {
        preferences.edit().clear().apply();
        Log.d(TAG, "Cleared all stored vehicle data");
    }

    public int getResetCount() {
        return preferences.getInt(KEY_RESET_COUNT, 0);
    }

    public void setResetCount(int count) {
        preferences.edit().putInt(KEY_RESET_COUNT, count).apply();
    }

    public void incrementResetCount() {
        int count = getResetCount() + 1;
        setResetCount(count);
    }

    public int getTrip1ResetCount() {
        return preferences.getInt(KEY_TRIP1_RESET_COUNT, 0);
    }

    public int getTrip2ResetCount() {
        return preferences.getInt(KEY_TRIP2_RESET_COUNT, 0);
    }

    public void setTrip1ResetCount(int count) {
        preferences.edit().putInt(KEY_TRIP1_RESET_COUNT, count).apply();
    }

    public void setTrip2ResetCount(int count) {
        preferences.edit().putInt(KEY_TRIP2_RESET_COUNT, count).apply();
    }
} 