# Gap Inventory R2 · 需要人为决策或本轮明确不做的硬点

| # | 现象 | 决策 | 归属 |
|---|---|---|---|
| R2-G1 | Watchdog 开关前置条件不一致：首页 `HomeActions.setWatchdog` 只看 `pingBinder()`，设置页要求 `lastLaunchMode == ADB && pingBinder()` | **本轮不统一**（行为保持）。两处逻辑各自保留；产品定语义后另开单 | 待产品 |
| R2-G2 | 无线开机开关：首页调 `WifiReadyMonitor.ensureRegistered/unregister`，设置页不调 | **本轮不统一**；同上 | 待产品 |
| R2-G3 | `AppIconCache.appIconLoaders` 普通 `mutableMapOf`，多 IO 线程并发有竞态（Compose 时代已存在） | 本轮不动（不属去重/死代码）；建议下轮改 `ConcurrentHashMap` 并加测试 | 待排期 |
| R2-G4 | `TerminalChannel` 导出流不关闭（从 `ShellTutorialActivity` 逐行复刻来的） | 本轮不动（行为保持）；下轮用 `use {}` 修并单测 | 待排期 |
| R2-G5 | `proguard-rules.pro` 里 ViewModel `clear()` keep 规则原为 Compose 应用管理页 R8 问题而加；页面已删但 `StarterActivity` 仍用 rikkax ViewModel | 保留不动（没有真机回归手段） | 本轮 OUT |
| R2-G6 | Ultra `one_status_hero.dart` 新增次按钮（OneKuku「配对」）；产品方案曾写"已运行 → 不占 Hero 按钮" | 组件回同步（I-4）但首页不启用；是否把"配对"挪到门面卡是产品决定 | 待产品 |
| R2-G7 | `HomeActions` 仍在 `home/` 包而其它通道在 `flutter/` 包 | 不搬包（纯目录审美，会造成 git 大改名）；只解掉 `home → flutter` 的 import 环 | 本轮决定 |
| R2-G8 | `HomeScreen` 里 Lang 芯片与设置页语言列表将共用 43 条表后，首页 `locales` JSON 体积略增（每次 `getState`） | 可接受（<3 KB） | 本轮决定 |
| R2-G9 | 新增 `nav_home` / `nav_apps` 只提供 en / zh-rCN / zh-rTW / ja / ko；其它 40+ 语言回落英文 `Home` / `Apps` | 可接受；比 `Application management` 截断更好 | 本轮决定 |
| R2-G10 | `manager_flutter/test/widget_test.dart` 等旧测试用 `find.text('Apps')` 等 fallback 断言；I-3 只改 Kotlin 侧 key，Dart fallback 不变 | 不受影响 | — |
