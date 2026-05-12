package com.powersentinel.app.model;

public final class BatterySnapshot {
    public final int levelPercent;
    public final int averageCurrentMicroAmps;
    public final boolean charging;
    public final int temperatureDeciCelsius;

    public BatterySnapshot(int levelPercent, int averageCurrentMicroAmps, boolean charging, int temperatureDeciCelsius) {
        this.levelPercent = levelPercent;
        this.averageCurrentMicroAmps = averageCurrentMicroAmps;
        this.charging = charging;
        this.temperatureDeciCelsius = temperatureDeciCelsius;
    }

    public boolean isDischargingFast() {
        return !charging && averageCurrentMicroAmps < -350000;
    }
}
