package com.powersentinel.app.system;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.storage.StorageManager;

public final class StorageProbe {
    private final Context context;

    public StorageProbe(Context context) {
        this.context = context.getApplicationContext();
    }

    public long cacheBytesForUid(int uid) {
        StorageStatsManager statsManager =
                (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
        if (statsManager == null) {
            return 0L;
        }
        try {
            StorageStats stats = statsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, uid);
            return Math.max(0L, stats.getCacheBytes());
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
