# Shizuku → OneIMS Ultra Flutter 皮肤变身方案

日期：2026-08-17  
对象：`E:\GQ\Ultra\Shizuku`（`asrtroh-netizen/shizuku` @ `231d468`）  
皮肤真源：`E:\GQ\Ultra\OneIms Ultra\oneims_flutter`  
本文件只定方案，不改运行时代码。

## 一句话结论

**用 Flutter 重做管家界面，视觉逐字节复用 OneIMS Ultra 的黑白 + 液态玻璃；提权服务、配对、授权弹窗全部留在现有 Kotlin。包名 `moe.shizuku.privileged.api` 不准改。**

打开 App 要一眼是 One 家族；第三方 App 调 Shizuku API 的路径不能断。

---

## 为什么不能「整个改成 Flutter」

Shizuku 能干活，靠的不是界面：

| 必须留原生 | 原因 |
|---|---|
| `server/` + `starter/` + `shell/` | binder 服务、native starter、`rish` |
| `api/` 子模块 | 客户端 SDK，别的 App 编译期依赖 |
| `AdbPairingService` / 通知监听 | 无线配对、通知栏一键启动 |
| `WatchdogService` / Boot 系列 Receiver | 开机自启、断线修复 |
| `RequestPermissionActivity` 等 | 系统级授权 Intent，必须是原生 Activity |

Flutter 只替换 **人看得见的管家壳**。这和 OneIMS Ultra 已经跑通的切法相同：`FlutterHostActivity` 画皮，`AppShellController` 摸真引擎。

---

## 现状（本轮核对过）

### Shizuku 现在

- 管家已是 Jetpack Compose：`LibrarySkinHome`（Hero → 无线调试 → 2×2 快捷 → 开机卡 → 检查更新；右上角 `Lang` + 日月滑块）。
- 色板仍是 **OneIms Lite 蓝** `#0B57D0`（`ShizukuComposeTheme.kt`），不是 Ultra 的「全局蓝换白」。
- 没有液态玻璃 token（圆角 18 / 发丝边 / Montserrat / StatusHero 三态）。
- 2026-07-19 那次「Lang / 日月 / GitHub 更新」是 Compose 增量，不是 Flutter 变身。

### OneIMS Ultra 皮肤真源

- Flutter：`oneims_flutter/lib/theme/app_theme.dart` → `Glass.apply` + `oneBlackWhiteLight/Dark` + Montserrat。
- 质感：`onetools/glass.dart`（卡片圆角 18、页边距 16、卡间距 14、描边 0.8、面板模糊 18 / 栏模糊 24）。
- 门面：`OneStatusHero` 三态（未就绪红卡 / 忙 / 就绪白卡）+ 点阵脸，状态不只靠颜色。
- 壳：`AppShell` + `GlassDock`；嵌入：`settings.gradle.kts` `include_flutter.groovy` + `implementation(project(":flutter"))`。
- 家族里这条提权通道已经叫 **OneKuku**（文案与独立包 `OneIms-Ultra-OneKuku-standalone`）。本仓库是「独立 Shizuku 管家」，皮要对齐它，业务不要把 IMS 的 SIM / 能力 / 诊断搬过来。

---

## 方案矩阵

| 方案 | 做法 | 结论 |
|---|---|---|
| **A. Flutter add-to-app（默认）** | 在现有 Gradle 工程里加 Flutter 模块；Launcher 改 `FlutterHostActivity`；通道桥到现有 `HomeViewModel` / Settings / 授权列表 | **选它。** 与 Ultra 同源，可分期，可回滚。 |
| B. 只换 Compose token | 把 `#0B57D0` 改成黑白，手搓玻璃 | 快，但你点名 Flutter，且以后两套皮会漂。不选。 |
| C. 纯 Dart 重写含 server | 用 Dart 再实现 binder / ADB / root | 废掉 Shizuku。禁止。 |
| D. 不再维护独立仓库，改用 Ultra 的 OneKuku flavor | 用户去开 OneIMS Ultra | 产品形态不同；你刚把独立库拉回来，不是这次目标。 |

回滚：Launcher 改回 `MainActivity`，Flutter 模块可留着不编进 flavor。

---

## 视觉系统（只抄 Ultra，不发明第四主色）

产品内页：一致性优先。气质一句话——**冷静黑白工具，液态玻璃面，办妥了就收声。**

### L1 基础 token（从 Ultra 原样搬）

