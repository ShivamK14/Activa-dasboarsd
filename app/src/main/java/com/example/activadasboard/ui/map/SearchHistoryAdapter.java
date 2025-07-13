package com.example.activadasboard.ui.map;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.activadasboard.R;
import com.example.activadasboard.data.SearchHistory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {
    private List<SearchHistory> searchHistoryList = new ArrayList<>();
    private OnSearchHistoryClickListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
    
    public interface OnSearchHistoryClickListener {
        void onSearchHistoryClick(SearchHistory searchHistory);
        void onSearchHistoryLongClick(SearchHistory searchHistory);
    }
    
    public void setOnSearchHistoryClickListener(OnSearchHistoryClickListener listener) {
        this.listener = listener;
    }
    
    public void setSearchHistory(List<SearchHistory> searchHistoryList) {
        this.searchHistoryList = searchHistoryList != null ? searchHistoryList : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_history, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchHistory searchHistory = searchHistoryList.get(position);
        holder.bind(searchHistory);
    }
    
    @Override
    public int getItemCount() {
        return searchHistoryList.size();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView placeNameText;
        private TextView addressText;
        private TextView timestampText;
        private TextView searchCountText;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            placeNameText = itemView.findViewById(R.id.place_name);
            addressText = itemView.findViewById(R.id.address);
            timestampText = itemView.findViewById(R.id.timestamp);
            searchCountText = itemView.findViewById(R.id.search_count);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSearchHistoryClick(searchHistoryList.get(position));
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSearchHistoryLongClick(searchHistoryList.get(position));
                    return true;
                }
                return false;
            });
        }
        
        void bind(SearchHistory searchHistory) {
            placeNameText.setText(searchHistory.placeName);
            addressText.setText(searchHistory.address);
            timestampText.setText(dateFormat.format(new Date(searchHistory.timestamp)));
            searchCountText.setText(String.format(Locale.getDefault(), 
                "%d time%s", searchHistory.searchCount, 
                searchHistory.searchCount == 1 ? "" : "s"));
        }
    }
} 