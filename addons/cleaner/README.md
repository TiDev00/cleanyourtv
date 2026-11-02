# Cleaner

Optional standalone Android TV module card for CleanYourTV, maintained by [TiDev00](https://github.com/TiDev00).
Provides one-click force close of all user-installed background applications to free RAM, live RAM and internal storage monitoring, and direct shortcuts to TV system storage and app management settings.
Build with JDK 17 and Android SDK 34: `./gradlew testReleaseUnitTest lintRelease assembleRelease`.
The small gradlew scripts reuse the repository's pinned Gradle wrapper with this folder as the project;
no wrapper JAR is copied into the add-on and no launcher source is included. Keep this folder inside
the repository when building. Install the APK you build with ADB or your preferred installer.
No binaries or prebuilt APKs are committed.

The launcher discovers the static module card (`spec="1"`) and asks whether to show it. Opening the card shows
system readings and the cleanup controls in this app.
No launcher changes are required.
Readings update periodically only while this screen is resumed; no background service survives leaving it.

## Permissions

The app requests `KILL_BACKGROUND_PROCESSES` (`android.permission.KILL_BACKGROUND_PROCESSES`) to allow closing background processes and reclaiming unused RAM for user-installed applications. Core system services, launchers, input methods, and remote Bluetooth services are protected from termination. No network access (`INTERNET`), no background services, no telemetry, and no ads.

## Validation

Tested on **onn 4K Pro (2024, 1st gen)** (Android TV 12 / Google TV).
Verified:

- One-click force close of user-installed background applications and RAM reclamation
- Live RAM and storage status with dynamic progress meters
- Navigation with TV remote D-pad focus and animations
- Protected system services (Bluetooth remote, IME keyboard, Home launcher)
- Unit tests (`CleanerEngineTest`), `lintRelease`, and release build passed cleanly.
