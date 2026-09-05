# 重构计划书 · Shizuku 管家壳 Compose → Flutter（P2–P4）

版本：v2（2026-09-05；v1.3 → v2：用户授权 P4，新增 §13 拆旧两步与范围扩展；v1 → v1.1 为 Wave 1 试点暴露的规格盲区修订：列表项用主题 `Card`、首页无设置按钮、Apps 无事件通道改 Tab 重建、纯函数用 Map；v1.1 → v1.2 为 Wave 2 修订：宿主 `FlutterActivity` 是 `android.app.Activity` 故用 `startActivityForResult`、重建型 Tab 禁用 EventChannel、端口区间 10..65535、`supportsStartOnBoot`；v1.2 → v1.3 为 Wave 3 修订：配对快照加 `autoPairingEnabled`、`onHostResumed` 在场门、`start` 取 onCreate 语义；SCOPE / 判据 / 依赖锁定未变）  
状态：**冻结**（执行期不得改软 SCOPE / 判据 / 行为契约 / 依赖锁定；改动 = 升版本 + 旧证据失效）  
上位文档：`docs/oneims-ultra-flutter-skin-plan.md`（产品方案，本计划书是它的 P2–P4 执行规格）  
前置产物：`MIGRATION_FEASIBILITY.md`（GO）、`MIGRATION_IMPACT.md`

---

## 0. 一句话

把管家界面剩下的三个 Compose 页（授权应用、设置、终端教程）和一个引导页（无线配对）换成 Flutter 黑白液态玻璃皮，加上 4 项底栏壳；**所有业务逻辑仍在 Kotlin**，Flutter 只画皮、只通过 MethodChannel 调用；旧 Compose 页与 `MainActivity` 并存作回滚，最后一期（P4）经用户授权后才拆。

## 1. 迁移目标

- 源：Jetpack Compose 页面（`manager/src/main/java/moe/shizuku/manager/{management,settings,shell,adb}/*ComposeScreen.kt` 及其 Activity）。
- 目标：Flutter 模块 `manager_flutter/`（Flutter 3.44.9 stable / Dart 3.12.2）新页面 + Kotlin 通道类 `manager/src/main/java/moe/shizuku/manager/flutter/*Channel.kt`。
- 类型：**重设计型**。`RULEBOOK.md` 是设计文档；`GAP_INVENTORY.md` 是默认规则盖不住的硬点。

## 2. IN SCOPE（允许新建 / 修改）

| 分片 | 新建文件（manifest target） | 伴随修改（协调者或分片负责人明示） |
|---|---|---|
| A shell-nav | `manager_flutter/lib/onetools/liquid_glass.dart`、`manager_flutter/lib/nav/glass_dock.dart`、`manager_flutter/lib/widgets/glass_choice_dialog.dart`、`manager_flutter/lib/nav/app_shell.dart`、`manager_flutter/test/app_shell_test.dart` | `manager_flutter/lib/onetools/glass.dart`（仅加性 `liquid*` 成员）、`manager_flutter/lib/main.dart`（home → AppShell）、`manager_flutter/lib/home/home_screen.dart`（仅加可选导航回调 + 配对按钮改推 Flutter 页的预留回调）、`manager_flutter/lib/home/home_models.dart`（`HomeCopy` 仅加 4 个 tab 标签字段）、`manager/src/main/java/moe/shizuku/manager/home/HomeActions.kt`（`copyJson()` 末尾仅加 4 行 `.put`，RULEBOOK §3.7）、`manager_flutter/test/widget_test.dart`（若 AppShell 改变根结构需同步断言） |
| B apps | `manager/src/main/java/moe/shizuku/manager/flutter/AppsChannel.kt`、`manager_flutter/lib/apps/apps_models.dart`、`manager_flutter/lib/apps/apps_channel.dart`、`manager_flutter/lib/apps/apps_screen.dart`、`manager_flutter/test/apps_screen_test.dart`、`manager/src/test/java/moe/shizuku/manager/flutter/AppsChannelTest.kt` | 无 |
| C settings | `.../flutter/SettingsChannel.kt`、`manager_flutter/lib/settings/{settings_models,settings_channel,settings_screen}.dart`、`manager_flutter/test/settings_screen_test.dart`、`manager/src/test/java/moe/shizuku/manager/flutter/SettingsChannelTest.kt` | 无 |
| D terminal | `.../flutter/TerminalChannel.kt`、`manager_flutter/lib/terminal/{terminal_models,terminal_channel,terminal_screen}.dart`、`manager_flutter/test/terminal_screen_test.dart` | 无 |
| E pairing | `.../flutter/PairingChannel.kt`、`manager_flutter/lib/pairing/{pairing_models,pairing_channel,pairing_screen}.dart`、`manager_flutter/test/pairing_screen_test.dart` | `manager_flutter/lib/home/home_screen.dart`（仅加可选 `onOpenPairing` 回调并在 `_call('openPairing')` 处 if/else 分流，RULEBOOK §12 v1.2） |
| 协调者 | — | `manager/src/main/java/moe/shizuku/manager/flutter/FlutterHostActivity.kt`（注册通道、生命周期钩子）、`manager_flutter/lib/nav/app_shell.dart`（批间接入各 Tab）、`manager_flutter/lib/main.dart`、`migration/**` |

