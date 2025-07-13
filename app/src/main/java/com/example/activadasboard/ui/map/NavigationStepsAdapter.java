package com.example.activadasboard.ui.map;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.activadasboard.R;
import com.google.maps.model.DirectionsStep;

import java.util.ArrayList;
import java.util.List;

public class NavigationStepsAdapter extends RecyclerView.Adapter<NavigationStepsAdapter.ViewHolder> {
    private List<DirectionsStep> steps = new ArrayList<>();
    private int currentStepIndex = -1;

    public void setSteps(List<DirectionsStep> steps) {
        this.steps = steps;
        notifyDataSetChanged();
    }

    public void clearSteps() {
        steps.clear();
        currentStepIndex = -1;
        notifyDataSetChanged();
    }

    public void setCurrentStep(int index) {
        int oldIndex = currentStepIndex;
        currentStepIndex = index;
        if (oldIndex >= 0) notifyItemChanged(oldIndex);
        if (currentStepIndex >= 0) notifyItemChanged(currentStepIndex);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_navigation_step, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DirectionsStep step = steps.get(position);
        holder.bind(step, position == currentStepIndex);
    }

    @Override
    public int getItemCount() {
        return steps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView instructionText;
        private final TextView distanceText;
        private final ImageView maneuverIcon;
        private final View itemContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            instructionText = itemView.findViewById(R.id.instructionText);
            distanceText = itemView.findViewById(R.id.distanceText);
            maneuverIcon = itemView.findViewById(R.id.maneuverIcon);
            itemContainer = itemView.findViewById(R.id.itemContainer);
        }

        public void bind(DirectionsStep step, boolean isCurrentStep) {
            instructionText.setText(Html.fromHtml(step.htmlInstructions, Html.FROM_HTML_MODE_LEGACY));
            distanceText.setText(step.distance.humanReadable);
            
            // Set the appropriate maneuver icon
            int iconResource = getManeuverIcon(step.maneuver);
            if (iconResource != 0) {
                maneuverIcon.setImageResource(iconResource);
                maneuverIcon.setVisibility(View.VISIBLE);
            } else {
                maneuverIcon.setVisibility(View.GONE);
            }

            // Highlight current step
            if (isCurrentStep) {
                itemContainer.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.primary_light));
                instructionText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.primary));
            } else {
                itemContainer.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), android.R.color.transparent));
                instructionText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_primary_dark));
            }
        }

        private int getManeuverIcon(String maneuver) {
            if (maneuver == null) return R.drawable.ic_straight;

            switch (maneuver) {
                case "turn-right":
                    return R.drawable.ic_turn_right;
                case "turn-left":
                    return R.drawable.ic_turn_left;
                case "roundabout-right":
                case "roundabout-left":
                    return R.drawable.ic_roundabout;
                case "fork-right":
                case "fork-left":
                    return R.drawable.ic_fork;
                case "merge":
                    return R.drawable.ic_merge;
                case "uturn-right":
                case "uturn-left":
                    return R.drawable.ic_uturn;
                default:
                    return R.drawable.ic_straight;
            }
        }
    }
} 