package com.powersentinel.app.system;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import com.powersentinel.app.model.SensorContextReport;

public final class SensorContextProbe {
    private final Context context;

    public SensorContextProbe(Context context) {
        this.context = context.getApplicationContext();
    }

    public SensorContextReport read() {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) {
            return new SensorContextReport(false, false, false, false, false, "Sensor service unavailable.");
        }

        boolean accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null;
        boolean gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
        boolean light = manager.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
        boolean proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null;
        boolean steps = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;

        String summary;
        if (accelerometer && light && proximity) {
            summary = "Ready for pocket, idle, and stationary learning.";
        } else if (accelerometer) {
            summary = "Ready for motion and idle learning.";
        } else {
            summary = "Limited sensor optimization available.";
        }

        return new SensorContextReport(accelerometer, gyroscope, light, proximity, steps, summary);
    }
}
