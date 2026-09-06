# 重构计划书 R2 · 退役 Compose 后的收敛（去重、死代码、真源回同步）

版本：v1.2（2026-09-06；v1 → v1.1：Wave 1 后新增 I-5 弹窗 bug 修复、I-6 应用占位卡统一；`openSettings` 合并后 catch 取 `Exception`，见 OBSERVATIONS。v1.1 → v1.2：§4.4 措辞修正——`HomeActions` 允许引用 `flutter` 包内共享工具，只禁止依赖宿主；I-6 补配对页占位；`SettingsChannel.setBool` 开机项统一由 `applyBootToggle` 结果落盘）  
状态：**冻结**（SCOPE / 判据 / 行为契约 / 依赖锁定执行期不得改软；改动 = 升版本 + 旧证据失效）  
上一轮：`migration/MIGRATION_SPEC.md` v2（P2–P4，已发布 V15.2.0）  
基线 commit：`91b9f3579ca8e48f0c4a4ca4ef8b07997168013b`（`main` = `origin/main`，工作树干净，仅未跟踪 `.xj-cursor/`）  
规则手册：`migration/RULEBOOK.md` v1.3 继续有效，本轮增补见 `migration/r2/RULEBOOK.md`

---

## 0. 一句话

上一轮为了"新旧并存、随时回滚"刻意允许了重复代码；旧 Compose 现在已经删光，这一轮把重复收回一份、把没人用的代码和依赖删掉、把首页拆成和其它页面一样的结构、把皮肤组件与 Ultra 真源对齐——**用户看到的行为不变**，除了 §6 明确列出的四处小改进。

## 1. 类型与判据

- **结构保持型重构**：每一项都要能回答"去掉这个改动任务是否失败"（替代测试）；顺手优化一律进 `OBSERVATIONS.md`。
- **裁判**（基线已全绿，本轮每批必须仍全绿）：
  - `manager_flutter/`：`flutter analyze` → No issues；`flutter test` → **56** passed（基线）+ 新增。
  - 仓根：`gradlew :manager:testDebugUnitTest --offline` → BUILD SUCCESSFUL，**50** tests（基线）+ 新增。
  - 收尾：`gradlew :manager:assembleDebug :manager:assembleRelease` 出包；`aapt2 dump badging` 包名 / Launcher 不变。
- **可抓坏验证**：每个新增纯函数测试至少含一个"改错即红"的断言；提取/搬移逻辑时先跑旧测试、搬完再跑，中途不改测试期望。

## 2. IN SCOPE（按分片 = 文件所有权；分片之间零交集）

| 分片 | 新建 | 修改 | 删除 |
|---|---|---|---|
| **K1 home-channel**（Kotlin） | `manager/src/main/java/moe/shizuku/manager/flutter/HomeChannel.kt`、`manager/src/main/java/moe/shizuku/manager/home/HomeState.kt`、`manager/src/test/java/moe/shizuku/manager/home/HomeStateTest.kt` | `manager/src/main/java/moe/shizuku/manager/home/HomeActions.kt` | — |
| **K2 shared-helpers**（Kotlin） | `.../flutter/NotificationListenerAccess.kt`、`.../flutter/LocaleLabels.kt`、`.../flutter/BootPrefs.kt`、`.../flutter/HtmlText.kt`、`manager/src/test/java/moe/shizuku/manager/flutter/SharedHelpersTest.kt` | `.../flutter/SettingsChannel.kt`、`.../flutter/PairingChannel.kt`、`.../flutter/AppsChannel.kt`、`.../flutter/TerminalChannel.kt`（只改为调用共享实现，行为不变） | — |
| **K3 dead-code**（Kotlin / Gradle） | — | `manager/build.gradle`（仅删依赖行） | `manager/src/main/java/moe/shizuku/manager/ktx/RecyclerView.kt`、`.../widget/VerticalPaddingDecoration.java`、`.../widget/CheckedImageView.java` |
| **D1 home-split**（Dart） | `manager_flutter/lib/widgets/glass_alert.dart`、`manager_flutter/lib/widgets/glass_notice_card.dart`、`manager_flutter/lib/home/home_header.dart`、`manager_flutter/lib/home/home_cards.dart`、`manager_flutter/lib/home/home_dialogs.dart`、`manager_flutter/test/home_screen_test.dart` | `manager_flutter/lib/home/home_screen.dart`、`manager_flutter/test/home_hero_face_test.dart`（只补必填回调） | — |
| **D3 onetools-sync**（Dart） | — | `manager_flutter/lib/onetools/one_status_hero.dart`、`manager_flutter/lib/onetools/dot_matrix_face.dart` | — |
| **D2 adopt-shared**（Dart，Wave 2） | — | `manager_flutter/lib/apps/apps_screen.dart`、`manager_flutter/lib/settings/settings_screen.dart`、`manager_flutter/lib/terminal/terminal_screen.dart`、`manager_flutter/lib/pairing/pairing_screen.dart`、对应 4 个测试文件（只改定位不改期望） | — |
| **K4 home-adopt**（Kotlin，Wave 2） | `manager/src/main/res/values{,-zh-rCN,-zh-rTW,-ja,-ko}/strings.xml` 各加 2 个 key | `home/HomeActions.kt`（改用 `LocaleLabels` / `BootPrefs`；tab 标签换新 key）、`home/HomeStateTest.kt`（如需） | — |
| **协调者** | — | `flutter/FlutterHostActivity.kt`（委托 `HomeChannel`）、`manager_flutter/lib/nav/app_shell.dart`（去掉 `onOpenSettings`、占位卡改 `GlassNoticeCard`）、`migration/r2/**` | — |

