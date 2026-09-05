# MIGRATION_HANDOFF · 会话交接（唯一交接真源）

更新：2026-09-05  
仓库根：`E:\GQ\Ultra\Shizuku`（`main`）  
基线 commit：`a10c72af207e046bf2ed38dfc508d24bc33e1aa6`（见 `BASELINE_COMMIT`；工作树基线 = 11 个纯 CRLF/LF 差异文件 + 未跟踪 `.xj-cursor/`，见 `BASELINE_*`）  
STATE_ROOT：`migration/`  
规格版本：SPEC **v2** / RULEBOOK v1.3 / GAP v1.3；`CURRENT_BATCH_GATE` = `534e8788ef152f9dd9534cb6af920b0df3361c882c930bae3c6fbfb921ddd3ae`（即 `STATE_VERSION.tsv` 的 SHA256）

## 队列状态（与磁盘重扫交叉校验一致）

| 桶 | 数量 |
|---|---|
| pending_translate | 0 |
| pending_verify | 0 |
| verified | 29 / 29（Wave 1–3 的 27 项 + P4-1 + P4-2） |
| blocked | 0 |
| `CURRENT_BATCH_MODE` | `complete`（全部批次完成；剩余门禁 = 真机验收） |

## P4-2 删源码（已完成，用户 2026-09-05 二次确认"不等真机"）

删除 15 个旧 Compose 源文件（清单见 `items/F2-p4-delete-sources.md` 与 `receipts/F2-p4-delete-sources.txt`，含删前 OID，`git checkout -- <path>` 可恢复）；4 个通道文件里指向已删类的 KDoc 方括号链接改为普通代码引用。保留 `ShizukuComposeTheme.kt` / `Type.kt`（授权弹窗、legacy 提示页）、`GrantStates.kt`、`AppActivity.kt` / `AppBarActivity.kt`、`model/ServiceStatus.kt`（`ShizukuReceiver` 用）。  
证据（`evidence/wave4/`）：删后单测 50 全绿（`compileDebugKotlin` 已重跑）；`assembleDebug` + `assembleRelease` BUILD SUCCESSFUL → `shizuku-vV15.1.3-debug.apk` 91.7 MB、`shizuku-vV15.1.3-release.apk` 22.5 MB（R8 + 双 ABI）。  
回滚：`git checkout -- <15 个路径>` 并恢复 Manifest 注册（P4-1 收据里有改动前哈希）。

## P4-1 断入口（已完成，用户 2026-09-05 授权）

Manifest 删掉 `.MainActivity` / `AppsManagementActivity` / `AdbPairingTutorialActivity` / `ShellTutorialActivity` / `SettingsActivity` 五条注册；`APPLICATION_PREFERENCES` 挂到 `FlutterHostActivity`（`getInitialRoute()` 见到该 action 返回 `/tab/3`）；`EXTRA_START_SERVICE_VIA_WADB` 迁到 `FlutterHostActivity.companion`（值不变），`AdbPairingService` / `HomeActions` 改引用；`LegacyIsNotSupportedActivity` 改启动 `FlutterHostActivity`；`HomeActions` 删 `openApps/openTerminal/openSettings/openPairing` 与宿主对应 4 条路由。源码文件**未删**。  
证据（`evidence/wave4/`）：单测 50 全绿；`flutter build aar --no-profile` Built（debug 16.7 MB / release 6.2 MB AAR）；`:manager:assembleDebug` → `manager/build/outputs/apk/debug/shizuku-vV15.1.3-debug.apk`（91.7 MB）；合并 Manifest 中旧 Activity 零命中。收据 `receipts/F1-p4-cut-entrypoints.txt`。  
回滚：在 Manifest 恢复 `.MainActivity` 注册并把 LAUNCHER filter 移回即可（源码都还在）。

## 已完成的批次（细节见 `CHANGE_MANIFEST.md`、`VERIFY_LEDGER.tsv`、`evidence/wave*/`）

| 批 | 分片 | 子代理（Fable 5.1） | 结果 |
|---|---|---|---|
| Wave 1 试点 | A shell-nav、B apps | 2 个并行 | 15 Dart 测试 / 23 Kotlin 单测全绿；暴露 GAP-14（GlassPanel 含 BackdropFilter）→ 规则 v1.1 → B 重生成列表行 |
| Wave 2 | C settings、D terminal | 2 个并行 | 41 / 38 全绿；暴露 GAP-5 修正（FlutterActivity 是 android.app.Activity）、GAP-15（重建型 Tab 禁 EventChannel）、GAP-16（端口 10..65535、supportsStartOnBoot）→ 规则 v1.2 → D 重生成去事件通道 |
| Wave 3 | E pairing（高风险） | 1 个 | 56 / 50 全绿；补 `autoPairingEnabled`、在场门、`start`=onCreate 语义 → 规则 v1.3 |

