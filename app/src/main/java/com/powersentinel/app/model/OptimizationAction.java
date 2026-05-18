package com.powersentinel.app.model;

public final class OptimizationAction {
    public enum Kind {
        ROOT_TRIM_CACHE,
        ROOT_FORCE_STOP,
        OPEN_APP_SETTINGS,
        OPEN_USAGE_ACCESS,
        OPEN_DISPLAY_SETTINGS,
        OPEN_LOCATION_SETTINGS,
        REVIEW_ONLY
    }

    public final Kind kind;
    public final String title;
    public final String detail;
    public final String packageName;
    public final boolean automatic;
    public final int estimatedSavingsScore;

    public OptimizationAction(
            Kind kind,
            String title,
            String detail,
            String packageName,
            boolean automatic,
            int estimatedSavingsScore
    ) {
        this.kind = kind;
        this.title = title;
        this.detail = detail;
        this.packageName = packageName;
        this.automatic = automatic;
        this.estimatedSavingsScore = estimatedSavingsScore;
    }
}
