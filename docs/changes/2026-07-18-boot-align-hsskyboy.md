# Boot autostart aligned to HSSkyBoy/Shizuku (2026-07-18)

Reference: https://github.com/HSSkyBoy/Shizuku

## Their path
`BOOT` → `WirelessBootStartWorker` (WorkManager + `UNMETERED`) → enable wireless ADB if Wi‑Fi up → `SelfStarterService` (mDNS + ADB start). No in-app 20s Wi‑Fi busy-wait; unlock via `USER_PRESENT`.

## Our alignment
| Step | Before (OneKuku-style) | After (HSSkyBoy-style) |
|---|---|---|
| Boot entry | `BootAdbStartService` FGS busy-wait | `AdbStartWorker` + UNMETERED |
| Wi‑Fi | `waitForWifiClient` up to 12–20s | system constraint + `Result.retry` |
| Activate | `WirelessAdbActivation` settle+mDNS timeout | `SelfStarterService` mDNS observe |
| Unlock | poll UserManager | `UserPresentRestartReceiver` |

## Files
- `SelfStarterService.kt` (new)
- `UserPresentRestartReceiver.kt` (new)
- `AdbStartWorker.kt` (rewritten)
- `BootCompleteReceiver.kt` / `WifiReadyMonitor.kt` / `ShizukuApplication.kt`
- `AndroidManifest.xml` (`LOCKED_BOOT_COMPLETED`, SelfStarterService)
