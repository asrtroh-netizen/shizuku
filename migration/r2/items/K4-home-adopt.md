# K4 · HomeActions 采用共享工具 + 短标签字符串（Wave 2）

- 分片：K4（Wave 2，依赖 K1、K2 已 verified）· 模型 Fable 5.1
- 目标（manifest target）：`migration/r2/receipts/K4-home-adopt.txt`
- 修改：`manager/src/main/java/moe/shizuku/manager/home/HomeActions.kt`、`manager/src/test/java/moe/shizuku/manager/home/HomeStateTest.kt`（如需补用例）、`manager/src/main/res/values/strings.xml`、`values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml`、`values-ja/strings.xml`、`values-ko/strings.xml`（各只**追加** 2 个 key）
- **不碰**：其它文件
- 契约：R2 RULEBOOK §R1；SPEC §6 改进 I-2、I-3

## 步骤

1. **I-2 语言标签**：`HomeActions.localeArray()` 里的 5 条 `when` 表删掉，改为 `LocaleLabels.rows(ShizukuLocales.LOCALES.toList(), current, activity.getString(R.string.settings_language_system))` 再映射成 `List<Map>`（字段 `tag/label/selected` 不变）。
2. **开机互斥**：`setBootRoot` / `setBootWireless` 中"checked 则把另一项置 false"的两行改为用 `applyBootToggle(BootPrefs(bootRoot, bootWireless), key, checked)` 计算 `next`，然后落盘两项、`setBootReceiverEnabled(next.bootRoot || next.bootWireless)`。**其余副作用（`WifiReadyMonitor`、缺权限返回 `needGrant`）一字不动**——这是首页面的既有语义（GAP R2-G2）。
3. **I-3 短标签**：`strings.xml`（默认）追加 `<string name="nav_home">Home</string>`、`<string name="nav_apps">Apps</string>`；zh-rCN：`首页` / `应用`；zh-rTW：`首頁` / `應用`；ja：`ホーム` / `アプリ`；ko：`홈` / `앱`。追加位置放在各文件 `</resources>` 之前，保留原有缩进风格。`HomeActions.copyMap()`：`tabHome` → `R.string.nav_home`，`tabApps` → `R.string.nav_apps`（`tabTerminal` / `tabSettings` 不变）。
4. `HomeStateTest`：若 K1 的测试对 `locales` 只做透传断言则不必改；可加一条 `LocaleLabels.rows` 与 `copy` key 存在性的断言。

## 自查

`rg -n '"zh-CN" ->|"zh-TW" ->' manager/src/main/java/moe/shizuku/manager/home/HomeActions.kt` → 零命中（旧 5 条表已删）；`rg -n 'name="nav_(home|apps)"' manager/src/main/res` → 5 个文件各 2 条。

## 完成判据

协调者：`:manager:testDebugUnitTest` 全绿；`flutter test` 全绿（Dart fallback 未变）。
