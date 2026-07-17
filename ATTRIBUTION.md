# Attribution / 致谢

本仓库是 Shizuku 的合规衍生发行，**不是** OneIms / IMS 产品源码。IMS 请见 https://github.com/asrtroh-netizen/OneIms 。

## Upstream

| 项目 | 链接 | 许可 |
|---|---|---|
| **RikkaApps/Shizuku** | https://github.com/RikkaApps/Shizuku | Apache-2.0 |
| **thedjchi/Shizuku** | https://github.com/thedjchi/Shizuku | Apache-2.0 |
| **Shizuku-API** | https://github.com/thedjchi/Shizuku-API · https://github.com/RikkaApps/Shizuku-API | MIT |

请优先支持上游作者。

## 本仓库改动（摘要）

- 开机：已连 Wi‑Fi STA 时跳过 WorkManager `UNMETERED` 空等，直接自启
- 保留 TcpIp / TCP mode
- UI 配色与状态卡微调
- 显示名保持 **Shizuku**；包名仍为上游 `moe.shizuku.manager`

详见 `docs/CHANGES.md`。

## 声明

- “Shizuku” 名称与品牌归属原作者
- 请勿移除 NOTICE / ATTRIBUTION / LICENSE
