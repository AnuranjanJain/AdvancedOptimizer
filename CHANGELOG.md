# Changelog

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
