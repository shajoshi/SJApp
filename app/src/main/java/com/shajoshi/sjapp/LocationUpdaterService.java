package com.shajoshi.sjapp;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.location.LocationListener;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Sample Code from http://codereview.stackexchange.com/questions/123933/android-studio-background-service-to-update-location
 *
 */

public class LocationUpdaterService extends Service
{
    public static final String tag = "LocationUpdaterService";
    private static int POLL_INTERVAL = 12000; // 12 seconds by default
    public static Boolean isRunning = false;

    public LocationManager mLocationManager;
    public LocationUpdaterListener mLocationListener;
    private static Location previousBestLocation = null;


    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onCreate()
    {
        Log.d(tag, "Start onCreate()");
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = new LocationUpdaterListener();
        super.onCreate();
        Log.d(tag, "End onCreate()");
    }

    Handler mHandler = new Handler();
    Runnable mHandlerTask = new Runnable()
    {
        @Override
        public void run()
        {
            if (!isRunning)
            {
                startListening();
            }
            mHandler.postDelayed(mHandlerTask, POLL_INTERVAL);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(tag, "Start onStartCommand()");
        mHandlerTask.run();
        Log.d(tag, "End onStartCommand()");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(tag, "Start onDestroy()");
        stopListening();
        mHandler.removeCallbacks(mHandlerTask);
        try
        {
            if (fos != null)
                fos.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        super.onDestroy();
        Log.d(tag, "End onDestroy()");
    }

    private void startListening() {
        Log.d(tag, "Start startListening()");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED)
        {
            if (mLocationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER))
            {
                Log.d(tag, "Using GPS_PROVIDER for location");
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, mLocationListener);
            }
            if (mLocationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER))
            {
                Log.d(tag, "Using NETWORK_PROVIDER for location");
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 5, mLocationListener);
            }
        }
        else
        {
            Log.i(tag, "App does not have permissions to access device location");
        }
        isRunning = true;
        Log.d(tag, "End startListening()");
    }

    private void stopListening()
    {
        Log.d(tag, "Start stopListening()");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
        {
            mLocationManager.removeUpdates(mLocationListener);
        }
        isRunning = false;
        Log.d(tag, "End stopListening()");
    }

    public class LocationUpdaterListener implements LocationListener
    {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(tag, "Start LocationUpdaterListener.onLocationChanged()");
            if (isBetterLocation(location, previousBestLocation))
            {
                try
                {
                    // Script to post location data to server
                    previousBestLocation = location;
                    //Write it to file
                    String text = getLocationText();
                    Log.i(tag, "Detected New Location: " + text);
                    writeToFileStream(text.getBytes());
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    stopListening();
                }
            }
            Log.d(tag, "End LocationUpdaterListener.onLocationChanged()");
        }

        @Override
        public void onProviderDisabled(String provider) {
            stopListening();
        }

        @Override
        public void onProviderEnabled(String provider) { }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }
    }

    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null)
        {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > POLL_INTERVAL;
        boolean isSignificantlyOlder = timeDelta < -POLL_INTERVAL;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer)
        {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder)
        {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }


    /** Public method to set delay between location checks
     *  Validate that delay between 1 sec and 120 sec only
     *  @return the actual delay set by this method
     */

    public static int setDelay(int sec)
    {
        if(sec <= 0)
            sec = 1;

        if(sec >=120)
            sec = 120;

        POLL_INTERVAL = sec * 1000; //convert to millisecond
        Log.i(tag, "POLL_INTERVAL set to \"" + sec + "\" sec");
        return sec;
    }

    public static Location getLocation()
    {
        return previousBestLocation;
    }

    //Name of file to store the locations
    private final String saveFileName = "sjapp.txt";
    private File f = null;
    private FileOutputStream fos = null;

    void writeToFileStream(byte[] line)
    {
        //check if file exists or create it
        try
        {
            if(f == null)
            {
                f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), saveFileName);
            }
            if (f.exists())
            {
                StringBuffer buf = new StringBuffer(128).append("File ").
                        append(saveFileName).append(" already exists at ").
                        append(f.getAbsolutePath());
                Log.d(tag, buf.toString());
            }

            if((fos == null) || !(fos.getFD().valid()))
            {
                fos = new FileOutputStream(f, true);  //open for append
            }
            fos.write(line);
            Log.d(tag, "Writing to file " + f.getAbsolutePath());
            fos.flush();
        }
        catch(Exception e)
        {
            Log.e(tag, "Error getting File " + saveFileName, e);
        }
    }

    public static String getLocationText()
    {
        Location loc = previousBestLocation;
        if(loc == null)
        {
            return "Location not found..";
        }

        StringBuffer buf = new StringBuffer(256);
        long t = loc.getTime();
        // New date object from millis
        Date date = new Date(t);
        // formattter
        SimpleDateFormat df = new SimpleDateFormat("dd-MMM-YYYY HH:mm:ss");
        // Pass date object
        String formatted = df.format(date);

        buf.append("Time: ").append(formatted).append("\n");
        buf.append("Location Provider: ").append(loc.getProvider()).append("\n");
        buf.append("Latitude: ").append(loc.getLatitude());
        buf.append("\n");
        buf.append("Longitude: ").append(loc.getLongitude());
        buf.append("\n");

        if(loc.hasAccuracy())
        {
            buf.append("Accuracy: ").append(loc.getAccuracy()).append(" m\n");
        }
        if(loc.hasSpeed())
        {
            buf.append("Speed: ").append(loc.getSpeed()).append(" m/s\n");
        }
        if(loc.hasAltitude())
        {
            buf.append("Altitude: ").append(loc.getAltitude()).append(" m\n");
        }

        //buf.append(getGeocodeDetails(loc));
        /**
        if(buf.length() > 256)
            buf.setLength(256);
        */
        return buf.toString();
    }
    /**
    private String getGeocodeDetails(Location loc)
    {
        StringBuffer addr = new StringBuffer(256);

        Geocoder gcd = new Geocoder(getBaseContext(), Locale.getDefault());
        List<Address> addresses;
        try
        {
            addresses = gcd.getFromLocation(loc.getLatitude(),
                    loc.getLongitude(), 1);
            for(int i = 0; i < addresses.size(); ++i)
            {
                addr.append(addresses.get(i).toString()).append("\n");
            }
        }
        catch (Exception e)
        {
            Log.getStackTraceString(e);
        }
        return addr.toString();
    }
     */


}
