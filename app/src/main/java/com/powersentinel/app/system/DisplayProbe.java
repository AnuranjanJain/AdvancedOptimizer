package com.powersentinel.app.system;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;

import com.powersentinel.app.model.DisplayReport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DisplayProbe {
    private final Context context;

    public DisplayProbe(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getDisplayInfo() {
        return read().summary();
    }

    public DisplayReport read() {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = metrics.widthPixels;
                int height = metrics.heightPixels;
                float refreshRate = display.getRefreshRate();

                List<String> modes = supportedModes(display);
                String resolutionType = resolutionClass(width, height);
                String panelClass = inferPanelClass(display);
                String impact = impactFor(width, height, refreshRate, panelClass);

                return new DisplayReport(
                        width,
                        height,
                        refreshRate,
                        resolutionType,
                        panelClass,
                        impact,
                        modes
                );
            }
        }
        return new DisplayReport(0, 0, 0f, "Unknown", "Unknown panel", "Unknown", new ArrayList<>());
    }

    private List<String> supportedModes(Display display) {
        List<String> modes = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 23) {
            for (Display.Mode mode : display.getSupportedModes()) {
                modes.add(String.format(
                        Locale.US,
                        "%dx%d @ %.0fHz",
                        mode.getPhysicalWidth(),
                        mode.getPhysicalHeight(),
                        mode.getRefreshRate()
                ));
            }
        }
        return modes;
    }

    private String resolutionClass(int width, int height) {
        int longEdge = Math.max(width, height);
        int shortEdge = Math.min(width, height);
        if (shortEdge >= 1440 || longEdge >= 3000) {
            return "QHD+";
        }
        if (shortEdge >= 1080 && longEdge > 2400) {
            return "FHD+";
        }
        if (shortEdge >= 1080) {
            return "FHD";
        }
        return "HD+";
    }

    private String inferPanelClass(Display display) {
        if (Build.VERSION.SDK_INT < 23) {
            return "Unknown panel";
        }
        Set<Integer> rates = new HashSet<>();
        boolean subSixty = false;
        for (Display.Mode mode : display.getSupportedModes()) {
            int rounded = Math.round(mode.getRefreshRate());
            rates.add(rounded);
            if (rounded < 60) {
                subSixty = true;
            }
        }
        if (subSixty || rates.size() >= 5) {
            return "LTPO-like adaptive panel";
        }
        if (rates.contains(165) || rates.contains(144) || rates.contains(120) || rates.contains(90)) {
            return "LTPS or fixed high-refresh panel";
        }
        return "Standard refresh panel";
    }

    private String impactFor(int width, int height, float refreshRate, String panelClass) {
        int pixels = width * height;
        String refreshImpact = refreshRate >= 144f
                ? "very high refresh load"
                : refreshRate >= 120f
                ? "high refresh load"
                : refreshRate >= 90f
                ? "moderate refresh load"
                : "baseline refresh load";
        String resolutionImpact = pixels >= 4_000_000 ? "QHD-class pixels" : "FHD-class pixels";
        if (panelClass.startsWith("LTPO")) {
            return resolutionImpact + ", " + refreshImpact + ", adaptive panel can save power when idle.";
        }
        return resolutionImpact + ", " + refreshImpact + ", fixed high refresh costs more during scrolling and gaming.";
    }
}
