package geodoor.tapsi.logic.service;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import geodoor.tapsi.MainActivity;
import geodoor.tapsi.logic.Constants;

public class GPSService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    // Google GPS API
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient;

    // Servicebinder stuff
    private final IBinder binder = new MyLocalBinder();

    Location homeLocation;
    float radius;

    private final SimpleDateFormat POINT_DATE_FORMATTER = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());

    private String TAG = "tapsi.logic.service.GPSService";
    private GPXFileWriter gpxFileWriter;
    private File file;

    public GPSService() {
        homeLocation = new Location("tapsi");
        homeLocation.setLatitude(47.04765827);
        homeLocation.setLongitude(15.07451153);
        homeLocation.setAltitude(474);
        radius = 200;

        if (isExternalStorageWritable())
            Log.d(TAG, "externalStorageIsWritable");
        else
            return;

        file = new File(getPrivateAlbumStorageDir("Files"), POINT_DATE_FORMATTER.format(new Date(System.currentTimeMillis())));
        gpxFileWriter = new GPXFileWriter(file);
        gpxFileWriter.openGpxFile();
    }

    public File getPrivateAlbumStorageDir(String albumName) {
        // Get the directory for the app's private pictures directory.
        File file = new File(MainActivity.getStaticActivity().getExternalFilesDir(
                Environment.DIRECTORY_DOCUMENTS), albumName);
        if (!file.mkdirs()) {
            Log.e(TAG, "Directory not created");
        }
        Log.e(TAG, "Directory:" + file.getAbsolutePath());
        return file;
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (intent == null) {
        } else if (intent.getAction().equals(Constants.ACTION.GPS_START)) {
            if (getAPIClient() == null) {
                buildGoogleApiClient();
            } else {
                startGPS();
            }
        } else if (intent.getAction().equals(Constants.ACTION.GPS_STOP)) {
            stopGPS();
            stopSelf();
            gpxFileWriter.closeGPXFile();
            Log.d(TAG, Constants.ACTION.GPS_STOP);
        }
        return Service.START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Binder stuff to get the parent class (the actual service class)
    class MyLocalBinder extends Binder {
        GPSService getService() {
            return GPSService.this;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged");
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
        Log.d(TAG, "onStatusChanged");
    }

    @Override
    public void onProviderEnabled(String s) {
        Log.d(TAG, "onProviderEnabled");
    }

    @Override
    public void onProviderDisabled(String s) {
        Log.d(TAG, "onProviderDisabled");
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(300);
        mLocationRequest.setFastestInterval(300);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        startGPS();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    public GoogleApiClient getAPIClient() {
        return mGoogleApiClient;
    }

    // Gps Google API
    public synchronized void buildGoogleApiClient() {
        Log.d(TAG, "buildGoogleApiClient");
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    public void startGPS() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        }
    }

    public void stopGPS() {
        //stop location updates
        if (mGoogleApiClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                mLastLocation = location;
                float distance = mLastLocation.distanceTo(homeLocation);
                GPXFileWriter.ActivityData activityData = new GPXFileWriter.ActivityData(System.currentTimeMillis(), location);
                gpxFileWriter.writeTrackPoints(activityData);

                ArrayList<String> list = new ArrayList<String>();
                list.add(getStringValue(distance, 0));
                list.add(getStringValue(location.getSpeed(), 1));
                list.add(getStringValue(location.getAccuracy(), 0));

                sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_LOCATIONUPDATE, list);
                Log.d(TAG, "NAME_LOCATIONUPDATE:");
                Log.d(TAG, "distance: " + distance);
                Log.d(TAG, "Speed: " + location.getSpeed());
                Log.d(TAG, "Accuracy: " + location.getAccuracy());
                if (distance < radius) {
                    if (location.getAccuracy() <= 20.00) {
                        sendOutBroadcast(Constants.BROADCAST.EVENT_TOSOCKET, Constants.BROADCAST.NAME_OPENGATE, "true");
                        sendOutBroadcast(Constants.BROADCAST.EVENT_TOMAIN, Constants.BROADCAST.NAME_OPENGATE, "true");
                        Log.d(TAG, "NAME_OPENGATE");
                    }
                }
            }
        }
    };

    // Format the values given to the Activity
    private String getStringValue(float number, int mode) {
        // Mode 0: get formated value in m or km
        // Mode 1: get formated value in km/h
        if (mode == 1)
            number *= 3.6;

        boolean km = false;
        if (number > 999.99) {
            number = number / 1000;
            km = true;
        }

        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.CEILING);

        String str_num = String.valueOf(df.format(number));

        switch (mode) {
            case 0:
                if (km)
                    str_num += " km";
                else
                    str_num += " m";
                break;
            case 1:
                str_num += " km/h";

        }
        return str_num;
    }

    public void sendOutBroadcast(String event, String name, String value) {
        Intent intent = new Intent(event);
        intent.putExtra(name, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void sendOutBroadcast(String event, String name, ArrayList<String> value) {
        Intent intent = new Intent(event);
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(name, value);
        intent.putExtras(bundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}