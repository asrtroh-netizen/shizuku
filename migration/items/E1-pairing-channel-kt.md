# E1 · PairingChannel.kt（高风险）

- 分片：E pairing（Wave 3）
- 目标：`manager/src/main/java/moe/shizuku/manager/flutter/PairingChannel.kt`
- 契约：RULEBOOK §1、§5.3、§5.4、§6.4、§12；GAP-6
- 只读参考：`adb/AdbPairingTutorialActivity.kt`（**逐行复刻**状态机：`syncState` / `startPairingIfReady` / `ensureLocalNetworkPermissionOrStartPairing` / `startPairingService` 的失败回退 / `onResume` 条件 / `onRequestPermissionsResult`）、`adb/AdbPairingTutorialComposeScreen.kt`（文案 key）、`adb/AdbPairingService.kt`（只用 `startIntent` 与 `NOTIFICATION_CHANNEL` 常量）

## 做什么

```kotlin
class PairingChannel(private val activity: Activity, private val scope: CoroutineScope) {
    fun register(messenger: BinaryMessenger)          // MethodChannel + EventChannel
    fun onHostResumed()                               // 复刻 Activity.onResume 的条件补启动 → 推 "changed"
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray)  // requestCode 1001
    companion object { const val CHANNEL = "shizuku/pairing"; const val EVENTS = "shizuku/pairing/events"; const val REQUEST_LOCAL_NETWORK_PERMISSION = 1001 }
}
```

- 状态四元组 + `supported = SDK_INT >= R` + `showMiuiHint = DeviceCompatibility.isMiui()`；`getState` 每次先 `syncState()`。
- `start` = `startPairingIfReady()` 语义（进入页面时 Dart 调一次，等价于 Activity `onCreate` 的 `syncState(); startPairingIfReady()`）。
- 纯函数：`internal fun shouldRestartPairingOnResume(old: PairingFlags, new: PairingFlags): Boolean`（复刻 `onResume` 的布尔条件），可单测（建议 E5 之外由协调者补 Kotlin 测试，或分片在本文件内提供并在报告标注）。
- 不改 `AdbPairingService`；不改 Manifest。
- 报告里给出接线片段：注册 + `override fun onResume() { super.onResume(); pairingChannel?.onHostResumed() }` + `onRequestPermissionsResult` 转发。

## 完成判据

协调者 survey build 通过；逐 diff 人审对照 `AdbPairingTutorialActivity`。
