# Shizuku (Fork)

[中文版](./README.zh.md)

## Disclaimer

This is a **fork** of Shizuku. If you are looking for the official Shizuku developed by Rikka, please visit the [**Official Repository**](https://github.com/RikkaApps/Shizuku).

## Changes and Enhancements in this Fork

- **Core Fixes and Optimizations**:
  - ~~Randomize `/data/local/tmp/shizuku` directory name~~
  - ~~Automatically delete `/data/local/tmp/shizuku_starter` files~~
  - Enable ADB root permissions on userdebug ROMs.
  - Support for custom ADB TCP/IP ports, resolving conflicts when the default port 5555 is occupied.

- **Automation and Watchdog**:
  - **Start on Boot (Wireless ADB)**: Supports rooted devices and Android 11+ (Wireless ADB) to automatically start the service on boot without a computer.
  - **Auto Wake-up**: When an application requests Shizuku service, the Manager will attempt to automatically wake up the background service via Wireless ADB if it is not running.
  - **Watchdog Service**: Introduced a watchdog service for ADB mode that monitors the service status in real-time and automatically repairs disconnections, significantly improving stability.

- **Workflow Optimizations**:
  - **One-tap Notification Start**: Optimized the Wireless ADB pairing process. After successful pairing, users can start the service directly from the system notification without returning to the app.
  - **TV Device Optimization**: Tailored startup logic and UI layout for Android TV and set-top boxes, ensuring compatibility with remote control operations.

- **Flutter Manager UI (V15.2.0+; current V15.5.0)**:
  - **OneIMS Ultra skin**: The whole manager shell is now a **Flutter** module (`manager_flutter/`) using the OneIMS Ultra black-and-white liquid-glass design — Montserrat type, glass panels, a floating 2-tab dock (Home / Settings) and a dot-matrix status face that smiles when the service is up and frowns when it is not. Apps and Terminal open from Home as pushed pages.
  - **V15.5.0**: Two-tab dock; language is changed from the Home Lang chip (not Settings); the Home sun/moon control follows the in-app night mode; Home list scrolls under the dock; the PC-ADB command dialog can be cancelled; unused Compose tooling was dropped so the release APK is smaller.
  - **Nothing privileged moved**: only the visible shell changed. The Shizuku server, starter, `rish`, ADB pairing service, watchdog, boot receivers and the system permission dialog are all still native Kotlin; Flutter only talks to them through method channels. Package name stays `moe.shizuku.privileged.api`, so already-authorized client apps keep working.
  - **Pure Black Dark Mode**: "Pure Black" theme option for OLED screens is still available in Settings.
  - **In-app update check**: Home can check this repository's GitHub Releases and download the newest APK.

## Usage Guide

### Start on Boot (Wireless ADB)
1. Configure Shizuku following the Wireless ADB pairing process.
2. Enable `Start on boot (Wireless ADB)` in Settings.
   - This requires `WRITE_SECURE_SETTINGS` permission.
   - It can be granted automatically by the Manager when Shizuku starts (if already running), or manually via ADB:
     `adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS`

> [!CAUTION]
> `WRITE_SECURE_SETTINGS` is a high-risk permission. Use it only if you understand the risks. The developers are not responsible for any consequences.

### Startup Support Details
- **Root Mode**: Supports most rooted devices to automatically load the service on boot.
- **Wireless ADB Mode**: For Android 11+. Uses `WRITE_SECURE_SETTINGS` to monitor network status and restart Shizuku automatically without a PC.
- **TV Devices**: Specifically optimized for stability in television environments.

## Background

When developing apps that require root, the most common method is to execute commands in a su shell. For example, some apps use the `pm enable/disable` command to enable or disable components.

This approach has significant drawbacks:
1. **Very slow** (multiple processes are created).
2. **Unreliable** (requires handling text output).
3. Restricted to existing commands.
4. Requires root even if ADB has sufficient permissions.

Shizuku uses a completely different approach. See below for details.

## Guide & Download
Official documentation and downloads: <https://shizuku.rikka.app/>

## How Shizuku Works?

Android uses `binder` for inter-process communication (IPC). Shizuku guides users to start a process (Shizuku server) with root or ADB privileges. When an application starts, a `binder` pointing to the Shizuku server is sent to the application.

Shizuku acts as an intermediary: it receives requests from applications, forwards them to the system server, and returns the results. This allows apps to use system APIs with higher privileges, which is almost identical to calling system APIs directly.

## Developer Guide
Refer to: <https://github.com/RikkaApps/Shizuku-API>

## Developing Shizuku

### Build
- Clone with `git clone --recurse-submodules`.
- Requirements: JDK 21 (the Gradle wrapper picks an installed JDK 21 automatically via `gradle/gradle-daemon-jvm.properties`), Flutter 3.44+ stable.
- Build the Flutter shell first — the host consumes it as a local Maven AAR, not as a source module:
  ```
  cd manager_flutter
  flutter pub get
  flutter build aar --no-profile
  ```
- Then run gradle task `:manager:assembleDebug` or `:manager:assembleRelease` from the repository root.
- Tests: `flutter test` inside `manager_flutter/`, and `gradlew :manager:testDebugUnitTest` at the root.

The `:manager:assembleDebug` task generates a debuggable server. You can attach a debugger to the `shizuku_server` process. Ensure "Always install with package manager" is checked in Android Studio settings.

### Repository layout for the UI
- `manager_flutter/lib/nav/` — app shell and glass dock; `lib/home`, `lib/apps`, `lib/terminal`, `lib/settings`, `lib/pairing` — one folder per screen (`*_models.dart`, `*_channel.dart`, `*_screen.dart`).
- `manager/src/main/java/moe/shizuku/manager/flutter/` — `FlutterHostActivity` (the only launcher Activity) and one `*Channel.kt` per screen; all business logic lives here and in `HomeActions.kt`.
- `migration/` — the plan, rulebook and verification ledger used for the Compose → Flutter migration.

## License
Licensed under Apache 2.0.

Under Apache 2.0 section 6:
* **FORBIDDEN** to use `ic_launcher` images unless for Shizuku itself.
* **FORBIDDEN** to use `Shizuku` as app name or `moe.shizuku.privileged.api` as ID.

## Credits
- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
- [yangFenTuoZi/Shizuku](https://github.com/yangFenTuoZi/Shizuku)
- [pixincreate/Shizuku](https://github.com/pixincreate/Shizuku)
- [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku)
- ...