| 槽位 | 值 | 来源 |
|---|---|---|
| 亮色背景 / 墨色 | `#F9F9FF` / `#1A1B20` | `one_palette.dart` |
| 暗色背景 / 内容 | `#111318` / `#E2E2E9` | 同上 |
| 强调 | 亮色近黑、暗色纯白；禁止再出现 `#0B57D0` | 「全局蓝换白」 |
| 报错 | 只用 `error` / `errorContainer` | Hero 未就绪 |
| 字体 | Montserrat Regular（中文回落系统字体） | `AppTheme` / OneIms `Theme.kt` |
| 卡片圆角 | 18 | `Glass.radiusCard` |
| 门面圆角 | 22（`radiusCard+4`） | `Glass.radiusHero` |
| 页边距 / 卡间距 / 卡内边距 | 16 / 14 / 16 | `pageMargin` / `cardGap` / `padCard` |
| 描边 | 0.8 | `strokeWidth` |
| 底栏 | 高 64、胶囊圆角、底隙 8 | `GlassDock` |
| 主题滑块 | 220ms 圆钮位移 | 现有 `ThemeSlideToggle`，用 Flutter 重做时时长对齐 |

页面禁止私写 hex。动态色：Ultra Flutter 侧配色身份走黑白板，`Glass.apply` 只改通透度；本项目第一期 **关掉 Material You 动态取色**，避免又漂回彩色，和 Ultra 管家壳一致。

### L2 组件（直接复用文件，不要重画一套）

从 `oneims_flutter/lib/onetools/` **复制**（不要改数值）：

- `glass.dart` / `one_palette.dart` / `one_status_hero.dart` / `dot_matrix_face.dart`
- `nav/glass_dock.dart` / `widgets/glass_choice_dialog.dart` / `theme/app_theme.dart`

Shizuku 专用组件只做编排：

- `ShizukuStatusHero` = `OneStatusHero` 填三态文案（服务未运行 / 正在启动 / 已运行）
- **点阵脸是 P0 硬项，不是装饰。** 就绪必须 `DotMatrixMood.smile`，未运行/启动中必须 `frown`。形状本身编码状态（7×7 点阵），禁止用 emoji、禁止只靠红绿色。缺脸 = P0 验收失败。
- 真源文件：`oneims_flutter/lib/onetools/dot_matrix_face.dart`（已拷到 `manager_flutter/lib/onetools/dot_matrix_face.dart`，hash 必须保持一致）
- 现皮预览：Compose `LibraryHeroCard` 标题行右侧已挂同一套矩阵（`DotMatrixFace.kt`），Flutter 接线后删 Compose 镜像、只留 Dart 真源
- `WirelessGlassCard` / `QuickTileGrid` / `BootGlassCard` / `LangChip` + `SunMoonToggle`

### L3 页面信息架构（不要 5 个 IMS Tab）

底栏 **4 项**，不要 Capabilities / Diagnostics / Exclusive：

1. **首页** — 服务总控（首屏预算：Hero 就是「Shizuku 跑没跑」）
2. **应用** — 授权列表（现 `AppsManagementActivity`）
3. **终端** — `rish` 教程入口（现 `ShellTutorialActivity`）
4. **设置** — 主题 / 语言 / 开机 / Watchdog / TCP 端口 / 更新

右上角保留你已经习惯的 **`Lang` 胶囊 + 日月滑块**（现 `LibraryTopLangThemeCapsules`）。

授权系统弹窗、无线配对系统通知、Starter 前台服务界面：**不进 Flutter**，原生 Activity/Service 继续接。

---

## 首页首屏（对现 `LibrarySkinHome` 的一一映射）

| 现在 Compose | Flutter 变身后 |
|---|---|
| `LibraryHeroCard(running)` | `OneStatusHero`：未运行=inactive 红卡+主按钮；启动中=activating；运行中=ready 收声 |
| `WirelessStartCard` | 玻璃卡：指南 / 配对 / 启动无线 ADB |
| 2×2 快捷（应用、终端、Root、ADB 命令） | 玻璃磁贴，点开仍用 `glass_choice_dialog` 确认 |
| `BootStartCard` | 玻璃卡 → 底 sheet：Root 开机 / 无线开机 / Watchdog（互斥逻辑保持） |
| ADB 权限不足警告 | `errorContainer` 玻璃警告条，点进帮助 |
| 检查更新 | 底栏右下或设置内同一条 GitHub Release 路径（已有 `UpdateChecker`） |
| Lang + 日月 | 页头右侧，行为不变 |

主按钮语义（对齐 Ultra `primaryAction`）：未运行 → 按设备选 Root 启动或无线启动；已运行 → 不占 Hero 按钮。

---

## 工程切法（文件级）

建议模块名：`manager_flutter/`（与 Ultra 的 `oneims_flutter/` 同位）。

