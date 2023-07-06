package com.github.mrrar.gps_locker;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends Activity {
    Handler handler = new Handler();
    Runnable runnable = () -> onTimer();
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults ) {
        Boolean allGranted = true;
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                allGranted = false;
            }
            Boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            Log.d("debug", permissions[i] + ": " + granted);
        }
        if (allGranted) {
            Switch gpsSwitch = findViewById(R.id.lockGpsToggle);
            gpsSwitch.setChecked(true);
            gpsSwitchOnChange();
        }
    }
    boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (GpsService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter gpsIntentFilter = new IntentFilter();
        gpsIntentFilter.addAction("GPS_STATUS");
        registerReceiver(gpsStatusUpdateReceiver, gpsIntentFilter);

        IntentFilter permissionsIntentFilter = new IntentFilter();
        permissionsIntentFilter.addAction("PERMISSIONS_MISSING");
        registerReceiver(permissionsReceiver, permissionsIntentFilter);

        setContentView(R.layout.activity_main);
        gpsSwitchOnChange();

        Switch gpsSwitch = findViewById(R.id.lockGpsToggle);
        gpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                gpsSwitchOnChange();
            }
        });

        onTimer();
    }
    public void toggleGPS(View view) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(intent);
    }
    public void gpsSwitchOnChange() {
        Switch gpsSwitch = findViewById(R.id.lockGpsToggle);
        Intent serviceIntent = new Intent(this, GpsService.class);
        if (gpsSwitch.isChecked()) {
            if (!isServiceRunning())
                startService(serviceIntent);
        } else {
            stopService(serviceIntent);
        }
        TextView textViewText = findViewById(R.id.text);
        textViewText.setText("");
        TextView textViewTitle = findViewById(R.id.title);
        textViewTitle.setText("");
    }
    void askUserForPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        ArrayList<String> permissions = new ArrayList<String>();
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Only on Android 10 (Q),
            // the permission dialog can include an 'Allow all the time'
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        requestPermissions(permissions.toArray(new String[0]), 0);
    }
    private final BroadcastReceiver gpsStatusUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getExtras().getString("text");
            TextView textViewText = findViewById(R.id.text);
            textViewText.setText(text);

            String title = intent.getExtras().getString("title");
            TextView textViewTitle = findViewById(R.id.title);
            textViewTitle.setText(title);
        }
    };
    private final BroadcastReceiver permissionsReceiver =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            askUserForPermissions();
            Switch gpsSwitch = findViewById(R.id.lockGpsToggle);
            gpsSwitch.setChecked(false);
        }
    };
    public void onTimer() {
        Button button = findViewById(R.id.toggleGpsBtn);
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            button.setText("Disable GPS");
        } else {
            button.setText("Enable GPS");
        }
        handler.postAtTime(runnable, System.currentTimeMillis() + 1000);
        handler.postDelayed(runnable, 1000);
    }
}
