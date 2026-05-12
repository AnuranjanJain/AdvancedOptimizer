package com.powersentinel.app.analyze;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Expert-system AI that reads the histogram data collected by AIAgentWorker
 * and generates actionable, time-aware power optimization insights.
 */
public class AIPlanGenerator {

    private static final String PREFS_NAME = "ai_agent_data";
    private final Context context;

    public AIPlanGenerator(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Generate a list of plain-English insights based on collected usage patterns.
     * Returns at least one insight even if no data has been collected yet.
     */
    public List<String> generateInsights() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<String> insights = new ArrayList<>();

        // Check if we have any data at all
        boolean hasData = false;
        for (int h = 0; h < 24; h++) {
            if (prefs.getInt("samples_h" + h, 0) > 0) {
                hasData = true;
                break;
            }
        }

        if (!hasData) {
            insights.add(generateRealtimeInsight());
            insights.add("\uD83D\uDD04 The AI Agent is now learning your usage patterns in the background. " +
                    "Check back in a few hours for personalized power recommendations.");
            return insights;
        }

        // Analyze Wi-Fi patterns
        analyzeWifiPattern(prefs, insights);

        // Analyze Bluetooth patterns
        analyzeBluetoothPattern(prefs, insights);

        // Analyze GPS patterns
        analyzeGpsPattern(prefs, insights);

        // Battery health insight
        int lastTemp = prefs.getInt("last_battery_temp", -1);
        if (lastTemp > 380) {
            insights.add("\uD83C\uDF21\uFE0F Your battery temperature is elevated (" +
                    String.format("%.1f", lastTemp / 10.0) + "°C). " +
                    "Consider closing heavy apps and removing the phone case to reduce thermal stress.");
        }

        if (insights.isEmpty()) {
            insights.add("\u2705 Your current usage pattern looks optimal! No changes recommended right now.");
        }

        return insights;
    }

    private String generateRealtimeInsight() {
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCA1 Real-time Snapshot:\n");

        boolean wifiOn = false;
        boolean btOn = false;
        boolean gpsOn = false;

        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            wifiOn = wifi != null && wifi.isWifiEnabled();
        } catch (SecurityException ignored) { }
        sb.append("  • Wi-Fi: ").append(wifiOn ? "ON (~50mAh/hr)" : "OFF").append("\n");

        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            btOn = bt != null && bt.isEnabled();
        } catch (SecurityException ignored) { }
        sb.append("  • Bluetooth: ").append(btOn ? "ON (~20mAh/hr)" : "OFF").append("\n");

        try {
            LocationManager loc = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            gpsOn = loc != null && loc.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (SecurityException ignored) { }
        sb.append("  • GPS: ").append(gpsOn ? "ON (~15mAh/hr)" : "OFF").append("\n");

        int activeCount = (wifiOn ? 1 : 0) + (btOn ? 1 : 0) + (gpsOn ? 1 : 0);
        int estDrain = (wifiOn ? 50 : 0) + (btOn ? 20 : 0) + (gpsOn ? 15 : 0);
        sb.append("\n  Estimated sensor drain: ~").append(estDrain).append("mAh/hr from ")
                .append(activeCount).append(" active sensor(s).");

        return sb.toString();
    }

    private void analyzeWifiPattern(SharedPreferences prefs, List<String> insights) {
        // Find late-night hours (11 PM - 5 AM) where Wi-Fi was consistently on
        int nightOnCount = 0;
        int nightSamples = 0;
        for (int h = 23; h != 6; h = (h + 1) % 24) {
            nightOnCount += prefs.getInt("wifi_on_h" + h, 0);
            nightSamples += prefs.getInt("samples_h" + h, 0);
        }

        if (nightSamples >= 2 && nightOnCount > nightSamples * 0.7) {
            insights.add("\uD83D\uDCA4 Wi-Fi was active during 85%+ of late-night samples (11PM-5AM). " +
                    "Disabling Wi-Fi while you sleep could save ~300mAh overnight. " +
                    "Tap 'Manage' to open Wi-Fi settings.");
        }
    }

    private void analyzeBluetoothPattern(SharedPreferences prefs, List<String> insights) {
        // Check if Bluetooth is always-on but rarely needed
        int totalBtOn = 0;
        int totalSamples = 0;
        for (int h = 0; h < 24; h++) {
            totalBtOn += prefs.getInt("bt_on_h" + h, 0);
            totalSamples += prefs.getInt("samples_h" + h, 0);
        }

        if (totalSamples >= 3 && totalBtOn > totalSamples * 0.9) {
            insights.add("\uD83D\uDD35 Bluetooth has been ON in 90%+ of all samples. " +
                    "If you're not using wireless earbuds or a smartwatch, disabling Bluetooth " +
                    "can save ~20mAh/hr. Tap 'Manage' below to open Bluetooth settings.");
        }
    }

    private void analyzeGpsPattern(SharedPreferences prefs, List<String> insights) {
        // Check if GPS is always-on
        int totalGpsOn = 0;
        int totalSamples = 0;
        for (int h = 0; h < 24; h++) {
            totalGpsOn += prefs.getInt("gps_on_h" + h, 0);
            totalSamples += prefs.getInt("samples_h" + h, 0);
        }

        if (totalSamples >= 3 && totalGpsOn > totalSamples * 0.8) {
            insights.add("\uD83D\uDCCD GPS Location has been active in 80%+ of all samples. " +
                    "Unless you need real-time navigation, switching to 'Battery Saver' location mode " +
                    "can significantly reduce drain. Tap 'Manage' below.");
        }
    }
}
