package com.powersentinel.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InstalledAppRecord {
    public final String packageName;
    public final String label;
    public final int uid;
    public final boolean systemApp;
    public final List<String> declaredServices;

    public InstalledAppRecord(
            String packageName,
            String label,
            int uid,
            boolean systemApp,
            List<String> declaredServices
    ) {
        this.packageName = packageName;
        this.label = label;
        this.uid = uid;
        this.systemApp = systemApp;
        this.declaredServices = Collections.unmodifiableList(new ArrayList<>(declaredServices));
    }
}
