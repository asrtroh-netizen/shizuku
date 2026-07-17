# Shizuku（asrtroh-netizen）

基于 [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) 的合规衍生版。  
**与 [OneIms](https://github.com/asrtroh-netizen/OneIms) 是分开的项目**：本仓只做 Shizuku 管理端；IMS 能力请看 OneIms。

> **致谢（必读）**  
> 核心来自 **RikkaApps/Shizuku** 与 **thedjchi/Shizuku**。  
> 完整署名见 [ATTRIBUTION.md](./ATTRIBUTION.md) 与 [NOTICE](./NOTICE)。  
> 官方原版：https://github.com/RikkaApps/Shizuku

## 本分叉改动

- **开机自启**：配对过且重启后 Wi‑Fi 已连上时直接启动（修复部分机型「Wi‑Fi 已连接却卡住」）
- **TcpIp / TCP mode**：保留
- **UI**：克制的 Material 蓝配色与卡片层级微调（不绑定任何 IMS 产品名）

## 下载

见 [Releases](../../releases)。包名：`moe.shizuku.manager`。安装前请卸载冲突的同包名 Shizuku。

## 构建

```bash
git clone --recurse-submodules https://github.com/asrtroh-netizen/shizuku.git
cd shizuku
# 配置 local.properties → sdk.dir
./gradlew :manager:assembleDebug
```

## License

- 主体：Apache-2.0（[LICENSE](./LICENSE)）
- API 子树：MIT（`api/LICENSE`）

修改与再分发请保留 NOTICE、ATTRIBUTION 与 LICENSE。
