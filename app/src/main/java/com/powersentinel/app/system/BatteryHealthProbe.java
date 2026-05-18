package com.powersentinel.app.system;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.powersentinel.app.model.BatteryHealthReport;

import java.util.Locale;

public final class BatteryHealthProbe {
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

        int current = currentAverage != Integer.MIN_VALUE && currentAverage != 0
                ? currentAverage
                : currentNow;
        double currentMa = current == Integer.MIN_VALUE ? 0.0 : Math.abs(current / 1000.0);
        double remainingMah = chargeCounter > 0 ? chargeCounter / 1000.0 : 0.0;
        double fullMah = remainingMah > 0.0 && level > 0
                ? remainingMah / (level / 100.0)
                : 0.0;
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

    private String timeEstimate(boolean charging, double currentMa, double remainingMah, double fullMah) {
        if (currentMa <= 1.0 || remainingMah <= 0.0) {
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
