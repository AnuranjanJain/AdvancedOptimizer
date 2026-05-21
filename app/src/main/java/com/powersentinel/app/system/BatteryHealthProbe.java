package com.powersentinel.app.system;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;

import com.powersentinel.app.model.BatteryHealthReport;

import java.util.Locale;

public final class BatteryHealthProbe {
    private static final String PREFS = "battery_flow_samples";
    private static final String KEY_LAST_COUNTER_UAH = "last_counter_uah";
    private static final String KEY_LAST_LEVEL = "last_level";
    private static final String KEY_LAST_FULL_MAH = "last_full_mah";
    private static final String KEY_LAST_TS = "last_ts";
    private static final String KEY_SMOOTHED_MA = "smoothed_ma";
    private static final long MIN_SAMPLE_MS = 15_000L;
    private static final long MAX_SAMPLE_MS = 20L * 60L * 1000L;

    private final Context context;

    public BatteryHealthProbe(Context context) {
        this.context = context.getApplicationContext();
    }

    public BatteryHealthReport read() {
        BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int level = manager == null ? -1 : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        int currentAverage = manager == null ? Integer.MIN_VALUE : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        int currentNow = manager == null ? Integer.MIN_VALUE : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        int chargeCounter = manager == null ? Integer.MIN_VALUE : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);

        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = -1;
        int health = -1;
        int voltage = -1;
        int temperature = -1;
        boolean charging = false;
        if (batteryIntent != null) {
            status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        double currentNowMa = normalizeCurrentToMilliAmps(currentNow);
        double currentAverageMa = normalizeCurrentToMilliAmps(currentAverage);
        double remainingMah = chargeCounter > 0 ? chargeCounter / 1000.0 : 0.0;
        double fullMah = remainingMah > 0.0 && level > 0
                ? remainingMah / (level / 100.0)
                : 0.0;
        double instantMa = chooseBestCurrent(currentNowMa, currentAverageMa);
        double estimatedMa = estimateCurrentFromBatteryDelta(chargeCounter, level, fullMah);
        double currentMa = chooseReliableCurrent(instantMa, estimatedMa);
        int current = currentMa > 0.0
                ? (int) Math.round(currentMa * 1000.0)
                : 0;
        double usedMah = fullMah > 0.0 ? Math.max(0.0, fullMah - remainingMah) : 0.0;
        String time = timeEstimate(charging, currentMa, remainingMah, fullMah);

        return new BatteryHealthReport(
                level,
                charging,
                status,
                health,
                voltage,
                temperature,
                current,
                chargeCounter,
                currentMa,
                remainingMah,
                fullMah,
                usedMah,
                time,
                healthLabel(health),
                statusLabel(status)
        );
    }

    private double chooseBestCurrent(double currentNowMa, double currentAverageMa) {
        if (currentNowMa >= 10.0) {
            return currentNowMa;
        }
        if (currentAverageMa >= 10.0) {
            return currentAverageMa;
        }
        return Math.max(0.0, Math.max(currentNowMa, currentAverageMa));
    }

    private double chooseReliableCurrent(double instantMa, double estimatedMa) {
        if (instantMa >= 10.0) {
            return smoothCurrent(instantMa);
        }
        if (estimatedMa >= 20.0) {
            return smoothCurrent(estimatedMa);
        }
        return 0.0;
    }

    private double estimateCurrentFromBatteryDelta(int chargeCounterMicroAh, int level, double fullMah) {
        long now = System.currentTimeMillis();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastTs = prefs.getLong(KEY_LAST_TS, 0L);
        int lastCounter = prefs.getInt(KEY_LAST_COUNTER_UAH, Integer.MIN_VALUE);
        int lastLevel = prefs.getInt(KEY_LAST_LEVEL, -1);
        float lastFullMah = prefs.getFloat(KEY_LAST_FULL_MAH, 0f);

        prefs.edit()
                .putLong(KEY_LAST_TS, now)
                .putInt(KEY_LAST_COUNTER_UAH, chargeCounterMicroAh)
                .putInt(KEY_LAST_LEVEL, level)
                .putFloat(KEY_LAST_FULL_MAH, (float) fullMah)
                .apply();

        long elapsedMs = now - lastTs;
        if (lastTs <= 0L || elapsedMs < MIN_SAMPLE_MS || elapsedMs > MAX_SAMPLE_MS) {
            return 0.0;
        }

        double elapsedHours = elapsedMs / 3600000.0;
        if (chargeCounterMicroAh > 0 && lastCounter > 0 && chargeCounterMicroAh != lastCounter) {
            double deltaMah = Math.abs(chargeCounterMicroAh - lastCounter) / 1000.0;
            return clampCurrent(deltaMah / elapsedHours);
        }

        double usableFullMah = fullMah >= 1000.0 ? fullMah : lastFullMah >= 1000.0 ? lastFullMah : 0.0;
        if (usableFullMah > 0.0 && level >= 0 && lastLevel >= 0 && level != lastLevel) {
            double deltaMah = usableFullMah * (Math.abs(level - lastLevel) / 100.0);
            return clampCurrent(deltaMah / elapsedHours);
        }
        return 0.0;
    }

    private double smoothCurrent(double currentMa) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        float previous = prefs.getFloat(KEY_SMOOTHED_MA, 0f);
        double smoothed = previous >= 10f ? (previous * 0.65) + (currentMa * 0.35) : currentMa;
        prefs.edit().putFloat(KEY_SMOOTHED_MA, (float) smoothed).apply();
        return smoothed;
    }

    private double clampCurrent(double currentMa) {
        if (Double.isNaN(currentMa) || Double.isInfinite(currentMa)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(6000.0, currentMa));
    }

    private double normalizeCurrentToMilliAmps(int rawCurrent) {
        if (rawCurrent == Integer.MIN_VALUE || rawCurrent == 0) {
            return 0.0;
        }
        double value = Math.abs((double) rawCurrent);
        if (value >= 100000.0) {
            return value / 1000.0;
        }
        if (value >= 100.0 && value <= 20000.0) {
            return value;
        }
        if (value > 20000.0) {
            return value / 1000.0;
        }
        return 0.0;
    }

    private String timeEstimate(boolean charging, double currentMa, double remainingMah, double fullMah) {
        if (currentMa < 10.0 || remainingMah <= 0.0) {
            return "Learning";
        }
        double hours = charging && fullMah > remainingMah
                ? (fullMah - remainingMah) / currentMa
                : remainingMah / currentMa;
        if (hours <= 0.0 || Double.isInfinite(hours) || Double.isNaN(hours)) {
            return "Learning";
        }
        int wholeHours = (int) Math.floor(hours);
        int minutes = (int) Math.round((hours - wholeHours) * 60.0);
        return String.format(Locale.US, "%dh %02dm", wholeHours, minutes);
    }

    private String healthLabel(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "Over voltage";
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "Cold";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "Failure";
            default:
                return "Unknown";
        }
    }

    private String statusLabel(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "Not charging";
            default:
                return "Unknown";
        }
    }
}
