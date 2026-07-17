# OneIms Lite · thedjchi/Shizuku 换皮与修缮

基于 [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) 本地分叉。

## 修缮：开机已连 Wi‑Fi 直接自启

- **问题**：`AdbStartWorker` 在需要 Wi‑Fi 时始终加 WorkManager `UNMETERED` 约束；开机时若配对过的 Wi‑Fi **已经连上**，部分机型会卡住不跑 worker。
- **对齐 OneKuku**：`hasPairedOnce` + 旧网 STA 已起 → 直接走静默激活，不空等网络约束。
- **改动**：
  - `EnvironmentUtils.isWifiClientConnected()`：检测当前是否已有 Wi‑Fi STA。
  - `AdbStartWorker.enqueue()`：仅当「需要 Wi‑Fi 且当前未连上」才加 `UNMETERED`；已连上则立即调度。
- **保留**：Tcp mode（`settings_tcp_mode` / `EnvironmentUtils.isWifiRequired` 逻辑不删）。

## 换皮：对齐 OneIMS / OneIms Lite

- 主色：`#0B57D0` / 暗色 `#A9C7FF`（对齐 OneIMS `Theme.kt`）
- 首页卡片圆角 20dp、内边距 20dp、间距贴近 OneImsTokens
- 状态卡（`home_server_status`）加最小高度与标题加粗，贴近首页状态卡层级
- 显示名：`OneIms Lite`（包名仍为上游 `moe.shizuku.manager`，避免破坏 Shizuku API 契约）
