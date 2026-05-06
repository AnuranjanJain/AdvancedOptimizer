package com.powersentinel.app;

import android.app.Activity;
import android.app.usage.UsageStats;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.powersentinel.app.analyze.PowerAnalyzer;
import com.powersentinel.app.control.OptimizationController;
import com.powersentinel.app.model.AppPowerReport;
import com.powersentinel.app.model.BatterySnapshot;
import com.powersentinel.app.model.InstalledAppRecord;
import com.powersentinel.app.system.BatteryProbe;
import com.powersentinel.app.system.PackageInventory;
import com.powersentinel.app.system.StorageProbe;
import com.powersentinel.app.system.UsageAccess;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout content;
    private TextView statusText;
    private ProgressBar progressBar;

    private UsageAccess usageAccess;
    private PackageInventory packageInventory;
    private StorageProbe storageProbe;
    private BatteryProbe batteryProbe;
    private PowerAnalyzer analyzer;
    private OptimizationController optimizationController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        usageAccess = new UsageAccess(this);
        packageInventory = new PackageInventory(this);
        storageProbe = new StorageProbe(this);
        batteryProbe = new BatteryProbe(this);
        analyzer = new PowerAnalyzer();
        optimizationController = new OptimizationController(this);

        buildUi();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        scrollView.addView(content);
        setContentView(scrollView);

        TextView title = new TextView(this);
        title.setText("Power Sentinel");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(11, 16, 32));
        title.setGravity(Gravity.START);
        title.setTypeface(null, 1);
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Scan services, rank likely drain, and apply controls only where Android permits it.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(71, 85, 105));
        subtitle.setPadding(0, dp(4), 0, dp(14));
        content.addView(subtitle);

        statusText = bodyText("");
        content.addView(statusText);

        LinearLayout actions = row();
        Button scan = button("Scan");
        scan.setOnClickListener(view -> scanNow());
        Button usage = button("Usage");
        usage.setOnClickListener(view -> startActivity(new Intent(usageAccess.settingsAction())));
        Button trim = button("Cache Trim");
        trim.setOnClickListener(view -> runRootTrim());
        actions.addView(scan);
        actions.addView(usage);
        actions.addView(trim);
        content.addView(actions);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        content.addView(progressBar);
    }

    private void refreshStatus() {
        BatterySnapshot battery = batteryProbe.read();
        String current = battery.averageCurrentMicroAmps == Integer.MIN_VALUE
                ? "unknown"
                : String.format(Locale.US, "%.0f mA", battery.averageCurrentMicroAmps / 1000.0);
        String owner = optimizationController.canUseDeviceOwnerControls() ? "available" : "not enrolled";
        String usage = usageAccess.isGranted() ? "granted" : "missing";
        statusText.setText("Usage access: " + usage
                + "\nDevice Owner controls: " + owner
                + "\nBattery: " + battery.levelPercent + "%, "
                + (battery.charging ? "charging" : "discharging")
                + ", current " + current);
    }

    private void scanNow() {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Scanning packages and calculating power pressure...");
        clearReports();

        executor.execute(() -> {
            BatterySnapshot battery = batteryProbe.read();
            List<InstalledAppRecord> apps = packageInventory.scan();
            Map<String, UsageStats> usageStats = usageAccess.usageForLastDay();
            Map<Integer, Long> cacheByUid = new HashMap<>();
            for (InstalledAppRecord app : apps) {
                if (!cacheByUid.containsKey(app.uid)) {
                    cacheByUid.put(app.uid, storageProbe.cacheBytesForUid(app.uid));
                }
            }
            List<AppPowerReport> reports = analyzer.analyze(apps, usageStats, cacheByUid, battery);
            mainHandler.post(() -> renderReports(reports, battery));
        });
    }

    private void clearReports() {
        while (content.getChildCount() > 5) {
            content.removeViewAt(5);
        }
    }

    private void renderReports(List<AppPowerReport> reports, BatterySnapshot battery) {
        progressBar.setVisibility(View.GONE);
        refreshStatus();

        TextView summary = bodyText("Ranked " + reports.size()
                + " apps. Power score is a transparent estimate, not exact mAh.");
        summary.setPadding(0, dp(12), 0, dp(8));
        content.addView(summary);

        int limit = Math.min(30, reports.size());
        for (int i = 0; i < limit; i++) {
            content.addView(reportView(reports.get(i)));
        }
    }

    private View reportView(AppPowerReport report) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        panel.setLayoutParams(params);
        panel.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView name = bodyText(report.app.label + "  [" + report.score + "]");
        name.setTextColor(colorForScore(report.score));
        name.setTypeface(null, 1);
        panel.addView(name);

        String lastUsed = report.lastUsedMillis == 0L
                ? "not seen in usage stats"
                : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(report.lastUsedMillis);
        TextView detail = bodyText(report.app.packageName
                + "\nIntensity: " + report.intensity.label
                + "\nServices: " + report.app.declaredServices.size()
                + ", cache: " + bytes(report.cacheBytes)
                + ", foreground today: " + minutes(report.foregroundMillis)
                + "\nLast used: " + lastUsed
                + "\n" + report.recommendation);
        panel.addView(detail);

        LinearLayout actions = row();
        Button settings = smallButton("Settings");
        settings.setOnClickListener(view -> openAppSettings(report.app.packageName));
        Button hide = smallButton("Hide");
        hide.setEnabled(optimizationController.canUseDeviceOwnerControls());
        hide.setOnClickListener(view -> {
            boolean ok = optimizationController.hidePackageAsDeviceOwner(report.app.packageName, true);
            toast(ok ? "Hidden by Device Owner." : "Device Owner hide failed.");
        });
        Button rootDisable = smallButton("Disable");
        rootDisable.setOnClickListener(view -> runRootDisable(report.app.packageName));
        actions.addView(settings);
        actions.addView(hide);
        actions.addView(rootDisable);
        panel.addView(actions);

        return panel;
    }

    private void runRootDisable(String packageName) {
        toast("Requesting root to disable " + packageName);
        executor.execute(() -> {
            OptimizationController.ShellResult result =
                    optimizationController.disablePackageWithRoot(packageName);
            mainHandler.post(() -> toast(result.isSuccess()
                    ? "Disabled " + packageName
                    : "Root disable failed: " + result.output));
        });
    }

    private void runRootTrim() {
        toast("Requesting root cache trim.");
        executor.execute(() -> {
            OptimizationController.ShellResult result = optimizationController.trimGlobalCachesWithRoot();
            mainHandler.post(() -> toast(result.isSuccess()
                    ? "Cache trim requested."
                    : "Cache trim failed: " + result.output));
        });
    }

    private void openAppSettings(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        startActivity(intent);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        return row;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(2), dp(4), dp(2), dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private Button smallButton(String text) {
        Button button = button(text);
        button.setTextSize(12);
        return button;
    }

    private TextView bodyText(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(14);
        textView.setTextColor(Color.rgb(30, 41, 59));
        textView.setLineSpacing(2f, 1.0f);
        return textView;
    }

    private int colorForScore(int score) {
        if (score >= 75) {
            return Color.rgb(185, 28, 28);
        }
        if (score >= 52) {
            return Color.rgb(194, 65, 12);
        }
        if (score >= 28) {
            return Color.rgb(14, 116, 144);
        }
        return Color.rgb(15, 118, 110);
    }

    private String bytes(long bytes) {
        if (bytes <= 0L) {
            return "0 MB";
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private String minutes(long millis) {
        return String.format(Locale.US, "%.0f min", millis / 60000.0);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
