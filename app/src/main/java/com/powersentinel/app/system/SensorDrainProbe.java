package com.powersentinel.app.system;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import com.powersentinel.app.model.SensorDrainReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SensorDrainProbe {
    private final Context context;

    public SensorDrainProbe(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<SensorDrainReport> read() {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        List<SensorDrainReport> reports = new ArrayList<>();
        if (manager == null) {
            return reports;
        }

        for (Sensor sensor : manager.getSensorList(Sensor.TYPE_ALL)) {
            float power = Math.max(0f, sensor.getPower());
            reports.add(new SensorDrainReport(
                    sensor.getName(),
                    sensor.getVendor(),
                    category(sensor.getType()),
                    power,
                    sensor.getResolution(),
                    power,
                    impact(power)
            ));
        }

        reports.sort(Comparator.comparingDouble((SensorDrainReport report) ->
                report.estimatedMilliAmpHoursPerHour).reversed());
        return reports;
    }

    private String impact(float power) {
        if (power >= 10f) {
            return "High";
        }
        if (power >= 3f) {
            return "Medium";
        }
        if (power > 0f) {
            return "Low";
        }
        return "Passive";
    }

    private String category(int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_GRAVITY:
            case Sensor.TYPE_LINEAR_ACCELERATION:
            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                return "Motion";
            case Sensor.TYPE_LIGHT:
            case Sensor.TYPE_PROXIMITY:
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return "Environment";
            case Sensor.TYPE_MAGNETIC_FIELD:
            case Sensor.TYPE_ORIENTATION:
                return "Position";
            case Sensor.TYPE_PRESSURE:
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                return "Climate";
            case Sensor.TYPE_STEP_COUNTER:
            case Sensor.TYPE_STEP_DETECTOR:
                return "Fitness";
            default:
                return "System";
        }
    }
}