最终裁判（`evidence/wave3/`）：`flutter analyze` No issues；`flutter test` 56 passed；`JAVA_HOME=JDK21 gradlew :manager:testDebugUnitTest --offline` BUILD SUCCESSFUL，50 tests 0 failures（基线 18 + AppsChannelTest 5 + SettingsChannelTest 15 + PairingChannelTest 12）。

## 工作树改动总览（未提交；未做任何 git 写操作）

新建 28 个源文件（另有 `migration/**` 状态目录）：`manager_flutter/lib/{nav,widgets,apps,settings,terminal,pairing}/**`（15）、`manager_flutter/lib/onetools/liquid_glass.dart`、`manager_flutter/test/{app_shell,apps_screen,settings_screen,terminal_screen,pairing_screen}_test.dart`（5）、`manager/src/main/java/moe/shizuku/manager/flutter/{Apps,Settings,Terminal,Pairing}Channel.kt`（4）、`manager/src/test/java/moe/shizuku/manager/flutter/{Apps,Settings,Pairing}ChannelTest.kt`（3）。  
修改 6 个文件（内容级）：`FlutterHostActivity.kt`（注册 4 通道、`getInitialRoute`、`onResume`、`onActivityResult`、`onRequestPermissionsResult`、`EXTRA_TAB`）、`HomeActions.kt`（+4 行 tab 标签 copy）、`main.dart`（AppShell + 初始 Tab 解析）、`home_screen.dart`（+4 个可选导航回调）、`home_models.dart`（+4 个 copy 字段）、`glass.dart`（+67 行加性 `liquid*`，与 Ultra 版 SHA256 一致）。

## 本会话决策与 edge cases

- 列表行一律主题 `Card`；`GlassPanel` 只给页面级少量面（性能 + 产品方案）。
- 应用 / 终端 / 设置 Tab 由 `AppShell` 以递增 `ValueKey` 重建取新鲜快照，**不用** EventChannel；配对页是推入页，可用 EventChannel，并有 `_pairingOpen` 防重推。
- 宿主 `FlutterActivity` 继承 `android.app.Activity`：文档树用 `startActivityForResult(0x5254)`，权限用 `requestPermissions(1001)`，通道实例保存为宿主字段。
- 设置页重建后回设置 Tab：通道写 intent extra `moe.shizuku.manager.extra.TAB`=3 → 宿主 `getInitialRoute()` 返回 `/tab/3` 并 `removeExtra` → Dart `initialTabFromRoute`。
- 声明的行为差异全部在 SPEC §6 右列（binder 死时占位卡而非 finish；toggle 非 SecurityException 走 error；终端导出在 IO 且吞异常；"手动"改复制命令；配对页仅失败卡用 errorContainer；配对按钮防重推）。

## 活跃 blockers

无代码阻塞。**真机验收未做**（本会话无设备，GAP-12）——这是进入 P4 的硬门禁。

## 下一步唯一动作

1. 用户决定是否 `git add` / `commit` 当前成果（Wave 1–3 + P4-1 + P4-2；协调者未做任何 git 写操作；工作树有 15 个 `D`、6 个 `M`（内容级）、28 个新源文件 + `migration/`）。
2. 把 `manager/build/outputs/apk/debug/shizuku-vV15.1.3-debug.apk`（或 release 包）装真机验收（SPEC §4 最终判据）：底栏 4 Tab 可切；应用 Tab 授权数与首页一致、切换授权生效、ADB 受限弹窗；设置 7 项副作用生效、改语言后回到设置 Tab、系统"应用信息 → 设置"能进设置 Tab；终端导出可用；配对流程能进系统配对且权限请求正常；第三方 Shizuku 客户端仍能连、授权弹窗仍是原生。
3. 若真机发现问题：按 `CHANGE_MANIFEST.md` 定位批次，修 RULEBOOK 再重生成对应分片（不逐文件手补）。

## 续跑方法（新会话）

读 `MIGRATION_SPEC.md` → 读本文件 → 校验 `STATE_VERSION.tsv` 各制品哈希 → 重扫 manifest 目标与 `VERIFY_LEDGER.tsv` 重建四桶队列 → 按 `CURRENT_BATCH_MODE` 继续。Gradle 必须以 JDK 21 运行（`$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot'`）。