完整文件范围 = 上表全部 ∪ `migration/**`。任何不在表内的文件被修改即为**越界**。

## 3. OUT OF SCOPE（禁碰）

- `server/`、`starter/`、`shell/`、`api/` 及其子模块、`common/`；`AndroidManifest.xml`（P4 前不动）；`manager/build.gradle`、`settings.gradle`、`gradle/**`、`manager_flutter/pubspec.yaml`、`pubspec.lock`。
- `authorization/**`、`legacy/**`、`adb/AdbPairingService.kt`、`adb/AdbPairingNotificationListener.kt`、`receiver/**`、`watchdog/**`、`starter/**`、`ShizukuApplication.kt`、`ShizukuManagerProvider.kt`、`ShizukuSettings.java`、`home/HomeActions.kt`（唯一例外：分片 A 在 `copyJson()` 末尾加 4 行 tab 标签 `.put`；其余 P4 前不动）。
- 所有 `*ComposeScreen.kt` 与其 Activity（P4 前只读不改；P4 删除需授权）。
- `manager/src/main/res/**`（不新增字符串；缺 key 用最接近的既有 key 并记 OBSERVATIONS）。
- `manager_flutter/lib/onetools/{one_palette,one_status_hero,dot_matrix_face}.dart`、`theme/app_theme.dart`（不回同步 Ultra 新版）。
- 包名 / applicationId `moe.shizuku.privileged.api`；binder 协议；`api/` 对外契约。
- Git：不 commit / stash / reset / checkout；不改 `.gitattributes`。

## 4. 成功判据（客观可跑）

每批（batch）全部满足才算 verified：

1. `manager_flutter/`：`flutter analyze` → `No issues found`；`flutter test` → 全部通过，且新页面至少有 1 个 Widget 测试文件、含 1 个"故意坏输入必须红"的断言（例：空 JSON 走 fallback、`unavailable` 不崩）。
2. `manager/`：`JAVA_HOME=JDK21 .\gradlew.bat :manager:testDebugUnitTest --offline` → BUILD SUCCESSFUL，18 个基线测试仍全绿 + 新增 Kotlin 纯函数测试通过。
3. 行为契约（§6）逐条对照通过（人审 diff + 测试）。
4. 漂移自审（`drift-detection`）：改动文件集合 ⊆ §2 文件范围；`uniq -d` 无多分片同改文件。
5. 替代测试：每个 hunk 问"去掉它任务还失败吗"，否 → 移到 OBSERVATIONS。

**最终**（P3 完成后、P4 之前）：Debug 装包真机验收——底栏 4 Tab 可切、应用 Tab 授权数与首页一致、设置 7 项副作用生效、终端导出可用、配对流程可进系统配对；未做真机验收不得进 P4。

## 5. 验证方式与顺序

