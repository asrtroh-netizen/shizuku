# B2 · apps_models.dart

- 分片：B apps（Wave 1 试点）
- 目标：`manager_flutter/lib/apps/apps_models.dart`
- 契约：RULEBOOK §2、§9
- 参考：`manager_flutter/lib/home/home_models.dart`（`fromJson` + `pick` + `fallback` 模式）

## 做什么

- `AppsCopy`（字段 = §9 copy key：`title, empty, requiresRoot, adbLimitedTitle, adbLimitedMessage, notRunning, back, ok`）+ `static const fallback`（英文）+ `fromJson(Map?)`。
- `AppRow`（`packageName, uid, userId, label, requiresRoot, granted`）+ `fromJson`，非法/缺失字段用默认值（`uid`/`userId` 缺 → -1，字符串缺 → `''`，bool 缺 → false）。
- `AppsSnapshot`（`running, adbLimited, apps: List<AppRow>, copy`）+ `static empty` + `fromJson(Map)`（空 Map → `empty`；`apps` 非 List → `[]`）+ `String get key(AppRow)` 或顶层 `appKey(row) => '${packageName}#${uid}'`。

## 完成判据

`flutter analyze` 无问题；被 B4/B5 使用。
