package com.powersentinel.app.analyze;

import android.app.usage.UsageStats;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.wifi.WifiManager;

import com.powersentinel.app.model.AppPowerReport;
import com.powersentinel.app.model.BatterySnapshot;
import com.powersentinel.app.model.DisplayReport;
import com.powersentinel.app.model.InstalledAppRecord;
import com.powersentinel.app.model.OptimizationAction;
import com.powersentinel.app.model.OptimizationPlan;
import com.powersentinel.app.model.SensorContextReport;
import com.powersentinel.app.system.BatteryProbe;
import com.powersentinel.app.system.DisplayProbe;
import com.powersentinel.app.system.LearningMode;
import com.powersentinel.app.system.PackageInventory;
import com.powersentinel.app.system.SensorContextProbe;
import com.powersentinel.app.system.StorageProbe;
import com.powersentinel.app.system.UsageAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-device planner. It keeps all usage and device signals local, then turns
 * them into a consent-based optimization plan.
 */
public final class AIPlanGenerator {

    private static final String PREFS_NAME = "ai_agent_data";
    private static final long TEN_MINUTES_MS = 10L * 60L * 1000L;
    private static final long BIG_CACHE_BYTES = 256L * 1024L * 1024L;

    private final Context context;

    public AIPlanGenerator(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<String> generateInsights() {
        return generatePlan(false, false).insights;
    }

    public OptimizationPlan generatePlan(boolean rootAvailable, boolean deviceOwnerAvailable) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        UsageAccess usageAccess = new UsageAccess(context);
        LearningMode learningMode = new LearningMode(context);
        BatterySnapshot battery = new BatteryProbe(context).read();
        DisplayReport display = new DisplayProbe(context).read();
        SensorContextReport sensors = new SensorContextProbe(context).read();

        List<String> insights = new ArrayList<>();
        List<OptimizationAction> actions = new ArrayList<>();

        insights.add(generateRealtimeInsight());
        insights.add("Display: " + display.summary() + ". " + display.batteryImpact);
        insights.add("Sensors: " + sensors.optimizationSummary);

        if (!usageAccess.isGranted()) {
            actions.add(new OptimizationAction(
                    OptimizationAction.Kind.OPEN_USAGE_ACCESS,
                    "Grant usage access",
                    "Needed to learn app patterns for 5 to 7 days and rank rarely used apps.",
                    null,
                    false,
                    30
            ));
            return finishPlan(learningMode, prefs, insights, actions);
        }

        Map<String, UsageStats> usageStats = usageAccess.usageForLastDay();
        List<InstalledAppRecord> apps = new PackageInventory(context).scan();
        StorageProbe storageProbe = new StorageProbe(context);
        Map<Integer, Long> cacheByUid = new HashMap<>();
        for (InstalledAppRecord app : apps) {
            if (!cacheByUid.containsKey(app.uid)) {
                cacheByUid.put(app.uid, storageProbe.cacheBytesForUid(app.uid));
            }
        }

        List<AppPowerReport> reports = new PowerAnalyzer()
                .analyze(apps, usageStats, cacheByUid, battery);

        addAppPatternInsights(reports, rootAvailable, deviceOwnerAvailable, insights, actions);
        addSensorPatternInsights(prefs, insights, actions, rootAvailable);
        addDisplayInsights(display, insights, actions);

        if (actions.isEmpty()) {
            actions.add(new OptimizationAction(
                    OptimizationAction.Kind.REVIEW_ONLY,
                    "No risky actions needed",
                    "Your current pattern looks stable. Keep learning enabled for better weekly recommendations.",
                    null,
                    false,
                    5
            ));
        }

        return finishPlan(learningMode, prefs, insights, actions);
    }

    private OptimizationPlan finishPlan(
            LearningMode learningMode,
            SharedPreferences prefs,
            List<String> insights,
            List<OptimizationAction> actions
    ) {
        int samples = totalSamples(prefs);
        boolean complete = !learningMode.isLearningActive();
        int confidence = complete ? 86 : Math.min(72, 25 + samples * 3);
        String headline = complete
                ? "Pattern learning complete"
                : "Learning mode active: " + learningMode.getDaysRemaining() + " day(s) remaining";
        String consent = "Power Sentinel will only run actions you approve. Root actions can force-stop apps and trim caches. Non-root Play Store mode opens Android-controlled settings screens where Android requires user confirmation.";
        return new OptimizationPlan(confidence, complete, headline, consent, insights, actions);
    }

