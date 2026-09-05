# E4 · pairing_screen.dart

- 分片：E pairing（Wave 3）
- 目标：`manager_flutter/lib/pairing/pairing_screen.dart`
- 契约：RULEBOOK §3、§4、§12；SPEC §6"配对页"
- 只读参考：`adb/AdbPairingTutorialComposeScreen.kt`（步骤卡、状态提示、按钮、MIUI 提示）

## 信息结构

这是**推入页**（`Navigator.push`），有返回箭头（`GlassBar` 或 `GlassSliverAppBar` + `Icons.arrow_back`）。`initState`：拉 `getState` → 调 `start()` → 订阅 `events`；`resumed` → 重拉。按 Compose 顺序渲染：通知未开 → 卡 + 按钮 `openNotificationOptions`；通知监听未开 → 卡 + `openNotificationAccessSettings`；本地网络权限未授 → 卡 + `requestLocalNetworkPermission`；服务启动失败 → 错误卡（`errorContainer`）；全部就绪 → 引导步骤卡 + `openDeveloperOptions` 按钮；`showMiuiHint` → 提示卡。`supported == false` → 只显示"需要 Android 11+"占位（用 Compose 现有对应文案 key，如无则用 `copy.title`）。

## 完成判据

`flutter analyze` 无问题；E5 全绿。
