package com.powersentinel.app.system;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Display;

public class DisplayProbe {
    private final Context context;

    public DisplayProbe(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getDisplayInfo() {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = metrics.widthPixels;
                int height = metrics.heightPixels;
                float refreshRate = display.getRefreshRate();

                String resolutionType = "HD/FHD";
                if (width >= 1440) resolutionType = "QHD/FHD+";

                return String.format("%dx%d (%s) @ %.0fHz", width, height, resolutionType, refreshRate);
            }
        }
        return "Unknown Display Info";
    }
}
