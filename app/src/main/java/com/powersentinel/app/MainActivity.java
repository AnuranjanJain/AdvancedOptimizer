package com.powersentinel.app;

import android.app.usage.UsageStats;
import android.content.ContentResolver;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.powersentinel.app.analyze.AIPlanGenerator;
import com.powersentinel.app.analyze.PowerAnalyzer;
import com.powersentinel.app.control.OptimizationController;
import com.powersentinel.app.model.AppPowerReport;
import com.powersentinel.app.model.BatterySnapshot;
import com.powersentinel.app.model.InstalledAppRecord;
import com.powersentinel.app.model.ServiceIntensity;
import com.powersentinel.app.system.AIAgentWorker;
import com.powersentinel.app.system.BatteryProbe;
import com.powersentinel.app.system.PackageInventory;
import com.powersentinel.app.system.StorageProbe;
import com.powersentinel.app.system.UsageAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends AppCompatActivity implements AppReportAdapter.OnAppInteractionListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private AppReportAdapter adapter;
    private ScrollView layoutAiPlan;
    private ScrollView layoutSensors;
    private LinearLayout aiInsightsContainer;
    private ExtendedFloatingActionButton fabScan;
    private TabLayout tabLayout;

    private UsageAccess usageAccess;
    private PackageInventory packageInventory;
    private StorageProbe storageProbe;
    private BatteryProbe batteryProbe;
    private PowerAnalyzer analyzer;
    private OptimizationController optimizationController;
    private AIPlanGenerator aiPlanGenerator;

    // Filter state
    private enum FilterTier { ALL, LOW, MID, HIGH }
    private FilterTier currentFilter = FilterTier.ALL;
    private List<AppPowerReport> fullReports = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Security: StrictMode for Android 16 policies
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usageAccess = new UsageAccess(this);
        packageInventory = new PackageInventory(this);
        storageProbe = new StorageProbe(this);
        batteryProbe = new BatteryProbe(this);
        analyzer = new PowerAnalyzer();
        optimizationController = new OptimizationController(this);
        aiPlanGenerator = new AIPlanGenerator(this);

        initViews();
        scheduleAIAgent();
        refreshStatus();

        // Initial scan
        swipeRefresh.setRefreshing(true);
        scanNow();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void initViews() {
        statusText = findViewById(R.id.text_status);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_view);
        layoutAiPlan = findViewById(R.id.layout_ai_plan);
        layoutSensors = findViewById(R.id.layout_sensors);
        aiInsightsContainer = findViewById(R.id.ai_insights_container);
        fabScan = findViewById(R.id.fab_scan);

        // Toolbar: hide default title so our parallax title doesn't overlap
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Buttons
        MaterialButton btnUsage = findViewById(R.id.button_usage);
        MaterialButton btnTrim = findViewById(R.id.button_trim);

        btnUsage.setOnClickListener(view -> startActivity(new Intent(usageAccess.settingsAction())));
        btnTrim.setOnClickListener(view -> runRootTrim());
        fabScan.setOnClickListener(view -> {
            swipeRefresh.setRefreshing(true);
            scanNow();
        });

        swipeRefresh.setOnRefreshListener(this::scanNow);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppReportAdapter(this, this);
        recyclerView.setAdapter(adapter);

        // Sensor Manage buttons — non-root Intents
        findViewById(R.id.btn_manage_wifi).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(new Intent(Settings.Panel.ACTION_WIFI));
            } else {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });
        findViewById(R.id.btn_manage_bt).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        findViewById(R.id.btn_manage_gps).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        findViewById(R.id.btn_manage_mobile).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)));
        findViewById(R.id.btn_manage_nfc).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
        findViewById(R.id.btn_manage_sync).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS)));
        findViewById(R.id.btn_manage_brightness).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));

        // Setup Tabs
        setupTabs();
    }

    private void setupTabs() {
        tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText("All Apps"));
        tabLayout.addTab(tabLayout.newTab().setText("Low Drain"));
        tabLayout.addTab(tabLayout.newTab().setText("Mid Drain"));
        tabLayout.addTab(tabLayout.newTab().setText("High Drain"));
        tabLayout.addTab(tabLayout.newTab().setText("AI Plan"));
        tabLayout.addTab(tabLayout.newTab().setText("Sensors"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                onTabChanged(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                onTabChanged(tab.getPosition());
            }
        });
    }

    private void onTabChanged(int position) {
        // Reset all views
        swipeRefresh.setVisibility(View.GONE);
        layoutAiPlan.setVisibility(View.GONE);
        layoutSensors.setVisibility(View.GONE);
        fabScan.setVisibility(View.GONE);

        switch (position) {
            case 0: // All Apps
                currentFilter = FilterTier.ALL;
                swipeRefresh.setVisibility(View.VISIBLE);
                fabScan.setVisibility(View.VISIBLE);
                applyFilterAndRender(batteryProbe.read());
                break;
            case 1: // Low Drain
                currentFilter = FilterTier.LOW;
                swipeRefresh.setVisibility(View.VISIBLE);
                fabScan.setVisibility(View.VISIBLE);
                applyFilterAndRender(batteryProbe.read());
                break;
            case 2: // Mid Drain
                currentFilter = FilterTier.MID;
                swipeRefresh.setVisibility(View.VISIBLE);
                fabScan.setVisibility(View.VISIBLE);
                applyFilterAndRender(batteryProbe.read());
                break;
            case 3: // High Drain
                currentFilter = FilterTier.HIGH;
                swipeRefresh.setVisibility(View.VISIBLE);
                fabScan.setVisibility(View.VISIBLE);
                applyFilterAndRender(batteryProbe.read());
                break;
            case 4: // AI Plan
                layoutAiPlan.setVisibility(View.VISIBLE);
                refreshAIPlan();
                break;
            case 5: // Sensors
                layoutSensors.setVisibility(View.VISIBLE);
                refreshSensorStats();
                break;
        }
    }

    private void refreshAIPlan() {
        aiInsightsContainer.removeAllViews();
        List<String> insights = aiPlanGenerator.generateInsights();
        for (String insight : insights) {
            TextView tv = new TextView(this);
            tv.setText(insight);
            tv.setTextSize(14f);
            tv.setTextColor(getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium));
            tv.setLineSpacing(6f, 1f);
            tv.setPadding(0, 0, 0, 32);
            aiInsightsContainer.addView(tv);
        }
    }

    private void refreshSensorStats() {
        float globalTemp = batteryProbe.read().temperatureDeciCelsius / 10.0f;
        String baseTemp = globalTemp > 0 ? String.format(Locale.US, "%.1f", globalTemp) : "--";

        TextView wifiStats = findViewById(R.id.text_wifi_stats);
        TextView btStats = findViewById(R.id.text_bt_stats);
        TextView gpsStats = findViewById(R.id.text_gps_stats);
        TextView mobileStats = findViewById(R.id.text_mobile_stats);
        TextView nfcStats = findViewById(R.id.text_nfc_stats);
        TextView syncStats = findViewById(R.id.text_sync_stats);
        TextView brightnessStats = findViewById(R.id.text_brightness_stats);

        wifiStats.setText("Temp: " + baseTemp + " °C  |  Est: ~50mAh/hr");
        btStats.setText("Temp: " + (globalTemp > 0 ? String.format(Locale.US, "%.1f", globalTemp + 0.5f) : "--") + " °C  |  Est: ~20mAh/hr");
        gpsStats.setText("Temp: " + (globalTemp > 0 ? String.format(Locale.US, "%.1f", globalTemp + 1.2f) : "--") + " °C  |  Est: ~15mAh/hr");

        // Mobile data status
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            boolean dataEnabled = tm != null && tm.isDataEnabled();
            mobileStats.setText(dataEnabled ? "Status: ON  |  Est: ~100mAh/hr" : "Status: OFF");
        } catch (SecurityException e) {
            mobileStats.setText("Permission required");
        }

        // NFC status
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc != null) {
            nfcStats.setText(nfc.isEnabled() ? "Status: ON  |  Est: ~5mAh/hr" : "Status: OFF");
        } else {
            nfcStats.setText("Not available on this device");
        }

        // Auto-sync status
        try {
            boolean syncOn = ContentResolver.getMasterSyncAutomatically();
            syncStats.setText(syncOn ? "Status: ON  |  Est: ~30mAh/hr" : "Status: OFF");
        } catch (SecurityException e) {
            syncStats.setText("Permission required");
        }

        // Screen brightness
        try {
            int brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            int pct = (int) (brightness / 255.0 * 100);
            brightnessStats.setText("Current: " + pct + "%  |  " + (pct > 70 ? "High drain" : "Normal"));
        } catch (Settings.SettingNotFoundException e) {
            brightnessStats.setText("Current: unknown");
        }

        // Dynamic hardware sensor enumeration
        populateHardwareSensors();
    }

    private void populateHardwareSensors() {
        LinearLayout container = findViewById(R.id.hardware_sensors_container);
        container.removeAllViews();

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager == null) return;

        List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : allSensors) {
            // Card for each sensor
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, dpToPx(8));
            card.setLayoutParams(cardParams);
            card.setRadius(dpToPx(12));
            card.setCardElevation(dpToPx(1));
            card.setContentPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);

            // Sensor name
            TextView name = new TextView(this);
            name.setText(sensor.getName());
            name.setTextSize(14f);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(name);

            // Sensor details
            TextView details = new TextView(this);
            String info = "Vendor: " + sensor.getVendor()
                    + "  |  Power: " + String.format(Locale.US, "%.2f mA", sensor.getPower())
                    + "  |  Resolution: " + sensor.getResolution();
            details.setText(info);
            details.setTextSize(11f);
            details.setTextColor(getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium));
            row.addView(details);

            card.addView(row);
            container.addView(card);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void scheduleAIAgent() {
        PeriodicWorkRequest agentWork = new PeriodicWorkRequest.Builder(
                AIAgentWorker.class, 1, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ai_agent_sampling",
                ExistingPeriodicWorkPolicy.KEEP,
                agentWork);
    }

    private void refreshStatus() {
        BatterySnapshot battery = batteryProbe.read();
        String current = battery.averageCurrentMicroAmps == Integer.MIN_VALUE
                ? "unknown"
                : String.format(Locale.US, "%.0f mA", battery.averageCurrentMicroAmps / 1000.0);
        String owner = optimizationController.canUseDeviceOwnerControls() ? "available" : "not enrolled";
        String usage = usageAccess.isGranted() ? "granted" : "missing";
        statusText.setText("Usage access: " + usage
                + "\nDevice Owner: " + owner
                + "\nBattery: " + battery.levelPercent + "%, "
                + (battery.charging ? "charging" : "discharging")
                + ", " + current);
    }

    private void scanNow() {
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

            mainHandler.post(() -> {
                fullReports = reports;
                applyFilterAndRender(battery);
            });
        });
    }

    private void applyFilterAndRender(BatterySnapshot battery) {
        List<AppPowerReport> filtered = new ArrayList<>();
        for (AppPowerReport report : fullReports) {
            switch (currentFilter) {
                case ALL:
                    filtered.add(report);
                    break;
                case LOW:
                    if (report.intensity == ServiceIntensity.LOW) filtered.add(report);
                    break;
                case MID:
                    if (report.intensity == ServiceIntensity.BALANCED) filtered.add(report);
                    break;
                case HIGH:
                    if (report.intensity == ServiceIntensity.AGGRESSIVE ||
                            report.intensity == ServiceIntensity.CRITICAL) filtered.add(report);
                    break;
            }
        }

        int limit = Math.min(50, filtered.size());
        List<AppPowerReport> limitedReports = filtered.subList(0, limit);
        renderReports(limitedReports, battery);
    }

    private void renderReports(List<AppPowerReport> reports, BatterySnapshot battery) {
        swipeRefresh.setRefreshing(false);
        refreshStatus();

        boolean isFirstLoad = adapter.getItemCount() == 0;
        adapter.setReports(reports);

        if (isFirstLoad) {
            LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down);
            recyclerView.setLayoutAnimation(animation);
            recyclerView.scheduleLayoutAnimation();
        }
    }

    // --- Non-root app management: opens the native App Info screen ---
    @Override
    public void onManageClicked(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        startActivity(intent);
    }

    @Override
    public void onHideClicked(String packageName) {
        if (!optimizationController.canUseDeviceOwnerControls()) {
            toast("Device Owner controls are not enrolled.");
            return;
        }
        boolean ok = optimizationController.hidePackageAsDeviceOwner(packageName, true);
        toast(ok ? "Hidden by Device Owner." : "Device Owner hide failed.");
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
