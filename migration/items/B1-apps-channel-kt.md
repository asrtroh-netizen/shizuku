# B1 · AppsChannel.kt

- 分片：B apps（Wave 1 试点）
- 目标：`manager/src/main/java/moe/shizuku/manager/flutter/AppsChannel.kt`
- 契约：RULEBOOK §1、§5.1、§9；GAP-3、GAP-4
- 只读参考：`management/AppsManagementActivity.kt`（toggle 语义）、`management/AppsManagementComposeScreen.kt`（label 拼接、requiresRoot、文案 key）、`management/GrantStates.kt`（`GrantedCountCache`、`packageGrantKey`）、`flutter/FlutterHostActivity.kt`（线程与错误码模式）、`utils/AppIconCache.kt`、`utils/UserHandleCompat.java`、`utils/ShizukuSystemApis.kt`

## 做什么

```kotlin
package moe.shizuku.manager.flutter

internal data class AppRow(val packageName: String, val uid: Int, val userId: Int, val label: String, val requiresRoot: Boolean, val granted: Boolean)

internal fun buildAppsStateJson(running: Boolean, adbLimited: Boolean, apps: List<AppRow>, copy: JSONObject): JSONObject  // 纯函数，可测

class AppsChannel(private val activity: Activity, private val scope: CoroutineScope) {
    fun register(messenger: BinaryMessenger)
    companion object { const val CHANNEL = "shizuku/apps" }
}
```

- `getState`：IO 线程。`running = Shizuku.pingBinder()`（异常→false）；`adbLimited = running && Shizuku.getUid() != 0`（异常→false）；`running` 才遍历 `AuthorizationManager.getPackages()`，跳过 `applicationInfo == null`；`userId = UserHandleCompat.getUserId(uid)`；他用户 label 拼 ` - ${ShizukuSystemApis.getUserInfo(userId).name} ($userId)`；`requiresRoot = applicationInfo.metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true`；`granted = AuthorizationManager.granted(pkg, uid)`。把 `List<PackageInfo>` 缓存到字段（供 `getIcon`）。
- `toggle`：IO 线程；逐行同 `AppsManagementActivity.onTogglePackage`（§5.1）；然后重新构建整页快照，附 `"result"`。
- `getIcon`：IO 线程；从缓存按 `packageName#uid` 找 `ApplicationInfo`；`AppIconCache.getOrLoadBitmap(activity, appInfo, userId, sizePx)` → `Bitmap.compress(PNG, 100)` → `Base64.encodeToString(..., NO_WRAP)`。
- copy：§9 列表；`adbLimitedMessage` 需 `HtmlCompat.fromHtml(activity.getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get())).toString().replace(Regex("\\s+"), " ").trim()`（同 Compose `plainText`）。
- 不改 `FlutterHostActivity.kt`；在报告里给出接线片段：`AppsChannel(this, ioScope).register(messenger)`。

## 完成判据

- 文件独立可读；无 Robolectric；纯函数 `buildAppsStateJson` 不依赖 Android（`org.json` 在 JVM 单测里由 Android SDK 的 `android.jar` 提供的是 stub，会抛 "Stub!"——因此 **纯函数请用 `org.json` 之外的方式**：返回 `Map<String, Any?>`，再由通道类用 `JSONObject(map)` 序列化；测试断言 Map）。
- 协调者 survey build 通过 `:manager:compileDebugKotlin`。
