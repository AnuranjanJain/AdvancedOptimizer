package com.powersentinel.app.analyze;

import android.app.usage.UsageStats;

import com.powersentinel.app.model.AppPowerReport;
import com.powersentinel.app.model.BatterySnapshot;
import com.powersentinel.app.model.InstalledAppRecord;
import com.powersentinel.app.model.ServiceIntensity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PowerAnalyzer {
    private static final long HOUR = 60L * 60L * 1000L;
    private static final long DAY = 24L * HOUR;
    private static final long MB = 1024L * 1024L;

    public List<AppPowerReport> analyze(
            List<InstalledAppRecord> apps,
            Map<String, UsageStats> usageStats,
            Map<Integer, Long> cacheByUid,
            BatterySnapshot batterySnapshot
    ) {
        long now = System.currentTimeMillis();
        List<AppPowerReport> reports = new ArrayList<>();
        for (InstalledAppRecord app : apps) {
            UsageStats usage = usageStats.get(app.packageName);
            long foreground = usage == null ? 0L : usage.getTotalTimeInForeground();
            long lastUsed = usage == null ? 0L : usage.getLastTimeUsed();
            long cacheBytes = cacheByUid.containsKey(app.uid) ? cacheByUid.get(app.uid) : 0L;

            int score = score(app, foreground, lastUsed, cacheBytes, now, batterySnapshot);
            ServiceIntensity intensity = intensityFor(score);
            reports.add(new AppPowerReport(
                    app,
                    foreground,
                    lastUsed,
                    cacheBytes,
                    score,
                    intensity,
                    recommendationFor(app, intensity)
            ));
        }
        reports.sort(Comparator.comparingInt((AppPowerReport report) -> report.score).reversed());
        return reports;
    }

    private int score(
            InstalledAppRecord app,
            long foregroundMillis,
            long lastUsedMillis,
            long cacheBytes,
            long now,
            BatterySnapshot battery
    ) {
        int score = 0;
        score += Math.min(35, (int) (foregroundMillis / (20L * 60L * 1000L)));
        score += Math.min(20, app.declaredServices.size() * 2);
        score += Math.min(15, (int) (cacheBytes / (64L * MB)));

        if (lastUsedMillis > 0L) {
            long age = Math.max(0L, now - lastUsedMillis);
            if (age < HOUR) {
                score += 20;
            } else if (age < 6L * HOUR) {
                score += 12;
            } else if (age < DAY) {
                score += 6;
            }
        }

        if (battery.isDischargingFast()) {
            score += 10;
        }
        if (app.systemApp) {
            score -= 12;
        }
        return Math.max(0, Math.min(100, score));
    }

    private ServiceIntensity intensityFor(int score) {
        if (score >= 75) {
            return ServiceIntensity.CRITICAL;
        }
        if (score >= 52) {
            return ServiceIntensity.AGGRESSIVE;
        }
        if (score >= 28) {
            return ServiceIntensity.BALANCED;
        }
        return ServiceIntensity.LOW;
    }

    private String recommendationFor(InstalledAppRecord app, ServiceIntensity intensity) {
        if (app.systemApp && intensity != ServiceIntensity.LOW) {
            return "System app. Prefer manual review before restriction.";
        }
        return intensity.guidance;
    }
}