文件范围 = 上表全部 ∪ `migration/r2/**`。表外文件被改即越界。

## 3. OUT OF SCOPE

- 一切原生提权链路（`server/ starter/ shell/ api/ common/`、`authorization/**`、`legacy/**`、`adb/AdbPairingService*`、`receiver/**`、`watchdog/**`、`starter/**`、`ShizukuApplication`、`ShizukuManagerProvider`、`ShizukuSettings`）。
- `AndroidManifest.xml`、`proguard-rules.pro`（`-keep androidx.compose.**` 与 ViewModel `clear()` 规则保留——授权弹窗仍是 Compose，`StarterActivity` 仍用 rikkax ViewModel）。
- `ui/theme/ShizukuComposeTheme.kt`、`Type.kt`、`app/AppActivity.kt`、`app/AppBarActivity.kt`（StarterActivity 基类）、`res/layout/*`（StarterActivity 用）。
- Compose 依赖本体：`compose.bom / ui / material3 / activity.compose` 保留。
- `manager_flutter/pubspec.*`；`onetools/glass.dart`、`one_palette.dart`、`liquid_glass.dart`（已与 Ultra 一致）。
- 语义统一类"改进"（Watchdog 前置条件、`WifiReadyMonitor`、`AppIconCache` 并发）——记录在 `GAP_INVENTORY.md` 待产品决定，本轮不动。
- Git 写操作由协调者在用户授权后做；分片不 commit。

## 4. 成功判据（每批）

1. §1 三个裁判全绿；新增测试：`HomeStateTest`（≥ 6 用例）、`SharedHelpersTest`（≥ 6 用例）、`home_screen_test.dart`（≥ 4 用例）。
2. 去重核验：`rg -n "fun isNotificationListenerEnabled|fun plainText|fun localeLabel|fun applyBootToggle|fun parseTcpipPort" manager/src/main/java` → 每个名字**只出现一次**（在共享文件里）；`rg -n "^class _GlassAlert" manager_flutter/lib` → 零命中。
3. 死代码核验：`rg -n "FixedAlwaysClipToPaddingEdgeEffectFactory|VerticalPaddingDecoration|CheckedImageView|runtime.livedata|icons.extended|ui.tooling" manager/` → 零命中。
4. 结构核验：`FlutterHostActivity.kt` 不再含 `MethodChannel(messenger, HOME_CHANNEL)` 内联 `when`；`home_screen.dart` ≤ 350 行；`HomeActions.kt` 不 import `FlutterHostActivity` / `HomeChannel`（切断对宿主的反向依赖；v1.2 措辞修正——引用 `flutter` 包里的共享工具 `LocaleLabels / LocaleRow / BootPrefs / applyBootToggle` 是允许的）。
5. 漂移自审：改动文件 ⊆ §2；`uniq -d` 无多分片同改文件；替代测试逐 hunk。
6. 收尾：Debug + Release 打包成功，`aapt2` 核对包名 `moe.shizuku.privileged.api`、Launcher `FlutterHostActivity`。

## 5. 验证顺序与分工

- 分片可跑：`dart analyze <自己的路径>`、`flutter test test/<自己的文件>`（Dart）；Kotlin 分片**不得**跑 Gradle（协调者串行 survey build）。
- 协调者每批：`flutter analyze` + `flutter test` 全量 → `:manager:testDebugUnitTest` → 入账 `VERIFY_LEDGER.tsv`；批红不进下一批。
- 同类错误跨文件重复 → 修 `migration/r2/RULEBOOK.md` 再重生成对应分片。

## 6. 行为契约

**必须不变**：五个通道的方法名、参数、返回 JSON 字段、错误码；所有副作用（授权切换、开机开关互斥与组件开关、Watchdog、TCP 端口、语言/夜间模式/主题、配对状态机、终端导出）；`FlutterHostActivity` 的生命周期钩子、`getInitialRoute`、`EXTRA_START_SERVICE_VIA_WADB` 值；首页全部按钮的动作；四个 Tab 的内容与弹窗文案。

