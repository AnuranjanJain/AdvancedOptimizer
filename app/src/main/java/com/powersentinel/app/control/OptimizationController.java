package com.powersentinel.app.control;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

import com.powersentinel.app.admin.PowerSentinelDeviceAdminReceiver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class OptimizationController {
    private final Context context;

    public OptimizationController(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean canUseDeviceOwnerControls() {
        DevicePolicyManager manager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return manager != null && manager.isDeviceOwnerApp(context.getPackageName());
    }

    public boolean hidePackageAsDeviceOwner(String packageName, boolean hidden) {
        DevicePolicyManager manager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (manager == null || !canUseDeviceOwnerControls()) {
            return false;
        }
        ComponentName admin = new ComponentName(context, PowerSentinelDeviceAdminReceiver.class);
        return manager.setApplicationHidden(admin, packageName, hidden);
    }

    public ShellResult disablePackageWithRoot(String packageName) {
        return runRootCommand("pm disable-user --user 0 " + shellQuote(packageName));
    }

    public ShellResult enablePackageWithRoot(String packageName) {
        return runRootCommand("pm enable " + shellQuote(packageName));
    }

    public ShellResult forceStopPackageWithRoot(String packageName) {
        return runRootCommand("am force-stop " + shellQuote(packageName));
    }

    public ShellResult trimGlobalCachesWithRoot() {
        return runRootCommand("pm trim-caches 999G");
    }

    public ShellResult setWifiEnabled(boolean enabled) {
        String command = enabled ? "svc wifi enable" : "svc wifi disable";
        return runRootCommand(command);
    }

    public ShellResult setBluetoothEnabled(boolean enabled) {
        String command = enabled ? "svc bluetooth enable" : "svc bluetooth disable";
        return runRootCommand(command);
    }

    public ShellResult setLocationEnabled(boolean enabled) {
        // 3 = High accuracy (on), 0 = Off
        String value = enabled ? "3" : "0";
        return runRootCommand("settings put secure location_mode " + value);
    }

    private ShellResult runRootCommand(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int exitCode = process.waitFor();
            return new ShellResult(exitCode, output.toString().trim());
        } catch (IOException exception) {
            return new ShellResult(-1, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ShellResult(-1, "Interrupted while waiting for root command.");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static final class ShellResult {
        public final int exitCode;
        public final String output;

        ShellResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