- 分片子代理**可以**跑：`flutter analyze`、`flutter test test/<自己的测试文件>`（在 `manager_flutter/`）。**不得**跑 `flutter pub get/upgrade/clean`、任何 Gradle 任务、`flutter build`。
- 协调者串行跑（唯一 build daemon）：`flutter analyze` + `flutter test` 全量 → Gradle `:manager:testDebugUnitTest`（含编译）→ 记入 `VERIFY_LEDGER.tsv`。
- Kotlin 分片产物在协调者的 survey build 后置编译；同类编译错误跨文件重复 → 修 RULEBOOK 再重生成，不逐文件手补。
- 每批红 → 不进下一批。

## 6. 行为契约（外部可见行为必须不变）

| 面 | 必须保持 | 允许的显式改进（单独验证） |
|---|---|---|
| 应用 Tab | 列表 = `AuthorizationManager.getPackages()` 且 `applicationInfo != null`；其他用户的 App 标签 `"<label> - <userName> (<userId>)"`；`V3_REQUIRES_ROOT` meta 显示"需要 root"；点卡或开关 = 切换授权；`SecurityException` 且 Shizuku uid ≠ 0 → "ADB 受限"弹窗；切换后 `GrantedCountCache.value = -1`；服务未运行时不能操作 | Compose 在 binder 死时 `finish()` 整个 Activity；Tab 内改为显示"服务未运行"占位（GAP-3）。Compose 里非 `SecurityException` 的异常会让点击回调崩溃；通道版改为 `result.error` → Dart 重拉快照（v1.1 记录） |
| 设置 Tab | 三组：启动（Root 开机 / 无线开机 / 自动配对 / Watchdog / TCP 端口）、语言（语言选择 / 翻译贡献者 / 翻译链接）、界面（深色模式三选 / 纯黑夜间 / 系统取色）；Root 与无线开机互斥；无线开机缺 `WRITE_SECURE_SETTINGS` → 缺权限弹窗（手动 = 打开指南 + 展示 grant 命令）；自动配对缺通知监听 → 引导到监听设置；Watchdog 开 = 上次启动方式为 ADB 且 binder 活才 start，关 = stop；语言/夜间模式/主题类开关改后宿主 `recreate()` | 无 |
| 终端 Tab | 文案与 `ShellTutorialComposeScreen` 同源（`R.string`）；"导出"= 系统文档树选择 → 删旧 `rish`/`rish_shizuku.dex` → 从 assets 写入；"查看指南"= `Helps.RISH`（整卡点击，与 Compose `CalloutCard.onClick` 一致） | 导出的文件操作从主线程改到 IO 线程，且异常被吞掉（Compose 会在主线程崩溃）——v1.2 记录 |
| 设置 Tab（补充） | TCP 端口合法区间 10..65535；SDK<30 且非 TV 非 root 时隐藏四个启动开关；设置页开无线开机不调 `WifiReadyMonitor`（与 Compose 设置页一致） | "手动"按钮：Compose 是打开指南 + 第二个对话框显示命令（含"发送"分享）；Flutter 改为打开指南 + 复制命令到剪贴板——v1.2 记录的显式差异 |
| 配对页 | 状态四元组（通知开 / 通知监听开 / 本地网络权限 / 前台服务启动失败）与 `AdbPairingTutorialActivity` 同步逻辑一致：进入即同步并按条件启动配对服务；`onResume` 重同步且满足条件补启动（仅配对页在场时）；三个系统设置跳转；MIUI 提示；通知监听卡仅在自动配对已开且监听未开时显示 | 视觉：只有"服务启动失败"卡用 `errorContainer`，其余警告卡只把图标染 `error` 色（Compose 全部用 errorContainer）；首页"配对"按钮连点只推一页（Compose 会开两个 Activity）——v1.3 记录 |
| 首页 | 现有 P1 行为全部不变；快捷磁贴"应用/终端"改为切换 Tab（Kotlin `openApps/openTerminal/openSettings` 保留；首页本无"设置"按钮，设置入口 = 底栏——v1.1 修订） | 切 Tab 替代开新 Activity（产品方案要求）；非首页 Tab 每次被选中重建以拿新鲜快照（等价于 Compose 每次新开 Activity） |
| 全局 | 包名、授权弹窗、配对服务、开机自启、Watchdog、通知、`api/` 全部不变 | 无 |

