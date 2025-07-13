package com.example.activadasboard.service;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface Esp8266Api {
    @GET("status")
    Call<ResponseBody> getStatus();

    @GET("dashboard-data")
    Call<ResponseBody> getDashboardData();

    @POST("reset-trip/{tripNum}")
    Call<ResponseBody> resetTrip(@Path("tripNum") int tripNum);

    @POST("reset-all-trips")
    Call<ResponseBody> resetAllTrips();

    @POST("reset-odometer")
    Call<ResponseBody> resetOdometer();

    @POST("reset-fuel-fill")
    Call<ResponseBody> resetFuelFill();

    @Multipart
    @POST("update")
    Call<ResponseBody> uploadFirmware(@Header("Authorization") String auth,
                                    @Part MultipartBody.Part firmware);

    @POST("start-trip/{tripNum}")
    Call<ResponseBody> startTrip(@Path("tripNum") int tripNum);

    @GET("lcd-backlight")
    Call<ResponseBody> setBacklightState(@Query("state") String state);

    @GET("lcd-backlight")
    Call<ResponseBody> setBacklightIntensity(@Query("intensity") String intensity);

    @POST("navigation")
    Call<ResponseBody> sendNavigationData(@Body RequestBody data);
    
    @POST("lcd/restart")
    Call<ResponseBody> restartLcd();
    
    @POST("lcd/auto-restart")
    Call<ResponseBody> setAutoRestart(@Query("enabled") boolean enabled);
} 