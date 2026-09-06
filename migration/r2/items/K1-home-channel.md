# K1 · HomeChannel 抽取 + HomeState 纯函数 + 解 import 环

- 分片：K1（Wave 1）· 模型 Fable 5.1
- 目标（manifest target）：`manager/src/main/java/moe/shizuku/manager/flutter/HomeChannel.kt`
- 新建：上者 + `manager/src/main/java/moe/shizuku/manager/home/HomeState.kt` + `manager/src/test/java/moe/shizuku/manager/home/HomeStateTest.kt`
- 修改：`manager/src/main/java/moe/shizuku/manager/home/HomeActions.kt`
- **不碰**：`FlutterHostActivity.kt`（协调者接线）、其它任何文件
- 契约：R2 RULEBOOK §R2、§R3、§R8；v1.3 RULEBOOK §1、§1.8、§6、§8
- 只读参考：`flutter/FlutterHostActivity.kt`（`configureFlutterEngine` 里 `shizuku/home` 的整段 `when` 与 `EventChannel` StreamHandler、`emitHomeChanged`、`runAction`、`replySnapshot`、`ok()`）、`flutter/HomeEventGate.kt`、`flutter/{Apps,Settings,Terminal,Pairing}Channel.kt`（通道类形状范例）、`manager/src/test/java/moe/shizuku/manager/flutter/SettingsChannelTest.kt`（测试风格）、`manager_flutter/lib/home/home_models.dart`（`HomeSnapshot.fromJson` 依赖的字段名——一个都不能变）

## 步骤（按序）

1. **HomeState.kt**（纯函数，零 `android.*` / `org.json`）：按 §R3 定义 `HomeFacts`、`HomeTexts`、`buildHomeStateMap`。把 `HomeActions.snapshot()` 里从 `running` 起到 `return JSONObject()...` 的全部派生逻辑逐条搬进来（字段顺序也保持：running, state, uid, permission, grantedCount, rooted, rootRestart, showWireless, showPair, bootRoot, bootWireless, watchdog, dark, adbLimited, adbCommand, appsSub, rootSub, locales, copy）。`appsSub` 三分支：`!running → texts.appsWaiting`；`grantedCount < 0 → texts.appsUnavailable`；否则 `texts.appsCount`（`getQuantityString` 结果由调用方算好传入）。`rootSub`：`!rooted → rootUnavailable`；`running && uid == 0 → rootRestart`；否则 `rootStart`。
2. **HomeActions.kt**：
   - `snapshot()` 改为：采集 `HomeFacts`（`Shizuku.pingBinder/getUid/checkRemotePermission` 的 try/catch 原样保留）、算 `HomeTexts`（用现有 `R.string` 与 `getQuantityString`）、`localeArray()` 改成返回 `List<Map<String, Any?>>`（字段 `tag/label/selected` 不变）、`copyJson()` 改成返回 `Map<String, Any?>`（名字可改为 `copyMap()`；`put` 逐条变 `to`，顺序和 key 一字不改）→ `JSONObject(buildHomeStateMap(...))`。`checkUpdateBlocking()` / `setBootWireless()` 仍返回 `JSONObject`（它们不是快照），不动。
   - `handleStartViaWadbIntent(intent: Intent?)` → `handleStartViaWadb(requested: Boolean)`：`if (!requested) return` 后逻辑不变。删除 `import moe.shizuku.manager.flutter.FlutterHostActivity`。
   - 其它方法一行不改。
3. **HomeChannel.kt**：按 §R2 形状。把宿主里 `MethodChannel(messenger, HOME_CHANNEL).setMethodCallHandler { ... }` 整段和 `EventChannel(messenger, HOME_EVENTS).setStreamHandler(...)` 整段搬入 `register()`；`runAction / replySnapshot / ok()` 私有方法一并搬入；`recreate()` 改为 `activity.recreate()`；`eventSink` 字段搬入；新增 `fun emitChanged(dartExecuting: Boolean, hostResumed: Boolean)`：主线程投递（`Looper.myLooper() == Looper.getMainLooper()` 直跑，否则 `Handler(Looper.getMainLooper()).post`），`if (activity.isDestroyed) return`，`if (!shouldEmitHomeEvent(dartExecuting, hostResumed, triggeredByHostResume = false)) return`，`eventSink?.success("changed")`，整体 `try/catch (_: Throwable)` 同原 `emitHomeChanged`。
4. **HomeStateTest.kt**（JUnit4，包 `moe.shizuku.manager.home`）：§R8 列的用例，至少 6 个；含"字段名精确集合"断言（拼错任一 key 必红）。

## 报告里要给协调者的接线片段

`FlutterHostActivity`：字段 `private lateinit var homeChannel: HomeChannel`；`configureFlutterEngine` 里删掉整段 home `when` + EventChannel，替换为 `homeChannel = HomeChannel(this, ioScope, actions); homeChannel.register(messenger)`；`emitHomeChanged()` 改为 `if (::homeChannel.isInitialized) homeChannel.emitChanged(flutterEngine?.dartExecutor?.isExecutingDart == true, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))`；`onCreate/onNewIntent` 里 `actions.handleStartViaWadbIntent(intent)` → `actions.handleStartViaWadb(intent?.getBooleanExtra(EXTRA_START_SERVICE_VIA_WADB, false) == true)`；宿主可删的 import 与常量（`HOME_CHANNEL / HOME_EVENTS` 若无其它引用可删，或改为引用 `HomeChannel.CHANNEL`）。

## 完成判据

- 协调者 survey build 通过；`HomeStateTest` 全绿；`rg "moe.shizuku.manager.flutter" manager/src/main/java/moe/shizuku/manager/home/HomeActions.kt` 零命中。
- Dart 侧 `flutter test` 56 个仍全绿（字段名未变的证明）。