**允许并已批准的显式改进**（各自单独验证）：

| # | 改进 | 理由 | 验证 |
|---|---|---|---|
| I-1 | 首页列表底部改用 `glassDockScrollPadding`，内容从底栏下透出 | 与应用/终端/设置三个 Tab 及 Ultra 视觉一致（上一轮 OBSERVATIONS） | `home_screen_test`：ListView padding.bottom ≥ Dock 高度 |
| I-2 | 首页 Lang 芯片改用 `LocaleLabels` 全表（43 条），不再对未知 tag 显示 `de` 之类原始码 | 与设置页一致；上一轮 OBSERVATIONS | `HomeStateTest`：`de` → `Deutsch` |
| I-3 | 底栏标签改用新短字符串 `nav_home` / `nav_apps`（含 zh-rCN / zh-rTW / ja / ko），英文 `Home` / `Apps` | 上一轮 GAP-2：`Application management` 太长 | `HomeStateTest` 断言 copy key；Widget 测试 fallback 仍为 `Apps` |
| I-4 | `one_status_hero.dart` 与 Ultra 真源逐字节对齐（新增可选 `secondaryActionLabel / onSecondaryAction`，首页不传） | 产品方案"皮肤真源"；本轮不启用次按钮 | `home_hero_face_test` 全绿；`Compare-Object` 与 Ultra 仅 import 差异 |
| I-5（v1.1 新增，bug 修复） | 首页「PC ADB → 查看命令」弹窗：`showDialog<String>` 与取消键 `pop(false)` 类型不符，运行时抛 `type 'bool' is not a subtype of type 'String?'`，**弹窗关不掉**（Wave 1 D1 复现，V15.2.0 已带此 bug） | 一行修复：`showDialog<Object?>`；用户可见的唯一变化是取消键能正常关闭 | `home_screen_test`：点取消后弹窗消失且无异常 |
| I-6（v1.1 新增） | 应用 Tab 的占位/空状态卡与配对页 `supported=false` 占位（SDK<R 才可达）改用共享 `GlassNoticeCard`（横排图标 + 粗体文案），与壳的占位卡统一 | 去重 §R4 的既定代价；原竖排 48px 图标 + `bodyLarge` 居中 | `apps_screen_test` / `pairing_screen_test` 定位改 `GlassNoticeCard`，期望文案不变 |

## 7. 批计划与风险

| 批 | 分片（并行子代理，Fable 5.1） | 文件数 | 风险 | 门禁 |
|---|---|---|---|---|
| Wave 1 | K1、K2、K3、D1、D3（5 个并行；文件零交集） | 新 12 / 改 10 / 删 3 | K1 中（宿主委托）、K2 低-中、K3 低、D1 中（首页拆分）、D3 低 | 协调者接线 `FlutterHostActivity` + `app_shell` → 全量裁判 |
| Wave 2 | D2、K4（2 个并行） | 改 ~10 / 新 5 strings | 低 | 全量裁判 + 去重核验 §4.2 |
| 收尾 | 协调者 | — | — | Debug/Release 出包 + badging；交接 |

## 8. 状态根、工作项、依赖锁定

- `<STATE_ROOT>` = `migration/r2/`；工作项 = `migration/r2/items/<ID>.md`；`MIGRATION_MANIFEST.tsv` 的 target = 该项的主要产出文件（新建）或 `migration/r2/receipts/<ID>.txt`（修改/删除类，由协调者在验证后写入哈希收据）。
- `translated` = target 落盘；`verified` = 当前哈希 + 全绿证据入 `VERIFY_LEDGER.tsv`。
- **依赖锁定**：`pubspec.*` 不改；`manager/build.gradle` 只允许**删除**§2 列出的依赖行，不允许新增或改版本；`gradle/libs.versions.toml` 不改（残留的别名不引用无害）。`DEPENDENCY_ALLOWLIST.txt` = `manager/build.gradle`。
- **禁止涌现系统**：本轮明确批准的共享文件只有 §2 列的 `NotificationListenerAccess / LocaleLabels / BootPrefs / HtmlText / glass_alert / glass_notice_card`；不得再抽 `BaseChannel`、`BaseScreen`、状态管理层、路由框架。

## 9. 回滚

每个分片的改动都是纯代码搬移/删除，`git checkout -- <路径>` 或 `git revert` 即可；没有数据格式、通道协议或 Manifest 变化。

## 10. 子代理派发

- 模型：**Fable 5.1**（`claude-fable-5-1`）；每个分片一个子代理，互不通信，只共享 SPEC + RULEBOOK + 自己的 items 卡片。
- 协调者（主 Agent）负责接线、全量验证、漂移自审、账本；不委派。
- 用户已在本轮明确指令"重写计划书 → 拆任务 → 以子代理派发执行"，视为对 Wave 1–2 的执行授权；提交与发布另行请求。
