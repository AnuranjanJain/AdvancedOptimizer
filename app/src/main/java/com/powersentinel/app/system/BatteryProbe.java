package com.powersentinel.app.system;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.powersentinel.app.model.BatterySnapshot;

public final class BatteryProbe {
    private final Context context;

    public BatteryProbe(Context context) {
        this.context = context.getApplicationContext();
    }

    public BatterySnapshot read() {
        BatteryManager batteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int level = -1;
        int current = Integer.MIN_VALUE;
        if (batteryManager != null) {
            level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            int currentAverage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
            int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            current = normalizeCurrentToMicroAmps(currentNow, currentAverage);
        }

        Intent batteryIntent = context.registerReceiver(
                null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );
        boolean charging = false;
        int tempDeciC = -1;
        if (batteryIntent != null) {
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            tempDeciC = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        }
        return new BatterySnapshot(level, current, charging, tempDeciC);
    }

    private int normalizeCurrentToMicroAmps(int currentNow, int currentAverage) {
        double nowMa = normalizeCurrentToMilliAmps(currentNow);
        double averageMa = normalizeCurrentToMilliAmps(currentAverage);
        double bestMa = nowMa >= 10.0 ? nowMa : averageMa >= 10.0 ? averageMa : Math.max(nowMa, averageMa);
        if (bestMa <= 0.0) {
            return 0;
        }
        int sign = currentNow < 0 || currentAverage < 0 ? -1 : 1;
        return (int) Math.round(bestMa * 1000.0) * sign;
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
}
