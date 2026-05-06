package com.powersentinel.app.model;

public final class BatterySnapshot {
    public final int levelPercent;
    public final int averageCurrentMicroAmps;
    public final boolean charging;

    public BatterySnapshot(int levelPercent, int averageCurrentMicroAmps, boolean charging) {
        this.levelPercent = levelPercent;
        this.averageCurrentMicroAmps = averageCurrentMicroAmps;
        this.charging = charging;
    }

    public boolean isDischargingFast() {
        return !charging && averageCurrentMicroAmps < -350000;
    }
}
