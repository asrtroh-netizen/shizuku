# D1 · 首页拆分 + 共享弹窗/提示卡 + 删死回退

- 分片：D1（Wave 1）· 模型 Fable 5.1
- 目标（manifest target）：`manager_flutter/lib/widgets/glass_alert.dart`
- 新建：上者 + `manager_flutter/lib/widgets/glass_notice_card.dart` + `manager_flutter/lib/home/home_header.dart` + `manager_flutter/lib/home/home_cards.dart` + `manager_flutter/lib/home/home_dialogs.dart` + `manager_flutter/test/home_screen_test.dart`
- 修改：`manager_flutter/lib/home/home_screen.dart`、`manager_flutter/test/home_hero_face_test.dart`（仅给 `HomeScreen()` 补三个必填回调）
- **不碰**：`app_shell.dart`、`main.dart`、`apps/ settings/ terminal/ pairing/`（D2 在 Wave 2 采用你的组件）、`onetools/`（D3 在改 `one_status_hero.dart`——你只用它的现有 API）、`home_channel.dart`、`home_models.dart`、`widget_test.dart`、`app_shell_test.dart`
- 契约：R2 RULEBOOK §R4、§R5、§R8；v1.3 RULEBOOK §3、§4、§7

## 步骤

1. **先读**三份 `_GlassAlert`：`home/home_screen.dart`（`title/body/confirmLabel/cancelLabel/showCancel/extraLabel/monospace`）、`apps/apps_screen.dart`、`settings/settings_screen.dart`，以及三处 `showDialog(...)` 调用与返回值处理。设计 `GlassAlert`（§R4）为**并集**：任何一处现有调用都能只改构造名就等价迁移；把三处的返回值哨兵（`true/false/'extra'` 之类）写进 KDoc。本步只把 **home** 的调用迁到 `GlassAlert`（apps/settings 由 D2 做）。
2. **glass_notice_card.dart**：读 `apps/apps_screen.dart` 的 `_NoticeCard` 与 `nav/app_shell.dart` 的 `_PlaceholderTab`，做并集 `GlassNoticeCard(icon, text, title?)`（`GlassPanel` + 左图标 `colorScheme.primary` + 文案 `titleMedium w700` / `bodyMedium`）。本步不改 apps / app_shell（D2 / 协调者采用）。
3. **拆 home_screen.dart**（917 行 → 主文件 ≤ 350 行）：
   - `home_header.dart` ← `_TopCapsules → HomeTopCapsules`、`_LangChip → HomeLangChip`、`_ThemeSlide → HomeThemeSlide`（220ms 等常量不变）。
   - `home_cards.dart` ← `_ActionCard → HomeActionCard`、`_QuickGrid → HomeQuickGrid`、`_QuickTile → HomeQuickTile`、`_LimitedBanner → HomeLimitedBanner`。
   - `home_dialogs.dart` ← `_BootDialog → HomeBootDialog`（含 State）、以及 `_showLanguage` 的弹层若是独立函数则移为 `showHomeLanguageSheet(...)`。
   - 只做"移出 + 去下划线 + 加前缀 + 补 import"，每个 widget 的 build 体不改一字（允许把原本依赖 `_HomeScreenState` 私有成员的地方改为构造参数传入——这是拆分的必要代价，要在报告列出）。
4. **HomeScreen**：`onOpenApps` / `onOpenTerminal` / `onOpenPairing` 改 `required VoidCallback`；删 `onOpenSettings`；删 `_call('openApps')` / `_call('openTerminal')` / `_call('openPairing')` 三处回退（直接调回调）。`ListView.padding` 底部改 `glassDockScrollPadding(context)`，`SafeArea` → `SafeArea(bottom: false)`（改进 I-1）。其余（`_refresh/_refreshSeq/_call/_toast`、事件订阅、`didChangeAppLifecycleState`）不动。
5. **测试**：
   - `home_hero_face_test.dart`：`const HomeScreen()` → `HomeScreen(onOpenApps: () {}, onOpenTerminal: () {}, onOpenPairing: () {})`（若 `const` 无法保留就去掉 const），期望不改。
   - 新 `home_screen_test.dart`（§R8）：无宿主渲染 fallback；点应用磁贴 → 计数器 +1 且 mock handler **未**收到 `openApps`；点配对按钮（`showPair: true` 的快照）→ `onOpenPairing` 被调；`ListView` 的 `padding.bottom >= Glass.dockHeight`；`adbLimited: true` → `HomeLimitedBanner` 出现；再加一个"故意坏"自证（改错期望必红，改回变绿）。

## 允许的验证命令（`manager_flutter/` 下）

`dart analyze lib/home lib/widgets test/home_screen_test.dart test/home_hero_face_test.dart`；`flutter test test/home_screen_test.dart test/home_hero_face_test.dart`。（`widget_test.dart` / `app_shell_test.dart` 在协调者接线前会因 `onOpenSettings` 被删而报错——那是预期的，报告注明即可，不要去改它们。）

## 完成判据

`home_screen.dart` ≤ 350 行；`rg "^class _GlassAlert" manager_flutter/lib/home` 零命中；`rg "_call\('open" manager_flutter/lib/home` 零命中；上述两个测试文件全绿。