    private void addAppPatternInsights(
            List<AppPowerReport> reports,
            boolean rootAvailable,
            boolean deviceOwnerAvailable,
            List<String> insights,
            List<OptimizationAction> actions
    ) {
        int infrequentCount = 0;
        int cacheCount = 0;
        for (AppPowerReport report : reports) {
            if (report.app.systemApp) {
                continue;
            }

            boolean briefUse = report.foregroundMillis > 0L && report.foregroundMillis <= TEN_MINUTES_MS;
            if (briefUse && infrequentCount < 4) {
                infrequentCount++;
                String detail = report.app.label + " was used briefly today. It is a candidate for force-stop after use.";
                insights.add(detail);
                actions.add(new OptimizationAction(
                        rootAvailable
                                ? OptimizationAction.Kind.ROOT_FORCE_STOP
                                : OptimizationAction.Kind.OPEN_APP_SETTINGS,
                        rootAvailable ? "Force-stop " + report.app.label : "Review " + report.app.label,
                        rootAvailable
                                ? "Root mode can force-stop this app now."
                                : "Android does not let Play Store apps force-stop other apps directly.",
                        report.app.packageName,
                        rootAvailable,
                        Math.min(35, report.score)
                ));
            }

            if (report.cacheBytes >= BIG_CACHE_BYTES && cacheCount < 3) {
                cacheCount++;
                actions.add(new OptimizationAction(
                        rootAvailable
                                ? OptimizationAction.Kind.ROOT_TRIM_CACHE
                                : OptimizationAction.Kind.OPEN_APP_SETTINGS,
                        "Large cache: " + report.app.label,
                        "Cache is around " + Math.round(report.cacheBytes / 1024.0 / 1024.0) + " MB.",
                        report.app.packageName,
                        rootAvailable,
                        24
                ));
            }
        }

        if (deviceOwnerAvailable) {
            insights.add("Device Owner controls are available for managed-device package hiding.");
        }
    }

    private void addSensorPatternInsights(
            SharedPreferences prefs,
            List<String> insights,
            List<OptimizationAction> actions,
            boolean rootAvailable
    ) {
        int gpsOn = 0;
        int samples = 0;
        for (int h = 0; h < 24; h++) {
            gpsOn += prefs.getInt("gps_on_h" + h, 0);
            samples += prefs.getInt("samples_h" + h, 0);
        }
        if (samples >= 8 && gpsOn > samples * 0.7f) {
            insights.add("Location has been active in most samples. Review apps that keep location hot in the background.");
            actions.add(new OptimizationAction(
                    rootAvailable
                            ? OptimizationAction.Kind.OPEN_LOCATION_SETTINGS
                            : OptimizationAction.Kind.OPEN_LOCATION_SETTINGS,
                    "Review location mode",
                    "Use precise/high-accuracy location only when navigation or tracking needs it.",
                    null,
                    false,
                    18
            ));
        }
    }

    private void addDisplayInsights(
            DisplayReport display,
            List<String> insights,
            List<OptimizationAction> actions
    ) {
        if (display.refreshRateHz >= 120f) {
            actions.add(new OptimizationAction(
                    OptimizationAction.Kind.OPEN_DISPLAY_SETTINGS,
                    "Review refresh rate",
                    "Dropping from " + Math.round(display.refreshRateHz) + "Hz to 60Hz or adaptive mode can save power outside gaming.",
                    null,
                    false,
                    22
            ));
        }
    }

    private String generateRealtimeInsight() {
        boolean wifiOn = false;
        boolean btOn = false;
        boolean gpsOn = false;

        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            wifiOn = wifi != null && wifi.isWifiEnabled();
        } catch (SecurityException ignored) {
        }

        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            btOn = bt != null && bt.isEnabled();
        } catch (SecurityException ignored) {
        }

        try {
            LocationManager loc = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            gpsOn = loc != null && loc.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (SecurityException ignored) {
        }

        int estimatedDrain = (wifiOn ? 50 : 0) + (btOn ? 20 : 0) + (gpsOn ? 15 : 0);
        return "Realtime radios: Wi-Fi " + onOff(wifiOn)
                + ", Bluetooth " + onOff(btOn)
                + ", GPS " + onOff(gpsOn)
                + ". Estimated active radio drain: about " + estimatedDrain + " mAh/hr.";
    }

    private int totalSamples(SharedPreferences prefs) {
        int total = 0;
        for (int h = 0; h < 24; h++) {
            total += prefs.getInt("samples_h" + h, 0);
        }
        return total;
    }

    private String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
