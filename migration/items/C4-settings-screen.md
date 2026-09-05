# C4 · settings_screen.dart

- 分片：C settings（Wave 2）
- 目标：`manager_flutter/lib/settings/settings_screen.dart`
- 契约：RULEBOOK §3、§4、§10；SPEC §6 行为契约"设置 Tab"
- 只读参考：`settings/SettingsComposeScreen.kt`（三组卡片、每项标题/副标题、四种对话框）、`manager_flutter/lib/home/home_screen.dart`（`_GlassAlert`、`_showLanguage` 的选择弹层写法）、`manager_flutter/lib/widgets/glass_choice_dialog.dart`（Wave 1 产物）

## 信息结构（对齐 Compose 三组）

1. 页头标题 `copy.title`。
2. **启动**（`copy.startup`）卡：Root 开机 `Switch`、无线开机 `Switch`、自动配对 `Switch`、Watchdog `Switch`、TCP 端口（点击 → 数字输入弹层，空 = 清除）。
3. **语言**（`copy.language`）卡：语言（点击 → `GlassChoiceDialog` 单选 `locales`）、翻译贡献者（静态行，点击无动作——与 Compose 一致）、参与翻译（点击 → `openTranslation`）。
4. **界面**（`copy.userInterface`）卡：深色模式（点击 → 单选 `nightModeOptions`）、纯黑夜间 `Switch`、系统取色 `Switch`。
5. 对话框：缺权限（`needGrant`）→ 标题 `permissionMissing`、正文 `wirelessBootPermissionTooltip` + 换行 + `grantCmd`、按钮 `manual`（调 `openWirelessGuide` 并 `copyText(grantCmd)`）/ `cancel`；缺通知监听（`needNotificationAccess`）→ 按钮 `ok`（调 `openNotificationAccess`）/ `cancel`。
6. 所有写操作直接用返回的快照 `setState`；宿主 recreate 由 Kotlin 负责，Dart 不处理。

## 完成判据

`flutter analyze` 无问题；C5 全绿。
