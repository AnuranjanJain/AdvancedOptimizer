package com.powersentinel.app.analyze;

import android.app.usage.UsageStats;
import android.content.Context;
import com.powersentinel.app.system.UsageAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppUsageAnalyzer {
    private final UsageAccess usageAccess;

    public AppUsageAnalyzer(Context context) {
        this.usageAccess = new UsageAccess(context);
    }

    public List<String> getInfrequentApps(long thresholdMillis) {
        List<String> infrequentApps = new ArrayList<>();
        Map<String, UsageStats> statsMap = usageAccess.usageForLastDay();
        for (Map.Entry<String, UsageStats> entry : statsMap.entrySet()) {
            UsageStats stats = entry.getValue();
            if (stats.getTotalTimeInForeground() < thresholdMillis && stats.getTotalTimeInForeground() > 0) {
                infrequentApps.add(entry.getKey());
            }
        }
        return infrequentApps;
    }
}
