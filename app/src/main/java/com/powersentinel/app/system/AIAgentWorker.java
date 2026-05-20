package com.powersentinel.app.system;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Background worker that periodically samples device sensor states and battery metrics.
 * Builds a lightweight 24-hour histogram in SharedPreferences to feed the AIPlanGenerator.
 */
public class AIAgentWorker extends Worker {

    private static final String PREFS_NAME = "ai_agent_data";
    private static final String WORK_NAME = "power_sentinel_learning";
    private static final long MIN_DRAIN_INTERVAL_MS = 30L * 60L * 1000L;
    private static final long MAX_DRAIN_INTERVAL_MS = 8L * 60L * 60L * 1000L;
    // Keys for each hour slot: wifi_on_h0 ... wifi_on_h23, bt_on_h0 ... bt_on_h23, gps_on_h0 ... gps_on_h23
    // Values: number of times the sensor was found ON at that hour

    public AIAgentWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AIAgentWorker.class,
                1,
                TimeUnit.HOURS
        ).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
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

        boolean mobileDataActive = isCellularNetworkActive(ctx);
        boolean interactive = isInteractive(ctx);

        // Increment sampling counters
        incrementCounter(editor, prefs, "wifi_on_h" + hour, wifiOn);
        incrementCounter(editor, prefs, "bt_on_h" + hour, btOn);
        incrementCounter(editor, prefs, "gps_on_h" + hour, gpsOn);
        incrementCounter(editor, prefs, "mobile_on_h" + hour, mobileDataActive);
        incrementCounter(editor, prefs, "samples_h" + hour, true);

        // Store battery level
        BatteryProbe batteryProbe = new BatteryProbe(ctx);
        com.powersentinel.app.model.BatterySnapshot snapshot = batteryProbe.read();
        updateDrainModel(editor, prefs, snapshot, wifiOn, btOn, gpsOn, mobileDataActive, interactive);
        editor.putInt("last_battery_level", snapshot.levelPercent);
        editor.putInt("last_battery_temp", snapshot.temperatureDeciCelsius);
        editor.putBoolean("last_charging", snapshot.charging);
        editor.putLong("last_sample_time_ms", System.currentTimeMillis());
        editor.putBoolean("last_interactive", interactive);
        editor.putBoolean("last_wifi_on", wifiOn);
        editor.putBoolean("last_bt_on", btOn);
        editor.putBoolean("last_gps_on", gpsOn);
        editor.putBoolean("last_mobile_on", mobileDataActive);

        editor.apply();
        return Result.success();
    }

    private void incrementCounter(SharedPreferences.Editor editor, SharedPreferences prefs, String key, boolean condition) {
        if (condition) {
            int current = prefs.getInt(key, 0);
            editor.putInt(key, current + 1);
        }
    }

    private void updateDrainModel(
            SharedPreferences.Editor editor,
            SharedPreferences prefs,
            com.powersentinel.app.model.BatterySnapshot snapshot,
            boolean wifiOn,
            boolean btOn,
            boolean gpsOn,
            boolean mobileDataActive,
            boolean interactive
    ) {
        long now = System.currentTimeMillis();
        long lastTime = prefs.getLong("last_sample_time_ms", 0L);
        int lastLevel = prefs.getInt("last_battery_level", -1);
        boolean lastCharging = prefs.getBoolean("last_charging", true);
        boolean lastInteractive = prefs.getBoolean("last_interactive", true);

        if (lastTime <= 0L || lastLevel < 0 || snapshot.levelPercent < 0) {
            return;
        }

        long elapsedMs = now - lastTime;
        if (elapsedMs < MIN_DRAIN_INTERVAL_MS || elapsedMs > MAX_DRAIN_INTERVAL_MS) {
            return;
        }
        if (snapshot.charging || lastCharging || interactive || lastInteractive) {
            return;
        }

        int levelDrop = lastLevel - snapshot.levelPercent;
        if (levelDrop <= 0 || levelDrop > 30) {
            return;
        }

        double hours = elapsedMs / 3600000.0;
        double drainPercentPerHour = levelDrop / hours;
        if (drainPercentPerHour <= 0.0 || drainPercentPerHour > 12.0) {
            return;
        }

        int sampleCount = prefs.getInt("idle_drain_sample_count", 0) + 1;
        editor.putInt("idle_drain_sample_count", sampleCount);
        editor.putFloat(
                "idle_drain_percent_per_hour",
                ema(prefs.getFloat("idle_drain_percent_per_hour", 0f), drainPercentPerHour, sampleCount)
        );

        boolean anyRadioOn = wifiOn || btOn || gpsOn || mobileDataActive
                || prefs.getBoolean("last_wifi_on", false)
                || prefs.getBoolean("last_bt_on", false)
                || prefs.getBoolean("last_gps_on", false)
                || prefs.getBoolean("last_mobile_on", false);
        String rateKey = anyRadioOn ? "radio_on_idle_drain_percent_per_hour" : "radio_quiet_idle_drain_percent_per_hour";
        String countKey = anyRadioOn ? "radio_on_idle_sample_count" : "radio_quiet_idle_sample_count";
        int radioSamples = prefs.getInt(countKey, 0) + 1;
        editor.putInt(countKey, radioSamples);
        editor.putFloat(rateKey, ema(prefs.getFloat(rateKey, 0f), drainPercentPerHour, radioSamples));

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        editor.putFloat(
                "idle_drain_h" + hour,
                ema(prefs.getFloat("idle_drain_h" + hour, 0f), drainPercentPerHour, prefs.getInt("idle_drain_samples_h" + hour, 0) + 1)
        );
        editor.putInt("idle_drain_samples_h" + hour, prefs.getInt("idle_drain_samples_h" + hour, 0) + 1);
    }

    private float ema(float previous, double next, int samples) {
        if (previous <= 0f || samples <= 1) {
            return (float) next;
        }
        double alpha = samples < 8 ? 0.45 : 0.25;
        return (float) (previous * (1.0 - alpha) + next * alpha);
    }

    private boolean isInteractive(Context context) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager == null || powerManager.isInteractive();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private boolean isCellularNetworkActive(Context context) {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }
            Network[] networks = connectivityManager.getAllNetworks();
            for (Network network : networks) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return true;
                }
            }
        } catch (SecurityException ignored) {
        } catch (RuntimeException ignored) {
        }
        return false;
    }
}
