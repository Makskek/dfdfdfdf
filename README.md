# Courier Analytics Pro — native Kotlin rewrite

This repository is a clean native Android/Kotlin implementation of the supplied Courier Analytics APK. It does **not** use the original HTML/WebView application.

## GitHub compilation
1. Create a GitHub repository and upload this folder.
2. Push to `main`.
3. Open **Actions → Build Android APK**.
4. Download the `courier-analytics-debug` artifact.

Local build: `./gradlew :app:assembleDebug`.

## Implemented native areas
- Dashboard with period switching, live shift timer, income/orders/distance metrics.
- Shift start / break / finish and quick order entry.
- Shift slots with edit/delete/duplicate/merge/undo and CSV/JSON export.
- Expenses and custom expense categories.
- Goals and order bonuses.
- Bike/e-bike rentals and configurable tariffs.
- Transport/equipment record, service history, battery history, depreciation and payback calculations.
- Operating-cost calculator and tax/tips/bonus settings.
- Theme switching and dashboard tile visibility.
- GPS tracking, route statistics and GPX export.
- Android notifications/reminders, vibration, torch, location, camera and audio recording hooks.
- JSON backup/restore, CSV export and monthly PDF report.
- Settings reset and persistent local storage.

The supplied APK was inspected before the rewrite; its UI layer is an embedded HTML application with 30+ persistent storage keys and a large JavaScript feature set. This project ports the application model and Android-facing functionality to Kotlin rather than packaging that HTML back into a WebView.