1. `flutter create --template module manager_flutter`
2. 复制 Ultra `onetools/` + `theme/app_theme.dart` + `nav/glass_dock.dart` + Montserrat 字体声明
3. **P0 实际接线（相对 Ultra 的源码 `include_flutter` 有偏差）**：本仓库 AGP **8.10.1** + Flutter **3.44** 走 `include_flutter.groovy` 会在宿主 `afterEvaluate` 里 `check(androidAppExtension != null)` 直接 `Check failed`。P0 改走官方 **`flutter build aar --no-profile`**，宿主只消费 Maven AAR。
   - 产物：`manager_flutter/build/host/outputs/repo`
   - `settings.gradle`：`download.flutter.io` + 上述本地 repo（`PREFER_SETTINGS`）
   - `manager/build.gradle`：`debugImplementation 'moe.shizuku.manager_flutter:flutter_debug:1.0'` / `releaseImplementation '...flutter_release:1.0'`
   - 模块 `.android` wrapper 钉本机已有的 **Gradle 9.5.0-bin**（模板默认 9.1.0-all 在本机下不下来）
4. Release 仍应 strip 多余 ABI 的 `libflutter.so`（P0 debug 三 ABI，包体约 91MB，属预期；正式包再收）
5. 新增 `FlutterHostActivity`（抄 `FlutterHostActivity.kt` 的 `replyAsync`：IO 干活、主线程回、错误码带方法名）
6. Launcher 指向它；`MainActivity` 暂时留着作回滚
7. 通道（JSON 字符串，与 Ultra 同一约定）：

| 通道 | 方法 | 原生真源 |
|---|---|---|
| `shizuku/app` + `shizuku/app/events` | `getShellState`；推 running / grantedCount | `HomeViewModel` / `ServiceStatus` |
| `shizuku/home` | `getState` / `startRoot` / `restartRoot` / `startWireless` / `openPairing` / `openWirelessGuide` / `setBootRoot` / `setBootWireless` / `setWatchdog` / `checkUpdate` | 现有 Home / Watchdog / UpdateChecker |
| `shizuku/apps` | `getState` / 授权相关只读+跳原生或后续迁 | `AppsManagementActivity` |
| `shizuku/settings` | 主题、语言、TCP 端口、动态色关 | `ShizukuSettings` / `ThemeHelper` |

无宿主（`flutter run` 桌面预览）时读空 Map、写 `unavailable`，与 Ultra `HomeChannel` 相同，方便先画皮。

**包名 / applicationId：保持 `moe.shizuku.privileged.api`。** 改包名 = 所有已授权客户端失效。显示名可以写成 OneKuku，那是标签不是身份。

---

## 分期与验收

| 期 | 范围 | 完成标准 |
|---|---|---|
| P0 皮 | 模块能编进 APK；首页只读 Hero；黑白玻璃 + Montserrat；**门面卡右侧点阵脸（笑/哭）必现** | **已落地（2026-08-17）**：Launcher=`FlutterHostActivity`；包名仍 `moe.shizuku.privileged.api`。 |
| P1 能启动 | 原 Compose 首页**全部动作**换皮：Lang/日月、无线指南/配对/启动、2×2、开机三开关、ADB 受限条、检查更新；Kotlin `HomeActions` 为唯一逻辑真源 | **已落地（2026-08-17）**：`shizuku/home` + `shizuku/home/events`；真机点阵笑/哭与无线/开机 **仍待装包确认**。 |
| P2 列表与设置 | 授权应用列表、设置页、语言 | 授权数与现 Compose 一致 |
| P3 教程壳 | 无线指南、终端教程用 Flutter 壳，系统配对服务仍原生 | 通知一键启动不回归 |
| P4 拆旧 | 删除 `LibrarySkinHome` 等 Compose 页 | Debug/Release 各打一包，API 客户端仍能连 |

每期可回滚：切回 `MainActivity`。

---

## 明确不做

- 不把 OneIMS 的 SIM 选择、能力页、诊断、激活码搬进这个独立管家。
- 不改 `api/` 对外契约、不改 binder 协议。
- 不为「更潮」加第二套主色或渐变紫。
- 不给每张列表卡片套 `BackdropFilter`（Ultra 已取舍：真模糊只留给门面/底栏）。

---

## 风险

- Flutter 引擎会增大 APK：必须沿用 Ultra 的 strip；只编需要的 ABI。
- 授权 Activity 若误迁到 Flutter，系统 Intent 会断。P0 就把它列进「禁迁」。
- TV/遥控器：现 fork 有 Leanback 适配；第一期以手机为准，TV 放 P3 后。
- 网络环境拉 Flutter SDK / `download.flutter.io` 可能慢，仓库侧已有阿里云镜像先例（Ultra `settings.gradle.kts`）。

---

## 建议的下一步（需你点头再写代码）

默认开工顺序：**P0 → P1**。显示名可用 OneKuku，包名不动。
