# Changes（相对 thedjchi/Shizuku）

## asrtroh boot + UI（13.7.2 · 对齐 OneKuku 编排）

- **前台服务开机路径** `BootAdbStartService`（对标 OneKukuBootRestoreService）：`BOOT_COMPLETED` + `goAsync` → FGS → 等解锁 →（无线才）等 Wi‑Fi → 写回 `adb_wifi` 并短等 → `ShizukuReceiverStarter`
- **`NETWORK_STATE_CHANGED`**：Wi‑Fi 晚到时用 WorkManager 再 nudge（无 FGS 白名单）
- **TCP 保留**：`isWifiRequired()==false` 时跳过一切 Wi‑Fi 门闩
- 其余：状态卡小方块、空授权文案、开机联动 Watchdog（见 13.7.1）

## asrtroh boot + UI（13.7.1）

- worker 内 waitForWifi、WifiReadyMonitor、状态卡 2×2、空授权文案

## 更早

- UI：主色 `#0B57D0` / `#A9C7FF`，卡片圆角 20dp
- 显示名：Shizuku（与 OneIms 产品线分离）
