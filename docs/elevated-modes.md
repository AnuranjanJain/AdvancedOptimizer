# Elevated modes

## Normal mode

Install the app, open **Usage**, and grant usage access. Normal mode can scan, estimate, recommend, and open Android settings for each app.

## Device Owner mode

Device Owner can hide packages with Android's public device-management API. This is meant for a freshly provisioned test device or managed fleet device.

```bash
adb shell dpm set-device-owner com.powersentinel.app/.admin.PowerSentinelDeviceAdminReceiver
```

After enrollment, the app's **Hide** action calls `DevicePolicyManager.setApplicationHidden`.

## Root mode

Root mode uses `su` and Android package-manager commands. The MVP intentionally disables whole packages, because normal Android apps cannot disable hidden services inside other apps.

Disable package:

```bash
pm disable-user --user 0 com.example.package
```

Enable package:

```bash
pm enable com.example.package
```

Trim global caches:

```bash
pm trim-caches 999G
```

Avoid using `pm clear` as a cache cleaner. It deletes app data, not just cache.
