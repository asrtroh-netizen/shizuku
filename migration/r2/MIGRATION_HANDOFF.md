# MIGRATION_HANDOFF R2 · 会话交接

更新：2026-09-06  
基线 commit：`91b9f3579ca8e48f0c4a4ca4ef8b07997168013b`（V15.2.0 发布后的 `main`）  
STATE_ROOT：`migration/r2/`；规格 SPEC v1.2 / RULEBOOK R2 v1（增补，基于 v1.3）  
`CURRENT_BATCH_GATE` = `2ed09ac9d3971b0166d13edb7dbc9e2bd580ddf91b985d3c59bd1961a2159eff`；`CURRENT_BATCH_MODE` = `complete`

## 队列

| 桶 | 数量 |
|---|---|
| verified | 7 / 7（K1 K2 K3 D1 D3 · D2 K4） |
| pending / blocked | 0 / 0 |

## 结果

- **裁判**：`flutter analyze` No issues；`flutter test` **74**（基线 56 + 首页 16 + I-5 2）；`gradlew :manager:testDebugUnitTest` **73**（基线 50 + HomeStateTest 12 + SharedHelpersTest 11）；`assembleDebug` + `assembleRelease` BUILD SUCCESSFUL；`aapt2` 包名 / Launcher / ABI 不变。
- **包体**：Release 22.5 → **19.6 MB**（`shizuku-V15.5.0-release.apk` 20,574,902 bytes）。已重编 Flutter AAR 后再打正式包。
- **§4 核验**：五个 Kotlin 重复实现各只剩一份；`_GlassAlert` / `_NoticeCard` 零命中；死代码 / 死依赖零命中；宿主无内联首页路由；`home_screen.dart` 330 行；`HomeActions` 不再依赖宿主类。
- **改动面**：已提交 `9feb450` feat(manager) + `1e132d7` docs(migration) + `d87df11` docs(readme) + `5b0691f` chore(release)。

## 用户可见的变化（全部在 SPEC §6 声明并有测试）

I-1 首页列表从底栏下透出（与其它 Tab 一致）；I-2 首页 Lang 芯片显示完整语言名；I-3 底栏标签 `Home / Apps`（zh：首页 / 应用；zh-TW / ja / ko 已配）；I-4 门面组件与 Ultra 真源逐字节一致（首页视觉不变）；**I-5 修复 V15.2.0 的 bug：「PC ADB → 查看命令」弹窗按取消会抛类型错误且关不掉**；I-6 应用 Tab 与配对页占位卡改横排提示卡。

## 发版

已提交并推送到 `origin/main`，标签 `V15.5.0` 打在 `5b0691f`。GitHub Release：[V15.5.0](https://github.com/asrtroh-netizen/shizuku/releases/tag/V15.5.0)（资产 `shizuku-V15.5.0-release.apk`，SHA256 `f2adc39ff7bfc7aed30fca8f0b43e88e9e9bf7f49d1e3e2e645a4f3798966ac7`；发布说明里明确标注真机验收未做）。应用内 UpdateChecker 读 `releases/latest` 会看到它。`migration/round2/` 未纳入提交。

GAP R2-G1 / G2（Watchdog 与 WifiReadyMonitor 在首页 / 设置页语义不一致）仍待产品拍板。

## 下一轮候选（见 OBSERVATIONS.md）

`AppIconCache` 并发；`TerminalChannel` 导出流关闭；`home_models.dart` 宽容解析；`HomeThemeSlide` 字面 hex；死资源 `main_layout_manager`；`libs.versions.toml` 残留别名。

## 续跑

读 `MIGRATION_SPEC.md` → 本文件 → 校验 `STATE_VERSION.tsv` → 重扫 `MIGRATION_MANIFEST.tsv` 目标与 `VERIFY_LEDGER.tsv`。Gradle 由 `gradle/gradle-daemon-jvm.properties` 自动选 JDK 21。
