<div align="center">
  <h1>⚡ Power Sentinel</h1>
  <p><strong>Advanced Android Battery Optimizer & Sensor Analyst</strong></p>
</div>

---

**Power Sentinel** is an intelligent, privacy-first Android battery optimization concept. It ranks installed apps by likely drain, analyzes sensor usage patterns in the background, and provides personalized, actionable recommendations to maximize your battery life.

Crucially, Power Sentinel is built on a **transparent execution model**. It explains *why* a service is draining your battery and only applies actions when Android allows them, depending on your device's capability tier.

## ✨ Features

### 🧠 AI-Powered Insights
- **Background Sensor Analysis:** Uses `WorkManager` to silently monitor Wi-Fi, Bluetooth, and GPS usage patterns over 24-hour cycles.
- **Smart Recommendations:** The built-in expert system (`AIPlanGenerator`) analyzes your histogram data to provide tailored, plain-English advice (e.g., suggesting turning off Wi-Fi if it's left on all night while unused).
- **Thermal Monitoring:** Keeps an eye on battery temperature to warn you of elevated thermal stress.
- **Real-Time Snapshots:** Calculates estimated milliamp-hour (mAh) drain based on currently active sensors.

### 🛡️ Tiered Capability System
Android restricts standard Play Store apps from silently disabling other apps' services. Power Sentinel embraces this reality by adapting to your device's permission level:

1. **Normal Mode:** Usage-access based drain scoring, service visibility, AI recommendations, and quick shortcuts to native Android system settings to manage sensors manually.
2. **Device Owner Mode:** Managed-device package hiding through `DevicePolicyManager`.
3. **Root Mode:** Explicit root shell actions, enabling precise controls like `pm disable-user` to fully neutralize misbehaving packages or trim global caches.

### 📊 Comprehensive App Scoring
- Scans installed packages and their declared services.
- Estimates power pressure by combining:
  - Foreground usage and recency
  - Background service count
  - System-app status
  - Cache size footprint
  - Current device discharge rates
- Categorizes service intensity as: `Low`, `Balanced`, `Aggressive`, or `Critical`.

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android device or emulator running API 24+

### Build & Run
1. Clone this repository and open the folder in Android Studio.
2. Sync the project with Gradle (the first sync will download the necessary Android Gradle Plugin dependencies).
3. Build and run the app on your target device.

## ⚠️ Reality Check & Disclaimers

**Honest Optimization:** This app does not pretend to magically double your battery life by clearing your RAM (which often causes *more* drain). Instead, it provides data-driven insights.

**Root Limitations:** For root mode, Power Sentinel intentionally disables whole packages via `pm disable-user --user 0 <package>`. Android's public SDK does not support disabling individual services inside other apps. Granular service-level controls require root, OEM/system signing, or private platform APIs.

## 📄 License

This project is intended as a conceptual tool and educational reference for Android power management and background work scheduling.
