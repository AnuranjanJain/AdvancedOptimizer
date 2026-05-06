package com.powersentinel.app.system;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import android.provider.Settings;

import java.util.Collections;
import java.util.Map;

public final class UsageAccess {
    private static final long ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final Context context;

    public UsageAccess(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isGranted() {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOpsManager == null) {
            return false;
        }
        int mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public String settingsAction() {
        return Settings.ACTION_USAGE_ACCESS_SETTINGS;
    }

    public Map<String, UsageStats> usageForLastDay() {
        if (!isGranted()) {
            return Collections.emptyMap();
        }
        UsageStatsManager usageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return Collections.emptyMap();
        }
        long end = System.currentTimeMillis();
        long begin = end - ONE_DAY_MILLIS;
        Map<String, UsageStats> stats = usageStatsManager.queryAndAggregateUsageStats(begin, end);
        return stats == null ? Collections.emptyMap() : stats;
    }
}
