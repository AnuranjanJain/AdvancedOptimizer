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
            current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        }

        Intent batteryIntent = context.registerReceiver(
                null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );
        boolean charging = false;
        if (batteryIntent != null) {
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }
        return new BatterySnapshot(level, current, charging);
    }
}
