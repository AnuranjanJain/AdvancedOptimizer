package com.powersentinel.app.system;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;

import com.powersentinel.app.model.InstalledAppRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PackageInventory {
    private final Context context;

    public PackageInventory(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<InstalledAppRecord> scan() {
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packages;
        int legacyFlags = PackageManager.GET_SERVICES;
        if (Build.VERSION.SDK_INT >= 33) {
            packages = packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(legacyFlags)
            );
        } else {
            packages = packageManager.getInstalledPackages(legacyFlags);
        }

        List<InstalledAppRecord> records = new ArrayList<>();
        for (PackageInfo info : packages) {
            ApplicationInfo appInfo = info.applicationInfo;
            if (appInfo == null || info.packageName.equals(context.getPackageName())) {
                continue;
            }

            List<String> services = new ArrayList<>();
            if (info.services != null) {
                for (ServiceInfo serviceInfo : info.services) {
                    services.add(serviceInfo.name);
                }
            }

            CharSequence label = appInfo.loadLabel(packageManager);
            boolean systemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            records.add(new InstalledAppRecord(
                    info.packageName,
                    label == null ? info.packageName : label.toString(),
                    appInfo.uid,
                    systemApp,
                    services
            ));
        }

        records.sort(Comparator.comparing(record -> record.label.toLowerCase()));
        return records;
    }
}
