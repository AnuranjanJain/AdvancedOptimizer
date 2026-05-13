package com.powersentinel.app.system;

import android.content.Context;
import android.content.SharedPreferences;

public class LearningMode {
    private static final String PREFS_NAME = "learning_mode_prefs";
    private static final String KEY_INSTALL_TIME = "install_time_ms";
    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;

    private final Context context;

    public LearningMode(Context context) {
        this.context = context.getApplicationContext();
        init();
    }

    private void init() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_INSTALL_TIME)) {
            prefs.edit().putLong(KEY_INSTALL_TIME, System.currentTimeMillis()).apply();
        }
    }

    public boolean isLearningActive() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long installTime = prefs.getLong(KEY_INSTALL_TIME, System.currentTimeMillis());
        return (System.currentTimeMillis() - installTime) < SEVEN_DAYS_MS;
    }

    public int getDaysRemaining() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long installTime = prefs.getLong(KEY_INSTALL_TIME, System.currentTimeMillis());
        long diff = SEVEN_DAYS_MS - (System.currentTimeMillis() - installTime);
        if (diff <= 0) return 0;
        return (int) Math.ceil(diff / (double)(24 * 60 * 60 * 1000));
    }
}
