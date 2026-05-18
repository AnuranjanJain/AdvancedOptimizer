package com.powersentinel.app.model;

public final class SensorDrainReport {
    public final String name;
    public final String vendor;
    public final String category;
    public final float powerMilliAmps;
    public final float resolution;
    public final double estimatedMilliAmpHoursPerHour;
    public final String impactLabel;

    public SensorDrainReport(
            String name,
            String vendor,
            String category,
            float powerMilliAmps,
            float resolution,
            double estimatedMilliAmpHoursPerHour,
            String impactLabel
    ) {
        this.name = name;
        this.vendor = vendor;
        this.category = category;
        this.powerMilliAmps = powerMilliAmps;
        this.resolution = resolution;
        this.estimatedMilliAmpHoursPerHour = estimatedMilliAmpHoursPerHour;
        this.impactLabel = impactLabel;
    }
}
