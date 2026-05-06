package com.powersentinel.app.model;

public final class AppPowerReport {
    public final InstalledAppRecord app;
    public final long foregroundMillis;
    public final long lastUsedMillis;
    public final long cacheBytes;
    public final int score;
    public final ServiceIntensity intensity;
    public final String recommendation;

    public AppPowerReport(
            InstalledAppRecord app,
            long foregroundMillis,
            long lastUsedMillis,
            long cacheBytes,
            int score,
            ServiceIntensity intensity,
            String recommendation
    ) {
        this.app = app;
        this.foregroundMillis = foregroundMillis;
        this.lastUsedMillis = lastUsedMillis;
        this.cacheBytes = cacheBytes;
        this.score = score;
        this.intensity = intensity;
        this.recommendation = recommendation;
    }
}
