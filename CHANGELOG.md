# Changelog

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
