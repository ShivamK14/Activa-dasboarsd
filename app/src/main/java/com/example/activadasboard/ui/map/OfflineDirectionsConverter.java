package com.example.activadasboard.ui.map;

import com.example.activadasboard.data.OfflineDirections;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsStep;
import com.google.maps.model.Distance;
import com.google.maps.model.Duration;
import com.google.maps.model.EncodedPolyline;
import com.google.maps.model.TravelMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class OfflineDirectionsConverter {
    
    public static DirectionsResult convertToDirectionsResult(OfflineDirections offlineDirections) {
        try {
            // Create the route
            DirectionsRoute route = new DirectionsRoute();
            
            // Create the leg
            DirectionsLeg leg = new DirectionsLeg();
            leg.distance = new Distance();
            leg.distance.inMeters = offlineDirections.distanceMeters;
            leg.distance.humanReadable = formatDistance(offlineDirections.distanceMeters);
            
            leg.duration = new Duration();
            leg.duration.inSeconds = offlineDirections.durationSeconds;
            leg.duration.humanReadable = formatDuration(offlineDirections.durationSeconds);
            
            // Reconstruct detailed steps from JSON
            DirectionsStep[] steps = reconstructStepsFromJson(offlineDirections.stepsJson, offlineDirections);
            if (steps != null && steps.length > 0) {
                leg.steps = steps;
            } else {
                // Fallback to simple step if JSON parsing fails
                DirectionsStep step = new DirectionsStep();
                step.distance = leg.distance;
                step.duration = leg.duration;
                step.htmlInstructions = "Follow the route to " + offlineDirections.destinationName;
                step.travelMode = TravelMode.DRIVING;
                
                // Set start and end locations
                step.startLocation = new com.google.maps.model.LatLng(
                    offlineDirections.originLatitude, offlineDirections.originLongitude);
                step.endLocation = new com.google.maps.model.LatLng(
                    offlineDirections.destinationLatitude, offlineDirections.destinationLongitude);
                
                leg.steps = new DirectionsStep[]{step};
            }
            
            // Set the polyline
            route.overviewPolyline = new EncodedPolyline(offlineDirections.routePolyline);
            route.summary = offlineDirections.routeSummary;
            route.legs = new DirectionsLeg[]{leg};
            
            // Create the result
            DirectionsResult result = new DirectionsResult();
            result.routes = new DirectionsRoute[]{route};
            
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private static String formatDistance(long meters) {
        if (meters < 1000) {
            return meters + " m";
        } else {
            double km = meters / 1000.0;
            return String.format("%.1f km", km);
        }
    }
    
    private static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        
        if (hours > 0) {
            return String.format("%d hr %d min", hours, minutes);
        } else {
            return String.format("%d min", minutes);
        }
    }
    
    public static List<LatLng> decodePolyline(String encodedPolyline) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encodedPolyline.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encodedPolyline.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encodedPolyline.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }
    
    private static DirectionsStep[] reconstructStepsFromJson(String stepsJson, OfflineDirections offlineDirections) {
        try {
            if (stepsJson == null || stepsJson.isEmpty() || stepsJson.equals("[]")) {
                return null;
            }
            
            JSONArray stepsArray = new JSONArray(stepsJson);
            DirectionsStep[] steps = new DirectionsStep[stepsArray.length()];
            
            for (int i = 0; i < stepsArray.length(); i++) {
                JSONObject stepObj = stepsArray.getJSONObject(i);
                DirectionsStep step = new DirectionsStep();
                
                // Set basic properties
                step.htmlInstructions = stepObj.optString("htmlInstructions", "");
                step.travelMode = TravelMode.DRIVING;
                
                // Set distance
                step.distance = new Distance();
                step.distance.humanReadable = stepObj.optString("distance", "");
                
                // Set duration
                step.duration = new Duration();
                step.duration.humanReadable = stepObj.optString("duration", "");
                
                // Set maneuver if available
                if (stepObj.has("maneuver")) {
                    try {
                        step.maneuver = stepObj.getString("maneuver");
                    } catch (Exception e) {
                        // Ignore maneuver parsing errors
                    }
                }
                
                // Set locations if available
                if (stepObj.has("startLat") && stepObj.has("startLng")) {
                    step.startLocation = new com.google.maps.model.LatLng(
                        stepObj.getDouble("startLat"), stepObj.getDouble("startLng"));
                }
                if (stepObj.has("endLat") && stepObj.has("endLng")) {
                    step.endLocation = new com.google.maps.model.LatLng(
                        stepObj.getDouble("endLat"), stepObj.getDouble("endLng"));
                }
                
                steps[i] = step;
            }
            
            return steps;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
} 