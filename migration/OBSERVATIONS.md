# OBSERVATIONS · 范围外发现（只记录，不夹带修复）

格式：日期 | 发现者 | 位置 | 现象 | 建议（另开任务）

- 2026-09-05 | 协调者 | `manager_flutter/lib/onetools/{glass,one_status_hero,dot_matrix_face}.dart` | 与 Ultra 真源已漂移（705/399/84 行 vs 763/441/82 行）；`dot_matrix_face.dart` 违反产品方案"hash 必须一致" | 单独立项回同步 onetools，并补 Widget 测试锁视觉
- 2026-09-05 | 协调者 | `res/values/strings.xml` | 无短标签 "Apps"/"Home"；底栏暂用 `home_app_management_title` / `app_name` | 后续单独加 `nav_home` / `nav_apps` 两个 key 并翻译
- 2026-09-05 | 协调者 | `HomeActions.setWatchdog` vs `SettingsComposeScreen` Watchdog 分支 | 启动 Watchdog 的前置条件不一致（GAP-10） | 产品定一个语义后统一
- 2026-09-05 | 协调者 | 工作区 | 11 个文件只有 CRLF/LF 差异（`core.autocrlf=true` 与 `.gitattributes eol=lf` 叠加） | 建议 `git add --renormalize .` 一次性清掉幻影改动（需用户授权，不属迁移）
- 2026-09-05 | 协调者 | 环境 | `JAVA_HOME` 默认 JDK 17，项目需 21，Gradle 直接跑会失败 | 在 `gradle.properties` 加 `org.gradle.java.home` 或用 toolchain 声明（需用户决定）
- 2026-09-05 | 分片 A | `home_screen.dart` `_QuickGrid` | 用 `Icons.terminal` 而非 `_outlined`；`HomeScreen` 自带 `SafeArea` 使列表停在 Dock 之上而非从 Dock 下透出（Ultra 视觉用 `glassDockScrollPadding`） | 首页视觉微调另立项
- 2026-09-05 | 分片 B | `utils/AppIconCache.kt` | `appIconLoaders` 是普通 `mutableMapOf`，多 IO 线程并发有竞态（Compose 现状同样） | 改 `ConcurrentHashMap` 或加锁，另立项
- 2026-09-05 | 分片 C | `HomeActions.setBootWireless` vs `SettingsComposeScreen` | 首页开无线开机会调 `WifiReadyMonitor.ensureRegistered/unregister`，设置页不调（第 184 行疑似被删过） | 产品定语义后统一
- 2026-09-05 | 分片 C | `HomeActions.localeArray` vs 设置页 `buildLocaleItems` | 首页 Lang 芯片只有 5 种语言有标签，其它显示原始 tag；设置页有 43 条完整表 | 统一为一份标签表
- 2026-09-05 | 分片 D | `ShellTutorialActivity` 导出 | `copyTo` 后从不关闭 `OutputStream` 与 asset `InputStream`；`deleteDocument` 失败会在主线程崩 | 修流关闭与异常处理（P4 删旧后只需修 `TerminalChannel`）
- 2026-09-05 | 分片 D | `ShellTutorialComposeScreen.mono()` | `plainText` 把 monospace span 丢掉，Compose 从未真正渲染等宽；`FontFamily` 是未用 import | 随 P4 一并删除
- 2026-09-05 | 分片 E | `AdbPairingTutorialActivity.onResume` | 条件里的 `pairingServiceStartFailed` 读的是 `syncState()` 不改写的旧值（语义偶然成立）；`R.string.adb_pairing_tutorial_content_network_limation_not_foreground` key 名拼写错误 | 保持现状；改 key 名需连带翻译文件
- 2026-09-05 | 分片 E | `SettingsChannel.isNotificationListenerEnabled`（`isNullOrEmpty`）vs Activity 版（`!= null`） | 两份复刻在空串判断上有细微差异，行为等价（空串 split 后无有效 ComponentName） | P4 后合并为一份工具函数
