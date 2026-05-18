package com.powersentinel.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OptimizationPlan {
    public final int confidencePercent;
    public final boolean learningComplete;
    public final String headline;
    public final String consentText;
    public final List<String> insights;
    public final List<OptimizationAction> actions;

    public OptimizationPlan(
            int confidencePercent,
            boolean learningComplete,
            String headline,
            String consentText,
            List<String> insights,
            List<OptimizationAction> actions
    ) {
        this.confidencePercent = confidencePercent;
        this.learningComplete = learningComplete;
        this.headline = headline;
        this.consentText = consentText;
        this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
    }
}
