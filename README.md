<div align="center">

# ✨ Shizuku · asrtroh 修缮版

**开机 Wi‑Fi 已连也能自启 · TcpIp 保留 · 蓝系 UI 微调**

基于 [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) 的合规衍生版  
**本仓只做 Shizuku 管理端** —— IMS 请看旁边的 [OneIms](https://github.com/asrtroh-netizen/OneIms) 哦～

[⬇️ 下载 APK](#-下载) · [🛠️ 本分叉改动](#-本分叉改动) · [👨‍👩‍👧 同门产品](#-同门产品--来串个门) · [🙏 致谢](#-致谢必读)

<br/>

| 标签 | 说明 |
|:---:|:---|
| `13.7.0-asrtroh` | 当前发布标签 |
| `moe.shizuku.manager` | 包名（与官方同系，冲突需先卸载） |
| Apache-2.0 | 主体协议 |

</div>

---

## 🙏 致谢（必读）

> 核心能力来自 **[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)** 与 **[thedjchi/Shizuku](https://github.com/thedjchi/Shizuku)**。  
> 本仓只是站在巨人肩膀上拧了几颗螺丝——**别把功劳算错人啦** 🙈  
> 完整署名：[ATTRIBUTION.md](./ATTRIBUTION.md) · [NOTICE](./NOTICE)  
> 官方原版：[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)

---

## 🛠️ 本分叉改动

| 改动 | 一句话 |
|---|---|
| 🚀 **开机自启** | 配对过且重启后 Wi‑Fi **已经连上**时直接启动——修部分机型「Wi‑Fi 明明在线却卡住」 |
| 📡 **TcpIp / TCP mode** | 保留，没砍 |
| 🎨 **UI** | 克制的 Material 蓝 + 卡片圆角微调（**不绑定任何 IMS 产品名**） |

---

## ⬇️ 下载

<div align="center">

### 👉 [Releases · v13.7.0-asrtroh](https://github.com/asrtroh-netizen/shizuku/releases/tag/v13.7.0-asrtroh)

| 口味 | 文件 | 适合谁 |
|---|---|---|
| 💎 **Release（推荐）** | [`shizuku-v13.7.0-asrtroh-release.apk`](https://github.com/asrtroh-netizen/shizuku/releases/download/v13.7.0-asrtroh/shizuku-v13.7.0-asrtroh-release.apk) | 日常使用 · 体积小 · 正式签名 |
| 🧪 Debug | [`shizuku-v13.7.0-asrtroh-debug.apk`](https://github.com/asrtroh-netizen/shizuku/releases/download/v13.7.0-asrtroh/shizuku-v13.7.0-asrtroh-debug.apk) | 排障 / 开发 |

</div>

> ⚠️ 安装前请卸载冲突的同包名 Shizuku。  
> ⚠️ Release 与 Debug **签名不同**，不能互相覆盖安装——换口味要先卸旧包哦～

---

## 👨‍👩‍👧 同门产品 · 来串个门

> 下面都是公开项目，和本仓 **分开维护**。喜欢就点进去看看，不喜欢也没关系，哥哥我不会哭……大概 🥺

<table>
<tr>
<td width="50%" valign="top">

### 📱 [OneIms](https://github.com/asrtroh-netizen/OneIms)

**让 Pixel 和运营商重新学会沟通。**

面向 Google Pixel 的 IMS 配置 / 诊断 / 修复助手——  
VoLTE · VoWiFi · VoNR · 信号格 · 国家码 · CarrierConfig……

- 🟢 **OneKuku**：App 内一键配对，少装一个 App  
- 🔵 **OneIms Lite**：已有 Shizuku 时更轻的那一壳（**正好能配本仓这只 Shizuku～**）

📦 [最新 Release · v2.3.0](https://github.com/asrtroh-netizen/OneIms/releases/tag/v2.3.0)  
💬 [Telegram · OneBoardX](https://t.me/OneBoardX)

</td>
<td width="50%" valign="top">

### 🎛️ [OneBoard](https://github.com/asrtroh-netizen/oneboard)

**Web 的魂，OneIMS 的皮。**

原生 Compose 控制台：盯网关、切代理、看设备、扫日志——  
把家里的 onebord gateway（默认 `:8866`）揣进裤兜里。

- 📊 仪表盘 / 流量 / DNS  
- 🔀 Proxy · 规则 · 订阅  
- 📶 Vohive 设备与短信  
- 🔐 路径级 Bearer，不乱串密钥

📦 仓库：[asrtroh-netizen/oneboard](https://github.com/asrtroh-netizen/oneboard)

</td>
</tr>
</table>

```text
        ┌─────────────┐     特权通道      ┌──────────────────┐
        │   OneIms    │ ───────────────► │  Shizuku（本仓）  │
        │ Lite 产品线 │                  │  开机自启修缮版   │
        └─────────────┘                  └──────────────────┘
                │
                │  同生态 · 不同仓
                ▼
        ┌─────────────┐
        │  OneBoard   │  ← 网关控制台（Compose）
        └─────────────┘
```

小提示：装 **OneIms Lite** 时，记得让本仓 Shizuku **保持 Start**，日常一点就能授权，不必每次重新配对 ✨

---

## 🏗️ 构建

```bash
git clone --recurse-submodules https://github.com/asrtroh-netizen/shizuku.git
cd shizuku
# 配置 local.properties → sdk.dir
./gradlew :manager:assembleDebug
# 或正式包：./gradlew :manager:assembleRelease（需本地 signing.properties）
```

---

## 📜 License

- 主体：**Apache-2.0**（[LICENSE](./LICENSE)）
- API 子树：**MIT**（`api/LICENSE`）

修改与再分发请保留 `NOTICE`、`ATTRIBUTION.md` 与 `LICENSE`。  
偷走星星可以，偷走署名不行——被发现会用拖鞋拍你的 🩴✨

---

<div align="center">

**Made with caffeine & stubbornness · by [asrtroh-netizen](https://github.com/asrtroh-netizen)**

若本仓帮到你，去给上游 [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) 也点个 ⭐ 吧～那才是真正的本尊！

</div>
