# Boot wireless autostart faster (2026-07-18)

## Problem
Cold-boot autostart felt slow / “not starting” because the FGS path stacked long waits:
- Wi‑Fi wait up to 20s
- wireless-debug settle 2.4s (Boot + Activation could both settle)
- mDNS discovery timeout 30s
- binder wait up to 60s
- BOOT debounce 1s

## Change
| Knob | Before | After |
|---|---|---|
| Boot Wi‑Fi wait | 20s | 12s (late Wi‑Fi → NETWORK_STATE retry) |
| Activation Wi‑Fi wait | 20s | 12s |
| Post wireless settle | 2.4s | 1.2s |
| Skip settle in Activation | no | yes when `alreadyWaitedWifi` (Boot settled) |
| mDNS timeout | 30s | 12s |
| Binder wait (activation) | 60s | 20s |
| BOOT debounce | 1s | 300ms |
| Unlock poll | 1.5s | 500ms |

## Files
- `BootAdbStartService.kt`
- `BootCompleteReceiver.kt`
- `WirelessAdbActivation.kt`
- `Starter.kt`
