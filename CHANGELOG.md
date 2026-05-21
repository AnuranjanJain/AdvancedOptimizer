# Changelog

## v2.2 - Device Intelligence Release

Power Sentinel 2.2 deepens the app's device-specific battery model. It adds charging intelligence, more realistic app optimization estimates, safer live mA handling on Android 16/OEM phones, and refreshed release screenshots.

### Highlights

- Added a Charging Intelligence card with charge-cycle guidance, dynamic target charging, estimated cycle cost, and OEM charging-limit detection where Android exposes it.
- Improved live mA flow on real phones that report blocked or placeholder current values such as `0`, `1`, or `2 mA`.
- Added a fallback live-current learner using charge-counter and battery-level deltas over time.
- Changed unreliable tiny current readings to `Learning live flow` instead of showing fake mA values.
- Removed restricted sysfs power-supply probing to avoid Android 16 SELinux denials on production devices.
- Made per-app force-close savings dynamic per app using foreground time, service count, cache pressure, recency, app type, and battery context.
- Made cache cleanup battery impact more honest: cache cleaning mainly frees storage, with only small battery gains unless cache is huge or constantly rebuilt.
- Refreshed README screenshots for the current dark glass UI.

### Notes

- Exact instant mA depends on what the device vendor exposes through Android `BatteryManager`.
- On devices such as OnePlus/OxygenOS where current APIs are blocked or noisy, Power Sentinel learns from battery movement over time instead of displaying misleading numbers.
- Charging-limit and cycle-count readings are shown only when the OEM exposes readable system properties.

## v2.1.1 - Optimization Report Bugfix

This patch fixes the Optimization Report settings handoff after running Optimize Now.

### Fixes

- Replaced the single saved manual action with a full manual action queue.
- The Open Setting button now advances through pending suggestions instead of reopening the same app/settings screen.
- Cache cleanup suggestions are prioritized first when cache-heavy apps are present.
- The report now shows progress such as `Open Setting 1/3` and `Next setting 2/3`.

## v2.1 - AI Optimization Release

Power Sentinel 2.1 focuses on the app's main USP: AI-guided battery optimization with clear per-app, per-sensor, and cache cleanup feedback.

### Highlights

- Added a redesigned Optimize Now preview with battery, idle time, SOT, cache, and cache-app metrics.
- Added a final Optimization Report that shows what was applied, what remains a suggestion, and the cache cleanup result.
- Added phone-specific battery saving estimates that improve over time using screen-off idle drain samples.
- Changed idle impact from a percentage to extra idle time in minutes/hours.
- Added AI auto battery saving settings for Wi-Fi, mobile data, auto-sync, and Bluetooth.
- Added direct master sync pause support where Android allows it.
- Added cache cleanup thresholds:
  - normal apps default to 50 MB;
  - social apps default to 100 MB;
  - both thresholds are adjustable in Settings.
- Added social app cache detection for Facebook, Instagram, WhatsApp, Telegram, Discord, Reddit, LinkedIn, Snapchat, TikTok, X/Twitter, and similar apps.
- Moved Settings into the top-right header button and moved root/non-root status into the Settings page.
- Added README screenshots and product positioning around AI battery intelligence, per-app drain, and per-sensor drain.

### Notes

- Wi-Fi, mobile data, Bluetooth, force-stop, and broad cache trimming remain Android-protected operations on non-root devices.
- Non-root mode provides guided Android settings handoff for protected actions.
- Root mode can run stronger actions after explicit user consent where the device/ROM supports them.
- Early AI estimates are provisional and become more personalized after several days of usage and screen-off samples.

## v2.0 - Dark Intelligence Release

Power Sentinel 2.0 is a full product redesign focused on battery intelligence, live hardware drain visibility, and a premium dark experience.

### Highlights

- Rebuilt the dashboard around a black, blue, and purple visual system.
- Added a new launcher and system splash logo with a battery-and-bolt mark.
- Forced dark mode across the app and Android launch splash.
- Added a one-time first-run setup flow with persistent acceptance.
- Added live battery health metrics: charging state, discharge flow, voltage, temperature, remaining mAh, used mAh, estimated full mAh, and time estimates.
- Added per-app battery usage estimates in mAh/hr.
- Added per-sensor battery usage estimates in mAh/hr using Android hardware sensor power metadata.
- Added Overview, Apps, and Sensors tabs for the main USP.
- Added local on-device optimization planning with consent-based actions.
- Added display analysis for FHD/FHD+/QHD+, refresh rate, and inferred adaptive/high-refresh panel behavior.
- Removed risky Accessibility auto-click automation to keep the Play Store version policy-safe.

### Notes

- Normal/non-root mode provides analysis, mAh estimates, guided optimization, and Android settings handoff where the OS requires user confirmation.
- Root mode can run stronger actions such as force-stop and cache trimming after user consent.
- Per-app mAh values are transparent estimates based on usage access, foreground time, service count, cache pressure, recency, and live battery flow.
- Per-sensor mAh values use Android-reported sensor power in mA, where one hour of active use is approximately mAh.
