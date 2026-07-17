# 2026-07-17 · Hero 去掉休眠 · 三态卡

## 需求

Shizuku 不休眠；四态框去掉「休眠」；存活按既有逻辑（Watchdog / 开机 / binder）。

## 改动

- `HeroState`：删 `SLEEPING`；Running → 一律 `READY`
- 步进条：4 点 → 3 点（未激活 / 激活中 / 就绪）
- 删除 sleeping 文案资源
- **未改**：`WatchdogService`、开机自启、binder 唤醒

## 验证

- `:manager:assembleDebug` + 真机安装
- 关 Watchdog 时 Running 仍显示「就绪」，步进仅 3 格
