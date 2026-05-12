package com.powersentinel.app.system;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.location.LocationManager;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;

/**
 * Background worker that periodically samples device sensor states and battery metrics.
 * Builds a lightweight 24-hour histogram in SharedPreferences to feed the AIPlanGenerator.
 */
public class AIAgentWorker extends Worker {

    private static final String PREFS_NAME = "ai_agent_data";
    // Keys for each hour slot: wifi_on_h0 ... wifi_on_h23, bt_on_h0 ... bt_on_h23, gps_on_h0 ... gps_on_h23
    // Values: number of times the sensor was found ON at that hour

    public AIAgentWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        // Sample Wi-Fi state
        boolean wifiOn = false;
        try {
            WifiManager wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            wifiOn = wifiManager != null && wifiManager.isWifiEnabled();
        } catch (SecurityException ignored) { }

        // Sample Bluetooth state
        boolean btOn = false;
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            btOn = btAdapter != null && btAdapter.isEnabled();
        } catch (SecurityException ignored) { }

        // Sample Location state
        boolean gpsOn = false;
        try {
            LocationManager locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            gpsOn = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (SecurityException ignored) { }

        // Increment sampling counters
        incrementCounter(editor, prefs, "wifi_on_h" + hour, wifiOn);
        incrementCounter(editor, prefs, "bt_on_h" + hour, btOn);
        incrementCounter(editor, prefs, "gps_on_h" + hour, gpsOn);
        incrementCounter(editor, prefs, "samples_h" + hour, true);

        // Store battery level
        BatteryProbe batteryProbe = new BatteryProbe(ctx);
        com.powersentinel.app.model.BatterySnapshot snapshot = batteryProbe.read();
        editor.putInt("last_battery_level", snapshot.levelPercent);
        editor.putInt("last_battery_temp", snapshot.temperatureDeciCelsius);
        editor.putBoolean("last_charging", snapshot.charging);

        editor.apply();
        return Result.success();
    }

    private void incrementCounter(SharedPreferences.Editor editor, SharedPreferences prefs, String key, boolean condition) {
        if (condition) {
            int current = prefs.getInt(key, 0);
            editor.putInt(key, current + 1);
        }
    }
}
