package com.example.activadasboard.ui.history;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class HistoryPagerAdapter extends FragmentStateAdapter {
    private static final int NUM_PAGES = 3;

    public HistoryPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new HistoryListFragment();
            case 1:
                return new HistoryChartsFragment();
            case 2:
                return new HistorySummaryFragment();
            default:
                throw new IllegalArgumentException("Invalid position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return NUM_PAGES;
    }
} 