# Power Sentinel

Advanced Android battery intelligence for app drain, sensor drain, and device health analysis.

## Version 2.0

Power Sentinel 2.0 introduces a premium dark UI and a sharper product focus: showing what is using battery, why it matters, and which optimizations are safe to perform.

## Core Features

- Dark blue-purple dashboard with a custom battery-and-bolt identity.
- One-time first-run setup for permissions, root/non-root explanation, and terms disclosure.
- Live battery health console:
  - charging or discharging state
  - live current flow in mA
  - remaining mAh
  - used mAh
  - estimated full mAh
  - time remaining or time to full
  - voltage, temperature, and health state
- Per-app battery usage estimates in mAh/hr.
- Per-sensor battery usage estimates in mAh/hr using Android hardware sensor power metadata.
- On-device AI planning based on usage patterns, app behavior, cache pressure, display state, and sensor context.
- Display analysis for FHD/FHD+/QHD+, refresh rate, and inferred high-refresh or adaptive panel behavior.
- Root-aware optimization actions with explicit user consent.
- Play Store-safe non-root mode with guided actions where Android requires system confirmation.

## Capability Modes

- **Normal mode:** analyzes apps, sensors, cache pressure, display configuration, and usage patterns.
- **Device Owner mode:** can hide or unhide managed-device packages through Android device management APIs.
- **Root mode:** can perform stronger actions such as force-stop and cache trim after consent.

## Android Reality Check

Android does not allow a normal Play Store app to silently force-stop other apps, disable hidden services, clear private app data, or change protected system settings. Power Sentinel is designed around this boundary:

- non-root builds explain and guide;
- rooted or managed devices can perform stronger actions;
- all meaningful actions are consent-based.

## Build

Open this repository in Android Studio, sync Gradle, then run the `app` configuration on a device or emulator.

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release Notes

See [CHANGELOG.md](CHANGELOG.md) for version history.
