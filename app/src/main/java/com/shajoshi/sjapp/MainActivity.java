package com.shajoshi.sjapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {
    public final static String EXTRA_MESSAGE = "com.shajoshi.sjapp.MESSAGE";
    public final static String LOC_MESSAGE = "com.shajoshi.sjapp.LOCATION";
    public final static String tag = "MainActivity";
    private GoogleMap mMap = null;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(tag, "Start onCreate() event");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(tag, "Location permission already granted.");
            startLocationDependentServices();
        } else {
            Log.d(tag, "Location permission not granted. Requesting permission.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.sjmap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e(tag, "MapFragment is null. Check layout file.");
        }
        Log.d(tag, "End onCreate() event");
    }

    private void startLocationDependentServices() {
        Log.d(tag, "Starting location-dependent services.");
        Intent serviceIntent = new Intent(this, LocationUpdaterService.class);
        ContextCompat.startForegroundService(this, serviceIntent); // Changed to startForegroundService
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(tag, "Location permission granted by user.");
                startLocationDependentServices();
                if (mMap != null) {
                    try {
                        mMap.setMyLocationEnabled(true);
                        updateMapLocation();
                    } catch (SecurityException e) {
                        Log.e(tag, "SecurityException in onRequestPermissionsResult after permission grant: " + e.getMessage());
                    }
                }
            } else {
                Log.d(tag, "Location permission denied by user.");
                Toast.makeText(this, "Location permission is required for core functionality.", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void setPollInterval()
    {
        EditText editText = (EditText) findViewById(R.id.poll_interval);
        String val = editText.getText().toString();
        if (val.trim().length() == 0)
            return;
        try
        {
            int delay = Integer.parseInt(val);
            delay = LocationUpdaterService.setDelay(delay);
            editText.setText(new Integer(delay).toString());
        }
        catch(Throwable t)
        {
            Log.e(tag, "Exception in setPollInterval()", t);
        }
    }

    public void refreshLocation(View view)
    {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            TextView dispText = (TextView) findViewById(R.id.display_message);
            String message = LocationUpdaterService.getLocationText();
            dispText.setText(message);
            Log.d(tag, "refreshLocation() = " + message);
        } else {
            TextView dispText = (TextView) findViewById(R.id.display_message);
            dispText.setText("Location permission denied. Cannot refresh location.");
            Log.d(tag, "refreshLocation() - permission denied.");
        }
        setPollInterval(); 
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshLocation(null); 
        Log.d(tag, "In the onStart() event");
    }

    @Override
    public void onRestart() {
        super.onRestart();
        refreshLocation(null);
        Log.d(tag, "In the onRestart() event");
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLocation(null);
        Log.d(tag, "In the onResume() event");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(tag, "In the onPause() event");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(tag, "In the onStop() event");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(tag, "In the onDestroy() event");
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        Log.d(tag, "Map is ready.");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                mMap.setMyLocationEnabled(true);
                Log.d(tag, "MyLocation layer enabled on map.");
            } catch (SecurityException e) {
                 Log.e(tag, "SecurityException onMapReady: " + e.getMessage());
            }
            updateMapLocation(); 
        } else {
            Log.d(tag, "Location permission not granted. MyLocation layer not enabled.");
        }
    }

    private void updateMapLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return; 
        }
        Location loc = LocationUpdaterService.getLocation(); 
        if (loc != null) {
            Log.d(tag, "Updating map to location: " + loc.getLatitude() + "," + loc.getLongitude());
            LatLng current = new LatLng(loc.getLatitude(), loc.getLongitude());
            mMap.addMarker(new MarkerOptions().position(current).title("Current Location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(current, 15f)); 
        } else {
            Log.d(tag, "Location for map update is null.");
        }
        TextView dispText = (TextView) findViewById(R.id.display_message);
        String message = LocationUpdaterService.getLocationText();
        dispText.setText(message);
    }
}
