# 2026-07-17 · 旧 WiFi 自启 + Hero 二态

## 自启

- `isWifiClientConnected`：扫 `allNetworks` 任一 Wi‑Fi，不再只认 `activeNetwork`（蜂窝默认路由时旧 WiFi 仍可自启）
- `AdbStartWorker`：已连 WiFi 跳过长等待；`ExistingWorkPolicy.KEEP` 避免 REPLACE 掐死在途启动
- `ShizukuReceiverStarter`：仅非 stale 的 STARTING 才挡自启

## UI

- Hero 二态：未激活 / 已就绪（`Shizuku` + pill `Active`）
- 去掉与下方无线启动重复的激活按钮
- 步进条改为未激活 → 已就绪

## 验证

- `assembleDebug` PASS；真机 adb 离线 → 打开 APK 目录自装
