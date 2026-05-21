package com.powersentinel.app.analyze;

import android.app.usage.UsageStats;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.wifi.WifiManager;

import com.powersentinel.app.model.AppPowerReport;
import com.powersentinel.app.model.BatteryHealthReport;
import com.powersentinel.app.model.BatterySnapshot;
import com.powersentinel.app.model.DisplayReport;
import com.powersentinel.app.model.InstalledAppRecord;
import com.powersentinel.app.model.OptimizationAction;
import com.powersentinel.app.model.OptimizationPlan;
import com.powersentinel.app.model.SensorContextReport;
import com.powersentinel.app.system.BatteryHealthProbe;
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
    private static final String SETTINGS_PREFS_NAME = "optimizer_settings";
    private static final String KEY_GENERAL_CACHE_MB = "general_cache_threshold_mb";
    private static final String KEY_SOCIAL_CACHE_MB = "social_cache_threshold_mb";
    private static final String KEY_AI_AUTO_ENABLED = "ai_auto_battery_saver_enabled";
    private static final String KEY_AUTO_WIFI = "ai_auto_wifi_enabled";
    private static final String KEY_AUTO_MOBILE_DATA = "ai_auto_mobile_data_enabled";
    private static final String KEY_AUTO_SYNC = "ai_auto_sync_enabled";
    private static final String KEY_AUTO_BLUETOOTH = "ai_auto_bluetooth_enabled";
    private static final long TEN_MINUTES_MS = 10L * 60L * 1000L;

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
        BatteryHealthReport batteryHealth = new BatteryHealthProbe(context).read();
        DisplayReport display = new DisplayProbe(context).read();
        SensorContextReport sensors = new SensorContextProbe(context).read();

        List<String> insights = new ArrayList<>();
        List<OptimizationAction> actions = new ArrayList<>();

        insights.add(generateRealtimeInsight());
        insights.add(generateLearningInsight(prefs, batteryHealth));
        insights.add("Display: " + display.summary() + ". " + display.batteryImpact);
        insights.add("Sensors: " + sensors.optimizationSummary);

        if (!usageAccess.isGranted()) {
            actions.add(new OptimizationAction(
                    OptimizationAction.Kind.OPEN_USAGE_ACCESS,
                    "Permission",
                    "Grant usage access",
                    "Needed to learn app patterns for 5 to 7 days and rank rarely used apps.",
                    "Unlocks per-app battery history, rare-use detection, and stronger optimization suggestions.",
                    null,
                    false,
                    30,
                    0.0,
                    0.0,
                    0
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

        addAppPatternInsights(reports, rootAvailable, deviceOwnerAvailable, insights, actions, batteryHealth);
        addSensorPatternInsights(prefs, insights, actions, rootAvailable, batteryHealth);
        addSleepNetworkInsights(prefs, insights, actions, rootAvailable, batteryHealth);
        addDisplayInsights(display, insights, actions, batteryHealth);

        if (actions.isEmpty()) {
            actions.add(new OptimizationAction(
                    OptimizationAction.Kind.REVIEW_ONLY,
                    "Review",
                    "No risky actions needed",
                    "Your current pattern looks stable. Keep learning enabled for better weekly recommendations.",
                    "No direct changes were applied because the local model did not detect a high-confidence drain source.",
                    null,
                    false,
                    5,
                    0.4,
                    1.0,
                    0
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
            List<OptimizationAction> actions,
            BatteryHealthReport batteryHealth
    ) {
        int infrequentCount = 0;
        int cacheCount = 0;
        SharedPreferences settings = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
        long generalCacheBytes = mbToBytes(settings.getInt(KEY_GENERAL_CACHE_MB, 50));
        long socialCacheBytes = mbToBytes(settings.getInt(KEY_SOCIAL_CACHE_MB, 100));
        for (AppPowerReport report : reports) {
            if (report.app.systemApp) {
                continue;
            }

            boolean briefUse = report.foregroundMillis > 0L && report.foregroundMillis <= TEN_MINUTES_MS;
            if (briefUse && infrequentCount < 4) {
                infrequentCount++;
                String detail = report.app.label + " was used briefly today. It is a candidate for force-stop after use.";
                SavingsEstimate savings = estimateAppSleepSavings(report, batteryHealth);
                insights.add(detail);
                actions.add(new OptimizationAction(
                        rootAvailable
                                ? OptimizationAction.Kind.ROOT_FORCE_STOP
                                : OptimizationAction.Kind.OPEN_APP_SETTINGS,
                        "App",
                        rootAvailable ? "Force-stop " + report.app.label : "Review " + report.app.label,
                        rootAvailable
                                ? "Root mode can force-stop this app now."
                                : "Android does not let Play Store apps force-stop other apps directly.",
                        rootAvailable
                                ? "Stops this app's background work until you open it again. Estimate uses its services, cache, foreground time, and recency."
                                : "Open Android app settings and restrict this app. Estimate uses its services, cache, foreground time, and recency.",
                        report.app.packageName,
                        rootAvailable,
                        Math.min(35, report.score),
                        savings.batteryPercent,
                        savings.idleMinutes,
                        savings.sotMinutes
                ));
            }

            boolean socialApp = isSocialApp(report.app);
            long cacheThreshold = socialApp ? socialCacheBytes : generalCacheBytes;
            if (report.cacheBytes >= cacheThreshold && cacheCount < 5) {
                cacheCount++;
                SavingsEstimate savings = estimateCacheSavings(report.cacheBytes, batteryHealth);
                long cacheMb = Math.round(report.cacheBytes / 1024.0 / 1024.0);
                long thresholdMb = Math.round(cacheThreshold / 1024.0 / 1024.0);
                actions.add(new OptimizationAction(
                        rootAvailable
                                ? OptimizationAction.Kind.ROOT_TRIM_CACHE
                                : OptimizationAction.Kind.OPEN_APP_SETTINGS,
                        "Cache",
                        (socialApp ? "Social cache: " : "Large cache: ") + report.app.label,
                        "Cache is around " + cacheMb + " MB. Threshold is " + thresholdMb + " MB.",
                        rootAvailable
                                ? "Trims temporary cache pressure. Battery gain is small and comes from fewer storage scans, indexing wakeups, and low-storage pressure."
                                : "Open Android app storage settings. Cache cleanup mainly frees storage; battery gain is small unless cache is huge or constantly rebuilt.",
                        report.app.packageName,
                        rootAvailable,
                        24,
                        savings.batteryPercent,
                        savings.idleMinutes,
                        savings.sotMinutes
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
            boolean rootAvailable,
            BatteryHealthReport batteryHealth
    ) {
        int gpsOn = 0;
        int samples = 0;
        for (int h = 0; h < 24; h++) {
            gpsOn += prefs.getInt("gps_on_h" + h, 0);
            samples += prefs.getInt("samples_h" + h, 0);
        }
        if (samples >= 8 && gpsOn > samples * 0.7f) {
            SavingsEstimate savings = estimateIdleSavings(prefs, batteryHealth, 0.22, 6.0);
            insights.add("Location has been active in most samples. Review apps that keep location hot in the background.");
            actions.add(new OptimizationAction(
                    rootAvailable
                            ? OptimizationAction.Kind.ROOT_DISABLE_LOCATION
                            : OptimizationAction.Kind.OPEN_LOCATION_SETTINGS,
                    "Sensor",
                    "Review location mode",
                    "Use precise/high-accuracy location only when navigation or tracking needs it.",
                    rootAvailable
                            ? "Turns location off now so GPS can rest until you enable it again."
                            : "Move location to off or app-only mode to rest GPS and fused-location sensors.",
                    null,
                    rootAvailable,
                    18,
                    savings.batteryPercent,
                    savings.idleMinutes,
                    savings.sotMinutes
            ));
        }
    }

    private void addSleepNetworkInsights(
            SharedPreferences prefs,
            List<String> insights,
            List<OptimizationAction> actions,
            boolean rootAvailable,
            BatteryHealthReport batteryHealth
    ) {
        int samples = totalSamples(prefs);
        int learnedDrainSamples = prefs.getInt("idle_drain_sample_count", 0);
        int sleepStart = 0;
        int sleepEnd = 6;
        if (samples >= 16) {
            int bestScore = Integer.MAX_VALUE;
            for (int start = 0; start < 24; start++) {
                int score = 0;
                for (int offset = 0; offset < 6; offset++) {
                    int hour = (start + offset) % 24;
                    score += prefs.getInt("wifi_on_h" + hour, 0);
                    score += prefs.getInt("bt_on_h" + hour, 0);
                    score += prefs.getInt("gps_on_h" + hour, 0) * 2;
                    score += prefs.getInt("samples_h" + hour, 0);
                }
                if (score < bestScore) {
                    bestScore = score;
                    sleepStart = start;
                    sleepEnd = (start + 6) % 24;
                }
            }
        }

        boolean confident = samples >= 16;
        SavingsEstimate wifiSavings = estimateIdleSavings(prefs, batteryHealth, 0.24, 6.0);
        SavingsEstimate mobileSavings = estimateIdleSavings(prefs, batteryHealth, 0.30, 6.0);
        SavingsEstimate bluetoothSavings = estimateIdleSavings(prefs, batteryHealth, 0.12, 6.0);
        SavingsEstimate gpsSavings = estimateIdleSavings(prefs, batteryHealth, 0.18, 6.0);
        String window = formatHour(sleepStart) + " to " + formatHour(sleepEnd);
        insights.add((confident ? "Sleep pattern detected: " : "Sleep saver warm-up: ")
                + window + ". Savings are calibrated from "
                + learnedDrainSamples + " screen-off drain sample(s) on this phone.");

        int wifiOn = 0;
        int btOn = 0;
        int gpsOn = 0;
        for (int offset = 0; offset < 6; offset++) {
            int hour = (sleepStart + offset) % 24;
            wifiOn += prefs.getInt("wifi_on_h" + hour, 0);
            btOn += prefs.getInt("bt_on_h" + hour, 0);
            gpsOn += prefs.getInt("gps_on_h" + hour, 0);
        }

        if (rootAvailable) {
            SharedPreferences settings = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
            boolean autoEnabled = settings.getBoolean(KEY_AI_AUTO_ENABLED, false);
            boolean autoWifi = autoEnabled && settings.getBoolean(KEY_AUTO_WIFI, true);
            boolean autoMobileData = autoEnabled && settings.getBoolean(KEY_AUTO_MOBILE_DATA, true);
            boolean autoBluetooth = autoEnabled && settings.getBoolean(KEY_AUTO_BLUETOOTH, true);
            addWifiAction(actions, window, learnedDrainSamples, wifiSavings, autoWifi, rootAvailable);
            addMobileDataAction(actions, learnedDrainSamples, mobileSavings, autoMobileData, rootAvailable);
            if (btOn > 0) {
                addBluetoothAction(actions, learnedDrainSamples, bluetoothSavings, autoBluetooth, rootAvailable);
            }
        } else {
            addWifiAction(actions, window, learnedDrainSamples, wifiSavings, false, false);
            addMobileDataAction(actions, learnedDrainSamples, mobileSavings, false, false);
        }

        SharedPreferences settings = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
        boolean autoEnabled = settings.getBoolean(KEY_AI_AUTO_ENABLED, false);
        boolean autoSync = autoEnabled && settings.getBoolean(KEY_AUTO_SYNC, true);
        if (autoSync) {
            SavingsEstimate syncSavings = estimateIdleSavings(prefs, batteryHealth, 0.10, 6.0);
            actions.add(new OptimizationAction(
                    OptimizationAction.Kind.DISABLE_SYNC,
                    "Sync",
                    "Rest auto-sync in sleep window",
                    "AI can pause Android master sync during low-use hours to reduce background wakeups.",
                    learnedOutcome("Auto-sync rest", syncSavings, learnedDrainSamples),
                    null,
                    true,
                    16,
                    syncSavings.batteryPercent,
                    syncSavings.idleMinutes,
                    syncSavings.sotMinutes
            ));
        } else {
            SavingsEstimate syncSavings = estimateIdleSavings(prefs, batteryHealth, 0.10, 6.0);
            actions.add(new OptimizationAction(
                    OptimizationAction.Kind.OPEN_SYNC_SETTINGS,
                    "Sync",
                    "Review auto-sync overnight",
                    "Enable AI auto sync saving in Settings if you want Power Sentinel to pause sync after consent.",
                    learnedOutcome("Auto-sync rest", syncSavings, learnedDrainSamples),
                    null,
                    false,
                    12,
                    syncSavings.batteryPercent,
                    syncSavings.idleMinutes,
                    syncSavings.sotMinutes
            ));
        }

        if (gpsOn > 0 && !rootAvailable) {
            actions.add(new OptimizationAction(
                    OptimizationAction.Kind.OPEN_LOCATION_SETTINGS,
                    "Sensor",
                    "Let GPS rest overnight",
                    "Location was active inside the learned rest window.",
                    learnedOutcome("GPS rest", gpsSavings, learnedDrainSamples),
                    null,
                    false,
                    18,
                    gpsSavings.batteryPercent,
                    gpsSavings.idleMinutes,
                    gpsSavings.sotMinutes
            ));
        }
    }

    private void addWifiAction(
            List<OptimizationAction> actions,
            String window,
            int learnedDrainSamples,
            SavingsEstimate savings,
            boolean automatic,
            boolean rootAvailable
    ) {
        actions.add(new OptimizationAction(
                automatic ? OptimizationAction.Kind.ROOT_DISABLE_WIFI : OptimizationAction.Kind.OPEN_WIFI_SETTINGS,
                "Sleep",
                automatic ? "Rest Wi-Fi in sleep window" : "Sleep network saver",
                automatic
                        ? "Learns your low-use hours (" + window + ") and can turn Wi-Fi off when the device is idle."
                        : "Detected rest window: " + window + ". Android Play mode cannot toggle Wi-Fi silently.",
                learnedOutcome(automatic ? "Wi-Fi rest" : "Wi-Fi sleep routine", savings, learnedDrainSamples),
                null,
                automatic && rootAvailable,
                automatic ? 28 : 18,
                savings.batteryPercent,
                savings.idleMinutes,
                savings.sotMinutes
        ));
    }

    private void addMobileDataAction(
            List<OptimizationAction> actions,
            int learnedDrainSamples,
            SavingsEstimate savings,
            boolean automatic,
            boolean rootAvailable
    ) {
        actions.add(new OptimizationAction(
                automatic ? OptimizationAction.Kind.ROOT_DISABLE_MOBILE_DATA : OptimizationAction.Kind.OPEN_NETWORK_SETTINGS,
                "Network",
                automatic ? "Rest mobile data in sleep window" : "Review mobile data overnight",
                automatic
                        ? "Mobile data standby can stay awake in weak signal areas. Root mode can disable it during learned sleep hours."
                        : "If signal is weak at night, mobile data standby can drain more than Wi-Fi.",
                learnedOutcome(automatic ? "Mobile data rest" : "Mobile data standby reduction", savings, learnedDrainSamples),
                null,
                automatic && rootAvailable,
                automatic ? 32 : 26,
                savings.batteryPercent,
                savings.idleMinutes,
                savings.sotMinutes
        ));
    }

    private void addBluetoothAction(
            List<OptimizationAction> actions,
            int learnedDrainSamples,
            SavingsEstimate savings,
            boolean automatic,
            boolean rootAvailable
    ) {
        actions.add(new OptimizationAction(
                automatic ? OptimizationAction.Kind.ROOT_DISABLE_BLUETOOTH : OptimizationAction.Kind.OPEN_BLUETOOTH_SETTINGS,
                "Sensor",
                automatic ? "Rest Bluetooth radios" : "Review Bluetooth overnight",
                "Bluetooth was seen active in the learned rest window.",
                learnedOutcome("Bluetooth rest", savings, learnedDrainSamples),
                null,
                automatic && rootAvailable,
                14,
                savings.batteryPercent,
                savings.idleMinutes,
                savings.sotMinutes
        ));
    }

    private void addDisplayInsights(
            DisplayReport display,
            List<String> insights,
            List<OptimizationAction> actions,
            BatteryHealthReport batteryHealth
    ) {
        if (display.refreshRateHz >= 120f) {
            double pressure = display.refreshRateHz >= 144f ? 0.16 : 0.10;
            SavingsEstimate savings = estimateActiveSavings(batteryHealth, pressure, display.refreshRateHz >= 144f ? 4.0 : 2.5);
            actions.add(new OptimizationAction(
                    OptimizationAction.Kind.OPEN_DISPLAY_SETTINGS,
                    "Display",
                    "Review refresh rate",
                    "Dropping from " + Math.round(display.refreshRateHz) + "Hz to 60Hz or adaptive mode can save power outside gaming.",
                    "Use adaptive/90Hz for normal scrolling or 60Hz for idle-heavy days to improve SOT.",
                    null,
                    false,
                    22,
                    savings.batteryPercent,
                    savings.idleMinutes,
                    savings.sotMinutes
            ));
        }
    }

    private String generateLearningInsight(SharedPreferences prefs, BatteryHealthReport batteryHealth) {
        int samples = prefs.getInt("idle_drain_sample_count", 0);
        double idleDrain = learnedIdleDrainPercentPerHour(prefs, batteryHealth);
        String source = samples >= 3 ? "learned from this phone" : "bootstrapping from live battery data";
        return "Idle model: " + round(idleDrain) + "%/hr screen-off drain, "
                + source + " using " + samples + " saved sample(s).";
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

    private long mbToBytes(int mb) {
        return Math.max(1L, mb) * 1024L * 1024L;
    }

    private boolean isSocialApp(InstalledAppRecord app) {
        String packageName = app.packageName.toLowerCase(java.util.Locale.US);
        String label = app.label.toLowerCase(java.util.Locale.US);
        return packageName.contains("facebook")
                || packageName.contains("instagram")
                || packageName.contains("whatsapp")
                || packageName.contains("snapchat")
                || packageName.contains("twitter")
                || packageName.contains("x.android")
                || packageName.contains("tiktok")
                || packageName.contains("telegram")
                || packageName.contains("discord")
                || packageName.contains("reddit")
                || packageName.contains("linkedin")
                || label.contains("facebook")
                || label.contains("instagram")
                || label.contains("whatsapp")
                || label.contains("snapchat")
                || label.contains("telegram")
                || label.contains("discord")
                || label.contains("reddit")
                || label.contains("linkedin")
                || label.equals("x");
    }

    private String formatHour(int hour) {
        int normalized = ((hour % 24) + 24) % 24;
        return String.format(java.util.Locale.US, "%02d:00", normalized);
    }

    private SavingsEstimate estimateIdleSavings(
            SharedPreferences prefs,
            BatteryHealthReport batteryHealth,
            double radioShare,
            double hours
    ) {
        double idleDrain = learnedIdleDrainPercentPerHour(prefs, batteryHealth);
        double radioOnDrain = prefs.getFloat("radio_on_idle_drain_percent_per_hour", 0f);
        double quietDrain = prefs.getFloat("radio_quiet_idle_drain_percent_per_hour", 0f);
        int radioOnSamples = prefs.getInt("radio_on_idle_sample_count", 0);
        int quietSamples = prefs.getInt("radio_quiet_idle_sample_count", 0);

        double savedPercentPerHour;
        if (radioOnSamples >= 2 && quietSamples >= 2 && radioOnDrain > quietDrain) {
            savedPercentPerHour = Math.max(0.02, (radioOnDrain - quietDrain) * radioShare);
        } else {
            savedPercentPerHour = Math.max(0.02, idleDrain * radioShare);
        }

        double batteryPercent = clamp(savedPercentPerHour * hours, 0.2, 12.0);
        int idleMinutes = estimateIdleMinutes(idleDrain, batteryPercent);
        return new SavingsEstimate(batteryPercent, idleMinutes, estimateSotMinutes(batteryHealth, batteryPercent));
    }

    private SavingsEstimate estimateActiveSavings(
            BatteryHealthReport batteryHealth,
            double activeDrainShare,
            double maxBatteryPercent
    ) {
        double activeDrain = activeDrainPercentPerHour(batteryHealth);
        double batteryPercent = clamp(activeDrain * activeDrainShare * 2.0, 0.4, maxBatteryPercent);
        int idleMinutes = estimateIdleMinutes(learnedIdleDrainPercentPerHour(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), batteryHealth), batteryPercent);
        return new SavingsEstimate(batteryPercent, idleMinutes, estimateSotMinutes(batteryHealth, batteryPercent));
    }

    private SavingsEstimate estimateAppSleepSavings(AppPowerReport report, BatteryHealthReport batteryHealth) {
        double foregroundMinutes = report.foregroundMillis / 60000.0;
        double cacheMb = report.cacheBytes / 1024.0 / 1024.0;
        int services = report.app.declaredServices.size();
        long lastUsedAgeMinutes = report.lastUsedMillis <= 0L
                ? 1440L
                : Math.max(0L, (System.currentTimeMillis() - report.lastUsedMillis) / 60000L);

        double servicePressure = Math.min(1.4, services * 0.055);
        double usagePressure = clamp(foregroundMinutes / 90.0, 0.05, 1.25);
        double cachePressure = clamp(cacheMb / 900.0, 0.0, 0.75);
        double recencyPressure = lastUsedAgeMinutes < 60L
                ? 0.85
                : lastUsedAgeMinutes < 360L ? 0.55 : lastUsedAgeMinutes < 1440L ? 0.35 : 0.18;
        double scorePressure = clamp(report.score / 100.0, 0.08, 1.0);

        double batteryPercent = clamp(
                0.18 + (scorePressure * 1.25) + servicePressure + (usagePressure * 0.65) + cachePressure + recencyPressure,
                0.2,
                report.app.systemApp ? 1.8 : 6.5
        );
        int idleMinutes = estimateIdleMinutes(learnedIdleDrainPercentPerHour(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                batteryHealth
        ), batteryPercent);
        return new SavingsEstimate(batteryPercent, idleMinutes, estimateSotMinutes(batteryHealth, batteryPercent));
    }

    private SavingsEstimate estimateCacheSavings(long cacheBytes, BatteryHealthReport batteryHealth) {
        double cacheMb = cacheBytes / 1024.0 / 1024.0;
        double storageWakeupPercent = Math.log10(Math.max(10.0, cacheMb)) * 0.08;
        double lowStoragePressurePercent = cacheMb >= 1024.0 ? 0.25 : cacheMb >= 512.0 ? 0.14 : 0.06;
        double batteryPercent = clamp(storageWakeupPercent + lowStoragePressurePercent, 0.05, 0.85);
        int idleMinutes = estimateIdleMinutes(learnedIdleDrainPercentPerHour(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                batteryHealth
        ), batteryPercent);
        int sotMinutes = (int) Math.max(1, Math.min(6, Math.round(estimateSotMinutes(batteryHealth, batteryPercent) * 0.45)));
        return new SavingsEstimate(batteryPercent, idleMinutes, sotMinutes);
    }

    private double learnedIdleDrainPercentPerHour(SharedPreferences prefs, BatteryHealthReport batteryHealth) {
        int samples = prefs.getInt("idle_drain_sample_count", 0);
        float learned = prefs.getFloat("idle_drain_percent_per_hour", 0f);
        if (samples >= 3 && learned > 0f) {
            return clamp(learned, 0.05, 5.0);
        }
        return clamp(activeDrainPercentPerHour(batteryHealth) * 0.18, 0.12, 1.8);
    }

    private double activeDrainPercentPerHour(BatteryHealthReport batteryHealth) {
        double fullMah = normalizedFullMah(batteryHealth);
        double currentMah = batteryHealth.currentMilliAmps > 1.0 ? batteryHealth.currentMilliAmps : 450.0;
        return clamp((currentMah / fullMah) * 100.0, 1.0, 28.0);
    }

    private int estimateSotMinutes(BatteryHealthReport batteryHealth, double batteryPercent) {
        double fullMah = normalizedFullMah(batteryHealth);
        double activeDrainMah = batteryHealth.currentMilliAmps > 120.0 ? batteryHealth.currentMilliAmps : 520.0;
        double savedMah = fullMah * (batteryPercent / 100.0);
        return (int) Math.max(1, Math.round((savedMah / activeDrainMah) * 60.0));
    }

    private int estimateIdleMinutes(double idleDrainPercentPerHour, double batteryPercent) {
        double safeIdleDrain = clamp(idleDrainPercentPerHour, 0.08, 5.0);
        return (int) Math.max(1, Math.min(720, Math.round((batteryPercent / safeIdleDrain) * 60.0)));
    }

    private double normalizedFullMah(BatteryHealthReport batteryHealth) {
        if (batteryHealth.estimatedFullMilliAmpHours >= 1000.0
                && batteryHealth.estimatedFullMilliAmpHours <= 9000.0) {
            return batteryHealth.estimatedFullMilliAmpHours;
        }
        if (batteryHealth.remainingMilliAmpHours >= 1000.0
                && batteryHealth.levelPercent > 5
                && batteryHealth.levelPercent <= 100) {
            return clamp(batteryHealth.remainingMilliAmpHours / (batteryHealth.levelPercent / 100.0), 1000.0, 9000.0);
        }
        return 4500.0;
    }

    private SavingsEstimate combine(SavingsEstimate first, SavingsEstimate second) {
        return new SavingsEstimate(
                clamp(first.batteryPercent + second.batteryPercent, 0.2, 18.0),
                Math.min(720, first.idleMinutes + second.idleMinutes),
                Math.min(90, first.sotMinutes + second.sotMinutes)
        );
    }

    private String learnedOutcome(String label, SavingsEstimate estimate, int samples) {
        String source = samples >= 3 ? "learned on this phone" : "provisional until more screen-off samples are collected";
        return label + " is estimated to save +" + round(estimate.batteryPercent)
                + "% battery, about +" + estimate.idleMinutes
                + " min idle time, and about +" + estimate.sotMinutes
                + " min SOT; " + source + ".";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String round(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private static final class SavingsEstimate {
        final double batteryPercent;
        final int idleMinutes;
        final int sotMinutes;

        SavingsEstimate(double batteryPercent, int idleMinutes, int sotMinutes) {
            this.batteryPercent = batteryPercent;
            this.idleMinutes = idleMinutes;
            this.sotMinutes = sotMinutes;
        }
    }
}
