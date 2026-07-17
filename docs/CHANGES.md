# Changes（相对 thedjchi/Shizuku）

## asrtroh boot + UI（本轮）

- **开机自启（OneKuku 对齐）**
  - `EnvironmentUtils.waitForWifiClient()`：进程内主动等待已记住的 Wi‑Fi STA（最长约 45s）
  - `AdbStartWorker`：不再依赖 WorkManager `UNMETERED` 门闩；进 worker 后再等 Wi‑Fi；mDNS 超时加长到 30s
  - `WifiReadyMonitor`：Wi‑Fi 晚到时用 `NetworkCallback` 再触发启动（修 BOOT_COMPLETED 早于连网）
  - 打开「开机自启」时自动打开 Watchdog，降低被杀后起不来的概率
- **授权列表为空**
  - 首页应用管理卡在服务未运行 / binder 不可用时给出明确文案（不再像「0 个授权」）
- **状态卡 UI（学 OneIms）**
  - 2×2 小方块信息格（模式 / 版本 / UID / Wi‑Fi）
  - 点按弹出详情对话框（检查式详情，不写日志）
- 显示名仍为 **Shizuku**（与 OneIms 产品线分离）

## 更早

- UI：主色 `#0B57D0` / `#A9C7FF`，卡片圆角 20dp
- 显示名：Shizuku（与 OneIms 产品线分离）
