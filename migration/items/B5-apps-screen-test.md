# B5 · apps_screen_test.dart

- 分片：B apps（Wave 1 试点）
- 目标：`manager_flutter/test/apps_screen_test.dart`
- 契约：RULEBOOK §7.1

## 必须覆盖

1. 无宿主（`MissingPluginException`）：渲染出 fallback 的 `notRunning` 文案，不崩。
2. 完整 JSON（`running: true`，2 个 app，其一 `granted: true`、`requiresRoot: true`）：两张卡、两处 `Switch`，其一 `value == true`；`requiresRoot` 文案出现一次。
3. 点第一张卡的 `Switch` → mock handler 收到 `toggle` 且参数 `{packageName, uid}` 正确；handler 返回 `result: 'adbLimited'` 的快照 → 出现 `adbLimitedTitle` 弹窗文本。
4. 坏输入：`apps` 字段是字符串 `"nope"` → 渲染空状态卡，不崩；`uid` 为字符串 `"x"` → 该项 `uid == -1` 且仍渲染 label。
5. `running: false` 且 `apps` 非空（矛盾输入）→ 只显示占位卡、不显示列表（GAP-3）。

## 完成判据

`flutter test test/apps_screen_test.dart` 全绿；把第 3 条的期望方法名改成 `'toggleX'` 必须红（验证后改回）。
