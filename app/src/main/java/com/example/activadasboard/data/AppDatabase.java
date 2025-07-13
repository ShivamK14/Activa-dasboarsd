package com.example.activadasboard.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.concurrent.Executors;

@Database(entities = {DashboardData.class, SearchHistory.class, OfflineDirections.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract DashboardDao dashboardDao();
    public abstract MapDao mapDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "dashboard_database")
                            .fallbackToDestructiveMigration()
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            Executors.newSingleThreadExecutor().execute(() -> {
                DashboardDao dao = INSTANCE.dashboardDao();
                dao.insert(createDummyData(1, 45.5, 120.5));
                dao.insert(createDummyData(2, 60.2, 150.2));
                dao.insert(createDummyData(3, 30.0, 95.8));
            });
        }
    };

    private static DashboardData createDummyData(int daysAgo, double speed, double distance) {
        DashboardData data = new DashboardData();
        data.timestamp = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000L);
        data.speed = speed;
        data.totalDistance = distance;
        // Add other fields if necessary
        return data;
    }
} 