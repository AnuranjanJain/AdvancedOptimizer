package com.powersentinel.app.model;

public enum ServiceIntensity {
    LOW("Low", "Leave it alone; little battery pressure detected."),
    BALANCED("Balanced", "Restrict background behavior only if you rarely use it."),
    AGGRESSIVE("Aggressive", "Good candidate for background restriction or disable."),
    CRITICAL("Critical", "High drain signal; disable only if you trust the impact.");

    public final String label;
    public final String guidance;

    ServiceIntensity(String label, String guidance) {
        this.label = label;
        this.guidance = guidance;
    }
}
