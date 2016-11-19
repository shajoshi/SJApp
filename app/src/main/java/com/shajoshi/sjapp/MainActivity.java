package com.shajoshi.sjapp;

import android.content.Intent;
import java.text.SimpleDateFormat;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.sql.Time;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback
{
    public final static String EXTRA_MESSAGE = "com.shajoshi.sjapp.MESSAGE";
    public final static String LOC_MESSAGE = "com.shajoshi.sjapp.LOCATION";
    public final static String tag = "MainActivity";
    //public static Location currentLocation = null;
    private GoogleMap mMap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(tag, "Start onCreate() event");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Spawn the location updater service
        Intent serviceIntent = new Intent(this, LocationUpdaterService.class);
        startService(serviceIntent);


        // Init Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.sjmap);
        mapFragment.getMapAsync(this);
        Log.d(tag, "End onCreate() event");
    }


    /** Called when the user clicks the Send button */
    public void setPollInterval()
    {
        // Do something in response to button
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
        TextView dispText = (TextView) findViewById(R.id.display_message);
        String message = LocationUpdaterService.getLocationText();
        dispText.setText(message);
        Log.d(tag, "refreshLocation() = " + message);
        setPollInterval();
    }



    public void onStart()
    {
        super.onStart();
        refreshLocation(null);
        Log.d(tag, "In the onStart() event");
    }
    public void onRestart()
    {
        super.onRestart();
        refreshLocation(null);
        Log.d(tag, "In the onRestart() event");
    }
    public void onResume()
    {
        super.onResume();
        refreshLocation(null);
        Log.d(tag, "In the onResume() event");
    }
    public void onPause()
    {
        super.onPause();
        Log.d(tag, "In the onPause() event");
    }
    public void onStop()
    {
        super.onStop();
        Log.d(tag, "In the onStop() event");
    }
    public void onDestroy()
    {
        super.onDestroy();
        Log.d(tag, "In the onDestroy() event");
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);

        Location loc = LocationUpdaterService.getLocation();
        if(loc != null)
        {
            // Add a marker in Sydney and move the camera
            LatLng current = new LatLng(loc.getLatitude(), loc.getLongitude());
            mMap.addMarker(new MarkerOptions().position(current).title("Current"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(current));
            this.refreshLocation(null);
        }
    }



}
