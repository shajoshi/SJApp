package com.shajoshi.sjapp;

import android.util.Log;

public class LocationUtils {
    
    // This method directly updates the POLL_INTERVAL in LocationUpdaterService
    // and returns the 'seconds' value that was effectively set.
    // It contains the core logic previously in LocationUpdaterService.setDelay().
    public static int setServicePollInterval(int seconds) {
        long newIntervalMillis;
        if (seconds <= 0) {
            newIntervalMillis = 1 * 1000; // 1 second
        } else if (seconds >= 120) {
            newIntervalMillis = 120 * 1000; // 120 seconds
        } else {
            newIntervalMillis = seconds * 1000;
        }
        
        LocationUpdaterService.POLL_INTERVAL = newIntervalMillis; // Update the static field
        
        int effectiveSeconds = (int) (newIntervalMillis / 1000);
        // Using LocationUpdaterService.tag for consistency if this log is desired.
        // Ensure LocationUpdaterService.tag is public static final if used here, or pass tag.
        // For now, assuming it's accessible or this log can be adjusted.
        // Log.i(LocationUpdaterService.tag, "LocationUpdaterService.POLL_INTERVAL set to " + effectiveSeconds + " sec (" + LocationUpdaterService.POLL_INTERVAL + " ms)");
        return effectiveSeconds;
    }
}
