# E2 · pairing_models.dart

- 分片：E pairing（Wave 3）
- 目标：`manager_flutter/lib/pairing/pairing_models.dart`
- 契约：RULEBOOK §2、§12；模式同 `home/home_models.dart`

`PairingCopy`（Compose 配对页全部文案 key + 英文 fallback）、`PairingSnapshot(supported, notificationEnabled, notificationListenerEnabled, localNetworkPermissionGranted, pairingServiceStartFailed, showMiuiHint, copy)` + `empty`（`supported:true, localNetworkPermissionGranted:true`，其余 false）+ `fromJson`。

完成判据：`flutter analyze` 无问题。
