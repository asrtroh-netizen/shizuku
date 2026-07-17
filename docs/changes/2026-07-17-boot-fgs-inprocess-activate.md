# 2026-07-17 · 开机自启：FGS 内完成激活

## 根因

开机 `BootAdbStartService` 只把任务丢给 WorkManager；电池优化未豁免时 OEM 冻结 Worker → 一直「未激活」。OneKuku 是整段激活在 FGS 内完成。

## 修复

- 新增 `WirelessAdbActivation`：mDNS + AdbStarter + waitForBinder
- `BootAdbStartService` 在前台服务内直接 `activate()`；失败才 REPLACE enqueue Worker
- 晚到 Wi‑Fi / App 冷启 nudge 同样走 FGS
- 截图底栏「禁用电池优化」仍需点一次「修复」（配对开自启不会自动弹系统豁免）

## 验收

1. 装 `shizuku-V15.0-debug.apk`（commit `9cc9474`）
2. 点底栏「修复」豁免电池优化
3. 重启 → 解锁后应自动 Active
