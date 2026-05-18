package com.powersentinel.app.model;

public final class SensorContextReport {
    public final boolean hasAccelerometer;
    public final boolean hasGyroscope;
    public final boolean hasLightSensor;
    public final boolean hasProximitySensor;
    public final boolean hasStepCounter;
    public final String optimizationSummary;

    public SensorContextReport(
            boolean hasAccelerometer,
            boolean hasGyroscope,
            boolean hasLightSensor,
            boolean hasProximitySensor,
            boolean hasStepCounter,
            String optimizationSummary
    ) {
        this.hasAccelerometer = hasAccelerometer;
        this.hasGyroscope = hasGyroscope;
        this.hasLightSensor = hasLightSensor;
        this.hasProximitySensor = hasProximitySensor;
        this.hasStepCounter = hasStepCounter;
        this.optimizationSummary = optimizationSummary;
    }
}