## 7. 风险分级与批计划

| 批 | 分片 | 文件数 | 风险 | 门禁 |
|---|---|---|---|---|
| **Pilot（Wave 1）** | A shell-nav + B apps | 11 新建 + 4 伴随 = 15 | 低-中 / 中 | 暴露规格盲区；全绿后才开 Wave 2；分片 A/B 各自也是并行子代理 |
| Wave 2 | C settings + D terminal | 11 | 中 / 低-中 | 依赖 Wave 1 的 `app_shell.dart`、`glass_choice_dialog.dart` 已 verified |
| Wave 3 | E pairing | 5 | 高 | 单独批；协调者逐 diff 人审；宿主需加 `onResume` / `onRequestPermissionsResult` 钩子 |
| Wave 4（P4） | 拆旧 | ~14 删 + Manifest + 2 处指向 | 高 | **需用户显式授权**，且真机验收通过；删前保留 `MainActivity` 回滚路径的替代方案（见 §10） |

## 8. 状态根与基线身份

- `<STATE_ROOT>` = `migration/`（仓根相对）。所有 SPEC / RULEBOOK / GAP / 队列 / 账本 / 交接文件只在此处，子文档不得在仓根另建同名副本。
- 仓库根：`E:\GQ\Ultra\Shizuku`；分支 `main`；`BASELINE_COMMIT` = `a10c72af207e046bf2ed38dfc508d24bc33e1aa6`。
- 基线工作树：11 个纯行尾差异文件（`BASELINE_DIRTY_STATE.tsv` 记 OID）；未跟踪 `.xj-cursor/`（`BASELINE_UNTRACKED.txt`）。这些不是迁移改动，也不得被迁移"顺手"改掉。
- 路径格式：UTF-8、仓根相对、`/` 分隔、每行一项；禁止绝对路径、`..`、CR/TAB。

## 9. 工作项三件套与两层完成判据

- `MIGRATION_ALLOWLIST.txt`：工作项 = `migration/items/<ID>-*.md` 卡片（每张卡 = 一个交付物的规格）。
- `MIGRATION_MANIFEST.tsv`：`work_path<TAB>target_path`，target = 要产出的源文件。
- `MIGRATION_ORDER.txt`：依赖安全顺序（A → B → C → D → E；组内 models → channel → screen → test）。
- `translated`：target 文件经原子写完整落盘且 `flutter analyze` 不报该文件错误。
- `verified`：当前 target 内容 SHA256 进入 `VERIFY_LEDGER.tsv`，且绑定当前 SPEC / RULEBOOK / GAP / JUDGE / TOOL 哈希的 §4 证据全绿。
- 伴随修改（`glass.dart`、`main.dart`、`home_screen.dart`、`FlutterHostActivity.kt`、`app_shell.dart`）不单列队列项，由所属批的 verify 一并裁定，并在 `CHANGE_MANIFEST.md` 列出。

## 10. 依赖锁定、工具证据、禁止涌现

- **依赖锁定**：不允许改 `pubspec.yaml` / `pubspec.lock` / 任何 Gradle 依赖。`DEPENDENCY_ALLOWLIST.txt` 为空文件。Ultra `app_shell.dart` 依赖 `provider` → 不复制，自写。
- **工具证据**：`TOOL_EVIDENCE.tsv`（flutter / dart / java / gradle wrapper / git / rg 版本与路径）。Gradle 必须用 JDK 21。不下载任何新工具。
- **禁止涌现系统**：不得新建共享状态管理层、路由框架、通用"BaseChannel"抽象、目录重组。每个通道类独立、模式相同即可（模式在 RULEBOOK §2）。
- **50 行门**：任一"本应机械"的步骤若需 >50 行净新增业务逻辑或新状态机 → 停，进 GAP 重新设计（配对页已预先列为 GAP-6 高风险）。
- **回滚**：P4 前任何时刻 Launcher 改回 `MainActivity` 即回到 Compose 全套；Flutter 侧新 Tab 未接入前不影响已上线首页。P4 时如需保留回滚，先做真机验收，再拆；拆分两步（先移 Manifest 注册、观察一个版本后再删源码）。

