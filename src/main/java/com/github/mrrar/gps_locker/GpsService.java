package com.github.mrrar.gps_locker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import java.util.Iterator;

@SuppressLint("MissingPermission")
public class GpsService extends Service {
    public String notificationText = "";
    public String notificationTitle = "Locking GPS";
    final int NOTIFICATION_ID = 1;
    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
    static boolean hasUserGrantedPermission(String permissionName, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true;
        boolean granted = context.checkSelfPermission(permissionName)
                == PackageManager.PERMISSION_GRANTED;
        return granted;
    }
    public static boolean hasUserGrantedAllNecessaryPermissions(Context context) {
        boolean granted = hasUserGrantedPermission(Manifest.permission.ACCESS_COARSE_LOCATION, context)
                && hasUserGrantedPermission(Manifest.permission.ACCESS_FINE_LOCATION, context);

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            granted = granted && hasUserGrantedPermission(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION, context);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted = granted && hasUserGrantedPermission(
                    Manifest.permission.POST_NOTIFICATIONS, context);
        }

        return granted;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!hasUserGrantedAllNecessaryPermissions(this)) {
            Intent permissionsIntent = new Intent();
            permissionsIntent.setAction("PERMISSIONS_MISSING");
            sendBroadcast(permissionsIntent);
            stopSelf();
            return START_NOT_STICKY;
        }
        Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new MyLocationListener(this);
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, 0,
                locationListener);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            locationManager.addGpsStatusListener(locationListener);
        } else { // Android 11+
            locationManager.registerGnssStatusCallback(getMainExecutor(),
                    new GnssStatus.Callback() {
                @Override
                public void onStarted() {
                    super.onStarted();
                    locationListener.setTitle("GPS Enabled");
                }
                @Override
                public void onStopped() {
                    super.onStopped();
                    locationListener.setTitle("GPS Disabled");
                    locationListener.setText("");
                }
                @Override
                public void onFirstFix(int ttffMillis) {
                    super.onFirstFix(ttffMillis);
                    locationListener.setTitle("GPS Locked");
                }
                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    int satellitesVisible = status.getSatelliteCount();
                    int satellitesUsedInFix = 0;
                    for (int i = 0; i < satellitesVisible; i++) {
                        if (status.usedInFix(i)) {
                            satellitesUsedInFix++;
                        }
                    }

                    locationListener.setText("Satellites used: " +
                            satellitesUsedInFix + " of " + satellitesVisible);
                }
            });
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
    private boolean isShutdown = false;
    @Override
    public void onDestroy() {
        isShutdown = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && locationListener != null) {
            locationManager.removeGpsStatusListener(locationListener);
        }
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
            Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show();
        }
        //locationManager.unregisterGnssStatusCallback();
    }
    public void updateNotification() {
        // Android keeps calling locationListener even after running:
        // locationManager.removeGpsStatusListener(locationListener);
        // locationManager.removeUpdates(locationListener);
        // This only seems to happen after the app is first installed
        if (isShutdown)
            return;
        Notification notification = getNotification();
        NotificationManager notificationManager =
                (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
        Intent intent = new Intent();
        intent.setAction("GPS_STATUS");
        intent.putExtra("title", notificationTitle);
        intent.putExtra("text", notificationText);
        sendBroadcast(intent);
    }
    @Override
    public void onCreate() {
        Notification notification = getNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }
    Notification getNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("notificationID", NOTIFICATION_ID);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
            Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_MUTABLE);
        Notification.Builder notificationBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel =
                    new NotificationChannel("gps_lock", "GPS Lock",
                            NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setSound(null, null);
            NotificationManager notificationManager =
                    (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
            notificationBuilder = new Notification.Builder(this, "gps_lock");
        } else {
            notificationBuilder = new Notification.Builder(this);
        }
        return notificationBuilder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setContentTitle(notificationTitle)
                .setOngoing(true)
                .setContentText(notificationText)
                .setSound(null)
                .getNotification();
    }
    LocationManager locationManager;
    MyLocationListener locationListener;
}

@SuppressLint("MissingPermission")
class MyLocationListener implements LocationListener, GpsStatus.Listener {
    GpsService gpsService;
    public MyLocationListener(GpsService gpsService) {
        super();
        this.gpsService = gpsService;
    }
    public void setTitle(String s) {
        gpsService.notificationTitle = s;
        gpsService.updateNotification();
    }
    public void setText(String s) {
        gpsService.notificationText = s;
        gpsService.updateNotification();
    }

    // From interface: LocationListener
    public void onLocationChanged(Location loc) {
        if (loc != null) {
            //String message = "Location changed : Lat: " + loc.getLatitude() +
            //        " Lng: " + loc.getLongitude();
            setTitle("GPS Locked");
        }
    }
    public void onProviderDisabled(String provider) {
        setTitle(provider.toUpperCase() + " disabled");
    }
    public void onProviderEnabled(String provider) {
        setTitle(provider.toUpperCase() + " enabled");
    }
    public void onStatusChanged(String provider, int status, Bundle extras) {
        provider = provider.toUpperCase();
        if (status == LocationProvider.OUT_OF_SERVICE) {
            setTitle(provider + " is out of service");
        }

        if (status == LocationProvider.AVAILABLE) {
            setTitle(provider + " is available");
        }

        if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
            setTitle(provider + " is temporarily unavailable");
        }
    }

    // From interface: GpsStatus.Listener
    public void onGpsStatusChanged(int event) {

        switch (event) {
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                setText("First fix");
                break;

            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:

                GpsStatus status = gpsService.locationManager.getGpsStatus(null);

                int maxSatellites = status.getMaxSatellites();

                Iterator<GpsSatellite> it = status.getSatellites().iterator();
                int satellitesVisible = 0;
                int satellitesUsedInFix = 0;

                while (it.hasNext() && satellitesVisible <= maxSatellites) {
                    GpsSatellite sat = it.next();
                    if(sat.usedInFix()){
                        satellitesUsedInFix++;
                    }
                    satellitesVisible++;
                }
                setText("Satellites used: " +
                        satellitesUsedInFix + " of " + satellitesVisible);
                break;

            case GpsStatus.GPS_EVENT_STARTED:
                setText("");
                break;

            case GpsStatus.GPS_EVENT_STOPPED:
                setText("");
                break;

        }
    }
}
