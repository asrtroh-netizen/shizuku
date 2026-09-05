# A5 · app_shell_test.dart

- 分片：A shell-nav（Wave 1 试点）
- 目标：`manager_flutter/test/app_shell_test.dart`
- 参考：`manager_flutter/test/widget_test.dart`、`test/home_hero_face_test.dart`（现有打桩方式）

## 必须覆盖（RULEBOOK §7.1）

1. 无宿主（通道抛 `MissingPluginException`）：`AppShell` 能渲染，底栏有 4 个 `NavigationDestination`，标签为 fallback `Shizuku / Apps / Terminal / Settings`，首页 `OneStatusHero` 在。
2. 点第 2 个目的地 → `IndexedStack.index == 1`，占位 Tab 出现；再点回首页 → `HomeScreen` 仍是同一实例（保活）。
3. 首页快捷磁贴"应用"（`copy.appsTitle` 文本或对应 Tile）点按后 Tab 切到 1，而不是调用 `openApps` 方法（用 mock handler 记录调用列表断言 `openApps` 未被调用）。
4. 坏输入：`getState` 返回 `copy` 中 `tabApps` 为数字 `123` 时，标签回落到 fallback `'Apps'`（不崩）。

## 完成判据

`flutter test test/app_shell_test.dart` 全绿；把第 4 条的 fallback 断言改成期望 `'123'` 必须红（自证裁判能抓坏，验证后改回）。
