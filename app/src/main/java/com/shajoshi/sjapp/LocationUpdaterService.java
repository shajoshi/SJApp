package com.shajoshi.sjapp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri; 
import android.os.Build;
import android.os.Bundle;
import android.os.Environment; 
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore; 
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.OutputStream; 
import java.io.IOException; 
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LocationUpdaterService extends Service
{
    public static final String tag = "LocationUpdaterService";
    // Changed to package-private for testability
    static long POLL_INTERVAL = 12000; 
    public static Boolean isRunning = false; 

    private static Location previousBestLocation = null;

    private static final String CHANNEL_ID = "LocationServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    // Lazy initialization for Handler and Runnable
    private Handler mHandler = null;
    private Runnable mHandlerTask = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.d(tag, "Start onCreate()");
        createNotificationChannel();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();
        
        Log.d(tag, "End onCreate()");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Service")
                .setContentText("Tracking your location in the background")
                .setSmallIcon(R.mipmap.ic_launcher) 
                .setContentIntent(pendingIntent)
                .build();
    }
    
    private void createLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, POLL_INTERVAL)
            .setMinUpdateIntervalMillis(POLL_INTERVAL / 2)
            .build();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        Log.d(tag, "New location received: " + location.getLatitude() + ", " + location.getLongitude());
                        if (isBetterLocation(location, previousBestLocation)) {
                            previousBestLocation = location;
                            String text = getLocationText(); 
                            Log.i(tag, "New Best Location (Fused): " + text);
                            writeToFileStream(text.getBytes());
                            stopListening(); 
                        }
                    }
                }
            }
        };
    }
    
    private void ensureHandlerInitialized() {
        if (mHandler == null) {
            // Check if Looper has been prepared for this thread, common issue in tests
            if (Looper.myLooper() == null) {
                Looper.prepare(); // Prepare looper for the current thread if not already prepared
                                  // This is a common workaround for tests, but consider implications.
                                  // For a service, Handler should ideally be on MainLooper or a dedicated HandlerThread.
                                  // The previous code used Looper.getMainLooper(), which is better.
            }
            mHandler = new Handler(Looper.getMainLooper()); // Reverted to ensure it uses MainLooper
            mHandlerTask = new Runnable() {
                @Override
                public void run() {
                    if (!isRunning) {
                        startListening();
                    }
                    if (mHandler != null) { // Check mHandler again as it could be cleared in onDestroy
                        mHandler.postDelayed(this, POLL_INTERVAL);
                    }
                }
            };
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(tag, "Start onStartCommand()");
        ensureHandlerInitialized(); // Initialize Handler here
        startForeground(NOTIFICATION_ID, buildNotification());
        if (mHandler != null && mHandlerTask != null) { // Null check for safety
            mHandler.removeCallbacks(mHandlerTask); // Remove existing callbacks before posting new one
            mHandler.post(mHandlerTask); 
        }
        Log.d(tag, "End onStartCommand()");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(tag, "Start onDestroy()");
        stopListening(); 
        if (mHandler != null && mHandlerTask != null) { // Null check
            mHandler.removeCallbacks(mHandlerTask); 
            mHandler = null; // Release handler
            mHandlerTask = null;
        }
        super.onDestroy();
        Log.d(tag, "End onDestroy()");
    }

    private void startListening() {
        Log.d(tag, "Attempting to start listening for location updates.");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
        {
            try {
                createLocationRequest(); // Ensure locationRequest is up-to-date with POLL_INTERVAL
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
                isRunning = true; 
                Log.d(tag, "Requested location updates via FusedLocationProviderClient.");
            } catch (SecurityException e) {
                Log.e(tag, "SecurityException while requesting location updates: " + e.getMessage());
                isRunning = false; 
            }
        }
        else
        {
            Log.i(tag, "ACCESS_FINE_LOCATION permission not granted. Cannot start listening.");
            isRunning = false; 
        }
    }

    private void stopListening()
    {
        Log.d(tag, "Attempting to stop listening for location updates.");
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            isRunning = false; 
            Log.d(tag, "Stopped location updates from FusedLocationProviderClient.");
        }
    }

    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) { return true; }
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > POLL_INTERVAL; 
        boolean isSignificantlyOlder = timeDelta < -POLL_INTERVAL;
        boolean isNewer = timeDelta > 0;
        if (isSignificantlyNewer) { return true; } 
        else if (isSignificantlyOlder) { return false; }
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;
        boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());
        if (isMoreAccurate) { return true; } 
        else if (isNewer && !isLessAccurate) { return true; } 
        else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) { return true; }
        return false;
    }

    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) { return provider2 == null; }
        return provider1.equals(provider2);
    }
    
    public static int setDelay(int sec) {
        if(sec <= 0) sec = 1;
        if(sec >=120) sec = 120;
        POLL_INTERVAL = sec * 1000;
        Log.i(tag, "POLL_INTERVAL set to \"" + sec + "\" sec");
        return sec;
    }

    public static Location getLocation() {
        return previousBestLocation;
    }

    private final String saveFileName = "sjapp.txt"; 

    void writeToFileStream(byte[] line) {
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, saveFileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");

        Uri collectionUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            String downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
            contentValues.put(MediaStore.MediaColumns.DATA, downloadsPath + "/" + saveFileName);
            collectionUri = MediaStore.Files.getContentUri("external");
        }
        
        Uri uri = null;
        try {
            uri = resolver.insert(collectionUri, contentValues);
            if (uri == null) {
                Log.e(tag, "Failed to create new MediaStore record.");
                return;
            }
            try (OutputStream fos = resolver.openOutputStream(uri)) {
                if (fos != null) {
                    fos.write(line);
                    fos.write("\n".getBytes()); 
                    Log.d(tag, "Writing to MediaStore URI: " + uri.toString());
                } else {
                     Log.e(tag, "Failed to open output stream for MediaStore URI: " + uri.toString());
                }
            }
        } catch (Exception e) { 
            Log.e(tag, "Error writing to MediaStore. URI: " + (uri != null ? uri.toString() : "null (insert failed)"), e);
        }
    }

    public static String getLocationText() {
        Location loc = previousBestLocation;
        if(loc == null) { return "Location not found.."; }
        StringBuffer buf = new StringBuffer(256);
        long t = loc.getTime();
        Date date = new Date(t);
        SimpleDateFormat df = new SimpleDateFormat("dd-MMM-YYYY HH:mm:ss", Locale.getDefault());
        String formatted = df.format(date);
        buf.append("Time: ").append(formatted).append("\n");
        buf.append("Location Provider: ").append(loc.getProvider()).append("\n"); 
        buf.append("Latitude: ").append(loc.getLatitude()).append("\n");
        buf.append("Longitude: ").append(loc.getLongitude()).append("\n");
        if(loc.hasAccuracy()) { buf.append("Accuracy: ").append(loc.getAccuracy()).append(" m\n"); }
        if(loc.hasSpeed()) { buf.append("Speed: ").append(loc.getSpeed()).append(" m/s\n"); }
        if(loc.hasAltitude()) { buf.append("Altitude: ").append(loc.getAltitude()).append(" m\n"); }
        return buf.toString();
    }
}
