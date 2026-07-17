# OneIms Lite

基于 [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) 的合规衍生版，UI 与开机自启体验对齐 [OneIms](https://github.com/asrtroh-netizen/OneIms) / OneIms Lite 审美。

> **致谢（必读）**  
> 核心能力来自 **RikkaApps/Shizuku** 与 **thedjchi/Shizuku**。  
> 完整署名见 [ATTRIBUTION.md](./ATTRIBUTION.md) 与 [NOTICE](./NOTICE)。  
> 若你需要官方原版，请前往 [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)。

## 相对 thedjchi 分叉的改动

- **开机自启**：配对过且重启后旧 Wi‑Fi 已连上时，直接启动（修复部分机型在「Wi‑Fi 已连接」时仍被 WorkManager 卡住的问题）
- **TcpIp / TCP mode**：保留
- **换皮**：OneIMS 蓝配色（`#0B57D0`）与状态卡圆角/层级

## 下载

见本仓库 [Releases](../../releases)。安装前请卸载冲突的同包名 Shizuku（包名：`moe.shizuku.manager`）。

## 构建

```bash
git clone --recurse-submodules https://github.com/asrtroh-netizen/OneIms-Lite.git
cd OneIms-Lite
# 配置 local.properties 中的 sdk.dir
./gradlew :manager:assembleDebug
```

本机验证过：JDK 21、Android SDK 36、NDK 27、CMake 3.22。

## License

- 主体：Apache License 2.0（见 [LICENSE](./LICENSE)）
- API 子树：MIT（见 `api/LICENSE`）

修改与再分发时请保留 NOTICE、ATTRIBUTION 与 LICENSE。
