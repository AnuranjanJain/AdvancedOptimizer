package com.powersentinel.app.model;

public final class OptimizationAction {
    public enum Kind {
        ROOT_TRIM_CACHE,
        ROOT_FORCE_STOP,
        ROOT_DISABLE_WIFI,
        ROOT_DISABLE_BLUETOOTH,
        ROOT_DISABLE_LOCATION,
        ROOT_DISABLE_MOBILE_DATA,
        DISABLE_SYNC,
        OPEN_APP_SETTINGS,
        OPEN_USAGE_ACCESS,
        OPEN_WIFI_SETTINGS,
        OPEN_BLUETOOTH_SETTINGS,
        OPEN_NETWORK_SETTINGS,
        OPEN_SYNC_SETTINGS,
        OPEN_DISPLAY_SETTINGS,
        OPEN_LOCATION_SETTINGS,
        REVIEW_ONLY
    }

    public final Kind kind;
    public final String category;
    public final String title;
    public final String detail;
    public final String expectedOutcome;
    public final String packageName;
    public final boolean automatic;
    public final int estimatedSavingsScore;
    public final double estimatedBatteryPercent;
    public final int estimatedIdleMinutes;
    public final int estimatedSotMinutes;

    public OptimizationAction(
            Kind kind,
            String title,
            String detail,
            String packageName,
            boolean automatic,
            int estimatedSavingsScore
    ) {
        this(
                kind,
                "Core",
                title,
                detail,
                detail,
                packageName,
                automatic,
                estimatedSavingsScore,
                Math.max(1.0, estimatedSavingsScore * 0.12),
                Math.max(2.0, estimatedSavingsScore * 0.35),
                Math.max(1, estimatedSavingsScore / 2)
        );
    }

    public OptimizationAction(
            Kind kind,
            String category,
            String title,
            String detail,
            String expectedOutcome,
            String packageName,
            boolean automatic,
            int estimatedSavingsScore,
            double estimatedBatteryPercent,
            double estimatedIdleMinutes,
            int estimatedSotMinutes
    ) {
        this.kind = kind;
        this.category = category;
        this.title = title;
        this.detail = detail;
        this.expectedOutcome = expectedOutcome;
        this.packageName = packageName;
        this.automatic = automatic;
        this.estimatedSavingsScore = estimatedSavingsScore;
        this.estimatedBatteryPercent = estimatedBatteryPercent;
        this.estimatedIdleMinutes = (int) Math.max(0, Math.round(estimatedIdleMinutes));
        this.estimatedSotMinutes = estimatedSotMinutes;
    }
}
