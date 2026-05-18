package com.powersentinel.app.model;

public final class BatteryHealthReport {
    public final int levelPercent;
    public final boolean charging;
    public final int status;
    public final int health;
    public final int voltageMillivolts;
    public final int temperatureDeciCelsius;
    public final int currentMicroAmps;
    public final int chargeCounterMicroAh;
    public final double currentMilliAmps;
    public final double remainingMilliAmpHours;
    public final double estimatedFullMilliAmpHours;
    public final double usedMilliAmpHours;
    public final String timeEstimate;
    public final String healthLabel;
    public final String statusLabel;

    public BatteryHealthReport(
            int levelPercent,
            boolean charging,
            int status,
            int health,
            int voltageMillivolts,
            int temperatureDeciCelsius,
            int currentMicroAmps,
            int chargeCounterMicroAh,
            double currentMilliAmps,
            double remainingMilliAmpHours,
            double estimatedFullMilliAmpHours,
            double usedMilliAmpHours,
            String timeEstimate,
            String healthLabel,
            String statusLabel
    ) {
        this.levelPercent = levelPercent;
        this.charging = charging;
        this.status = status;
        this.health = health;
        this.voltageMillivolts = voltageMillivolts;
        this.temperatureDeciCelsius = temperatureDeciCelsius;
        this.currentMicroAmps = currentMicroAmps;
        this.chargeCounterMicroAh = chargeCounterMicroAh;
        this.currentMilliAmps = currentMilliAmps;
        this.remainingMilliAmpHours = remainingMilliAmpHours;
        this.estimatedFullMilliAmpHours = estimatedFullMilliAmpHours;
        this.usedMilliAmpHours = usedMilliAmpHours;
        this.timeEstimate = timeEstimate;
        this.healthLabel = healthLabel;
        this.statusLabel = statusLabel;
    }
}
