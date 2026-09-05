# B6 · AppsChannelTest.kt

- 分片：B apps（Wave 1 试点）
- 目标：`manager/src/test/java/moe/shizuku/manager/flutter/AppsChannelTest.kt`
- 契约：RULEBOOK §1.8、§7.2；参考 `manager/src/test/java/moe/shizuku/manager/management/GrantStatesTest.kt`（JUnit4 风格）

## 必须覆盖（只测 B1 的纯函数，零 Android 依赖）

1. `buildAppsStateJson(running=false, ...)` → `apps` 为空列表且 `running == false`，即使传入非空 `apps`（GAP-3 的服务端保证）。
2. `running=true, adbLimited=true`，两行 `AppRow` → 输出 `apps` 大小 2，字段名精确为 `packageName / uid / userId / label / requiresRoot / granted`（拼错 key 必须红）。
3. `copy` 原样透传在 `copy` 键下。
4. 若 B1 抽了 `toggleOutcome(securityException: Boolean, shizukuUid: Int?)` 之类纯函数（推荐）：`(true, 2000) → "adbLimited"`、`(true, 0) → "success"`、`(true, null) → "success"`、`(false, any) → "success"`。

## 完成判据

协调者运行 `:manager:testDebugUnitTest` 全绿（分片自己不能跑 Gradle；请保证不 import `android.*` / `org.json`）。