## 11. 子代理派发方案（本轮授权范围）

- 模型：**Fable 5.1**（`claude-fable-5-1`），implementer 角色；每个分片一个子代理；分片之间不共享文件、不通信；输入 = 本 SPEC + RULEBOOK + GAP + 自己分片的 items 卡片 + 只读参考文件清单。
- 协调者（主 Agent）：串行做接线、全量验证、漂移自审、账本与交接；不把接线委派出去。
- 用户已在本轮明确指令"写计划书 → 拆任务 → 以子代理派发执行"，视为对 Wave 1–3 的执行授权；**Wave 4（P4 拆旧）另行请求授权**。

## 12. 待批字段

无（Wave 1–3）。P4 已由用户于 2026-09-05 显式授权（"直接进 P4 拆旧，按计划书两步拆、先不等真机"），见 §13。

## 13. P4 拆旧（v2 新增；用户已授权；两步）

**范围扩展（仅 P4）**：`manager/src/main/AndroidManifest.xml`、`legacy/LegacyIsNotSupportedActivity.kt`、`adb/AdbPairingService.kt`（仅常量引用行）、`home/HomeActions.kt`（删 4 个开旧页方法 + 常量引用）、`flutter/FlutterHostActivity.kt`（加常量、删 4 条路由、`APPLICATION_PREFERENCES` 处理）、以及第二步的删除清单。其余 OUT OF SCOPE 不变。

| 步 | 内容 | 门禁 |
|---|---|---|
| **P4-1 断入口**（本次执行） | Manifest 删 `.MainActivity` / `.management.AppsManagementActivity` / `.adb.AdbPairingTutorialActivity` / `.shell.ShellTutorialActivity` / `.settings.SettingsActivity` 五条注册；`APPLICATION_PREFERENCES` intent-filter 挂到 `.flutter.FlutterHostActivity`，宿主 `getInitialRoute()` 见到该 action 返回 `/tab/3`；`LegacyIsNotSupportedActivity` 改启动 `FlutterHostActivity`；`EXTRA_START_SERVICE_VIA_WADB` 常量迁到 `FlutterHostActivity.companion`（字符串值不变），`AdbPairingService` / `HomeActions` 改引用；`HomeActions` 删 `openApps / openTerminal / openSettings / openPairing`，`FlutterHostActivity` 删对应 4 条路由（Dart 侧 `AppShell` 永远传回调，这四条已无调用；`HomeScreen` 的 null 回退路径遇 `notImplemented` 走 `unavailable`，无害）。源码文件**不删**——回滚 = 恢复 Manifest 一行 | 编译 + 50 单测全绿；`flutter build aar --no-profile` + `:manager:assembleDebug` 出包 |
| **P4-2 删源码**（观察一个版本后，或用户再次点头） | 删 `home/{HomeActivity,HomeComposeScreen,LibrarySkinHome,HomeViewModel}.kt`、`MainActivity.kt`、`management/{AppsManagementActivity,AppsManagementComposeScreen,AppsViewModel}.kt`、`settings/{SettingsActivity,SettingsComposeScreen}.kt`、`shell/{ShellTutorialActivity,ShellTutorialComposeScreen}.kt`、`adb/{AdbPairingTutorialActivity,AdbPairingTutorialComposeScreen}.kt`、`ui/widget/DotMatrixFace.kt`；**保留** `ui/theme/ShizukuComposeTheme.kt`、`ui/theme/Type.kt`（授权弹窗与 legacy 提示页仍用）、`management/GrantStates.kt`（`AppsChannel` / `HomeActions` 用）、`app/AppActivity.kt`（`RequestPermissionActivity` 等的基类）。删前用 `rg` 逐文件核查零引用 | 编译 + 单测全绿；Debug/Release 各打一包；第三方 API 客户端仍能连（真机） |

回滚：P4-1 后回到 Compose = 在 Manifest 恢复 `.MainActivity` 注册并把 LAUNCHER intent-filter 移回；P4-2 后回滚 = `git revert`。
