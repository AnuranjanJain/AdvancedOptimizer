package com.powersentinel.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DisplayReport {
    public final int widthPixels;
    public final int heightPixels;
    public final float refreshRateHz;
    public final String resolutionClass;
    public final String panelClass;
    public final String batteryImpact;
    public final List<String> supportedModes;

    public DisplayReport(
            int widthPixels,
            int heightPixels,
            float refreshRateHz,
            String resolutionClass,
            String panelClass,
            String batteryImpact,
            List<String> supportedModes
    ) {
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.refreshRateHz = refreshRateHz;
        this.resolutionClass = resolutionClass;
        this.panelClass = panelClass;
        this.batteryImpact = batteryImpact;
        this.supportedModes = Collections.unmodifiableList(new ArrayList<>(supportedModes));
    }

    public String summary() {
        return widthPixels + "x" + heightPixels + " " + resolutionClass
                + " at " + Math.round(refreshRateHz) + "Hz, " + panelClass;
    }
}
