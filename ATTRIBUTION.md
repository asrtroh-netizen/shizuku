# Attribution / 致谢

OneIms Lite 不是从零写起的 Shizuku。感谢前辈们把路铺好——本仓库在合规前提下二次分发与修改。

## Upstream

| 项目 | 链接 | 许可 | 说明 |
|---|---|---|---|
| **RikkaApps/Shizuku** | https://github.com/RikkaApps/Shizuku | Apache-2.0 | 原版作者与核心架构 |
| **thedjchi/Shizuku** | https://github.com/thedjchi/Shizuku | Apache-2.0 | 开机等 Wi‑Fi、TCP mode、Watchdog 等增强 |
| **Shizuku-API** | https://github.com/thedjchi/Shizuku-API · https://github.com/RikkaApps/Shizuku-API | MIT | API / aidl / provider 等 |

请优先支持上游：

- 原版：https://github.com/RikkaApps/Shizuku  
- 功能分叉：https://github.com/thedjchi/Shizuku（作者注明暂停维护，仍应致谢）

## OneIms Lite 本仓库改动（摘要）

- 开机：若已配对且当前 Wi‑Fi STA 已连接，跳过 WorkManager `UNMETERED` 空等，直接自启（对齐 OneKuku 体验）
- 保留 TcpIp / TCP mode
- UI 换皮：对齐 OneIMS / OneIms Lite 配色与状态卡层级
- 显示名：OneIms Lite（**包名仍为上游** `moe.shizuku.manager`，避免破坏 API 契约）

详细见 `docs/ONEIMS-LITE-CHANGES.md`。

## 商标与声明

- “Shizuku” 名称与品牌归属原作者；本仓库仅为衍生发行说明。
- 使用本软件即表示你遵守 `LICENSE`（Apache-2.0）及 `api/LICENSE`（MIT）要求。
- 请勿移除本 NOTICE / ATTRIBUTION / LICENSE。
