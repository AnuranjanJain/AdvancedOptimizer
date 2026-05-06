# Architecture

## Data pipeline

1. `PackageInventory` reads installed apps and declared services.
2. `UsageAccess` reads `UsageStatsManager` data when the user grants usage access.
3. `StorageProbe` estimates cache size with `StorageStatsManager`.
4. `BatteryProbe` samples battery current, level, and charging state.
5. `PowerAnalyzer` combines those signals into a score and recommendation.
6. `OptimizationController` chooses which actions are available for the current permission tier.

## Permission tiers

| Tier | What it can do | Notes |
| --- | --- | --- |
| Normal | Analyze, recommend, open settings | Works on regular phones. |
| Device Owner | Hide/unhide packages | Requires managed-device enrollment. |
| Root | Disable packages, trim global cache | Requires explicit root access. |

## Scoring model

The score is not a laboratory-accurate mAh reading. Normal Android apps do not receive per-package battery consumption data. The MVP uses transparent heuristics:

- long foreground time increases score;
- recent activity increases score;
- many declared services increase score;
- large cache increases score;
- system packages are penalized less aggressively to reduce dangerous recommendations;
- live battery current increases urgency when the device is discharging.

Future work can ingest `adb shell dumpsys batterystats` during developer/debug mode for more accurate per-UID power history.
