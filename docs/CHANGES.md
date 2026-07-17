# Changes（相对 thedjchi/Shizuku）

## v15.0.0 · 首页三态卡 + 检查网格（本机已装）

- **三态 Hero**：未激活（红）/ 激活中 / 就绪（白+Ready 铭牌）；已去掉「休眠」态（Running 一律显示就绪）
- **存活逻辑不变**：Watchdog / 开机自启 / binder 唤醒仍按既有实现
- **检查 2×2**：服务诊断 · 应用管理 · 无线启动 · 自启与看门狗（点按详情弹层）
- 产物：`shizuku-v15.0.0-release.apk`；开机自启链路两次冷重启已 PASS

## 下一发（可选）

- GitHub Release `v15.0.0` 上传
- 次要入口（电脑 adb / 自动化 / 隐身）可继续收进第二排网格

## asrtroh boot + UI（历史 · 原 13.7.2 线 · 现归入 V15.0）

- **前台服务开机路径** `BootAdbStartService`（对标 OneKukuBootRestoreService）：`BOOT_COMPLETED` + `goAsync` → FGS → 等解锁 →（无线才）等 Wi‑Fi → 写回 `adb_wifi` 并短等 → `ShizukuReceiverStarter`
- **`NETWORK_STATE_CHANGED`**：Wi‑Fi 晚到时用 WorkManager 再 nudge（无 FGS 白名单）
- **TCP 保留**：`isWifiRequired()==false` 时跳过一切 Wi‑Fi 门闩
- 其余：状态卡小方块、空授权文案、开机联动 Watchdog

## asrtroh boot + UI（历史 · 原 13.7.1 线 · 现归入 V15.0）

- worker 内 waitForWifi、WifiReadyMonitor、状态卡 2×2、空授权文案

## 更早

- UI：主色 `#0B57D0` / `#A9C7FF`，卡片圆角 20dp
- 显示名：Shizuku（与 OneIms 产品线分离）
