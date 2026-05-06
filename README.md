# Power Sentinel

Power Sentinel is an Android battery optimizer concept that ranks installed apps by likely drain, explains which services are safe to restrict, and applies actions only when Android allows them.

## Reality check

Android does not let a normal Play Store app silently disable other apps' hidden services or clear other apps' private cache. This project is built around three capability tiers:

- **Normal mode:** usage-access based drain scoring, service visibility, recommendations, and shortcuts to system settings.
- **Device Owner mode:** managed-device package hiding through `DevicePolicyManager`.
- **Root mode:** explicit root shell actions such as disabling a package for the current user or asking Android to trim global caches.

That makes the app honest: it can be useful on any phone, and powerful on devices where the owner deliberately grants elevated control.

## MVP features

- Scan installed packages and declared services.
- Estimate power pressure from foreground usage, recency, service count, system-app status, cache size, and current battery discharge.
- Recommend service intensity: `Low`, `Balanced`, `Aggressive`, or `Critical`.
- Open Android's usage-access settings when permission is missing.
- Disable/hide packages only through Device Owner or root execution paths.

## Build

Open this folder in Android Studio and sync Gradle. The first sync downloads the Android Gradle Plugin.

## Important

For root mode, Power Sentinel intentionally disables whole packages with `pm disable-user --user 0 <package>`. Android's public SDK does not support disabling individual services inside other apps. Service-level controls require root, OEM/system signing, or private platform APIs.
