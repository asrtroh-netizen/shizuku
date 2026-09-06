# D2 · 四个页面采用 GlassAlert / GlassNoticeCard（Wave 2）

- 分片：D2（Wave 2，依赖 D1 已 verified）· 模型 Fable 5.1
- 目标（manifest target）：`migration/r2/receipts/D2-adopt-shared.txt`
- 修改：`manager_flutter/lib/apps/apps_screen.dart`、`manager_flutter/lib/settings/settings_screen.dart`、`manager_flutter/lib/terminal/terminal_screen.dart`、`manager_flutter/lib/pairing/pairing_screen.dart`；对应 `manager_flutter/test/{apps_screen,settings_screen,terminal_screen,pairing_screen}_test.dart` 只改定位方式（如 `find.byType(_GlassAlert)` → `find.byType(GlassAlert)`），期望不改
- **不碰**：`home/**`、`widgets/**`（只读）、`nav/**`
- 契约：R2 RULEBOOK §R4；v1.3 RULEBOOK §3、§7.3

## 步骤

1. 读 `lib/widgets/glass_alert.dart` 与 `glass_notice_card.dart` 的 KDoc（返回值哨兵、tone）。
2. `apps_screen.dart`：删私有 `_GlassAlert`，ADB 受限弹窗改 `GlassAlert(tone: GlassAlertTone.error, ...)`；删 `_NoticeCard`，占位/空状态改 `GlassNoticeCard`。
3. `settings_screen.dart`：删私有 `_GlassAlert`，缺权限 / 缺通知监听两个弹窗改 `GlassAlert`（按钮顺序、`manual`/`cancel`/`ok` 文案、点击后的通道调用一字不变）。`_PortDialog` 保留（只有它用）。
4. `terminal_screen.dart` / `pairing_screen.dart`：若有等价于"图标 + 文案的提示卡"或私有弹窗，改用共享组件；没有就不改，报告注明。
5. 自查：`rg -n "^class _GlassAlert|^class _NoticeCard" manager_flutter/lib` → 零命中；四个测试文件全绿；`dart analyze lib test` 无问题。
