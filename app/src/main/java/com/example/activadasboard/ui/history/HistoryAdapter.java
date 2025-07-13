package com.example.activadasboard.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.activadasboard.R;
import com.example.activadasboard.data.DashboardData;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private List<DashboardData> dataList = Collections.emptyList();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        DashboardData data = dataList.get(position);
        holder.tripDate.setText(dateFormat.format(new Date(data.timestamp)));
        holder.tripDistance.setText(String.format(Locale.getDefault(), "Distance: %.2f km", data.totalDistance));
        holder.tripAvgSpeed.setText(String.format(Locale.getDefault(), "Avg Speed: %.1f km/h", data.speed));
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public void setData(List<DashboardData> data) {
        this.dataList = data;
        notifyDataSetChanged();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tripDate;
        TextView tripDistance;
        TextView tripAvgSpeed;

        HistoryViewHolder(View itemView) {
            super(itemView);
            tripDate = itemView.findViewById(R.id.trip_date);
            tripDistance = itemView.findViewById(R.id.trip_distance);
            tripAvgSpeed = itemView.findViewById(R.id.trip_avg_speed);
        }
    }
} 