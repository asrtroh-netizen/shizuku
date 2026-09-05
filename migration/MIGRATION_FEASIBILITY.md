# 可行性裁决 · Shizuku 管家壳 Compose → Flutter（P2–P4）

日期：2026-09-05  
基线 commit：`a10c72af207e046bf2ed38dfc508d24bc33e1aa6`（`main`，与 `origin/main` 同步）  
裁决：**GO**

## 迁移类型

**重设计型**（不是逐文件同构翻译）：Compose 页面被 Flutter 页面替代，业务逻辑留在 Kotlin，通过 MethodChannel（JSON 字符串）暴露。规则手册即设计文档。

## 证据

| 问题 | 结论 | 证据 |
|---|---|---|
| 目标栈是否覆盖源能力 | 是 | P0/P1 已落地：Launcher = `FlutterHostActivity`，首页全部动作已经通过 `shizuku/home` 通道跑通（`FlutterHostActivity.kt` 190 行、`HomeActions.kt` 461 行、`home_screen.dart` 889 行） |
| 现有测试能否继续当裁判 | 部分能 | Kotlin 单测 18 个（5 个类）全绿；Flutter 测试 3 个全绿、`flutter analyze` 0 问题。它们覆盖事件闸门、授权计数、开机自启，不覆盖 Apps/Settings/Terminal/Pairing 页面 → 需为每个新页面补 Widget 测试 + Kotlin 纯函数测试作为增量裁判 |
| 工具链是否支持 | 是 | git 2.54 / ripgrep 15.1 / Flutter 3.44.9 stable / Dart 3.12.2 / JDK 21.0.11 / Gradle wrapper 8.14.4 / AGP 8.10.1 / Kotlin 2.4.10-RC。注意：`JAVA_HOME` 默认指向 JDK 17 会导致 `:aidl:compileDebugJavaWithJavac` 报"无效的源发行版 21"，必须用 JDK 21 运行 Gradle |
| 皮肤真源是否可用 | 是，但已漂移 | `E:\GQ\Ultra\OneIms Ultra\oneims_flutter\lib` 存在；`nav/glass_dock.dart`、`widgets/glass_choice_dialog.dart`、`onetools/liquid_glass.dart` 可复制。但 Ultra 的 `glass.dart`（763 行）、`one_status_hero.dart`（441 行）、`dot_matrix_face.dart`（82 行）已比本仓库副本（705 / 399 / 84 行）演进，本次**不整体回同步**，只做加性移植 |
| 工作区是否干净 | 内容级干净 | `git status` 显示 11 个 " M" 文件，但 `git diff --name-only HEAD` 为 0 行；`ls-files --eol` 显示 i/lf w/crlf，`core.autocrlf=true` + `.gitattributes eol=lf` 造成的纯行尾差异。未跟踪：`.xj-cursor/`（6 个会话笔记，不属于迁移） |

## 四种结论的代价

- **继续迁移（选）**：P2–P3 约 27 个新文件 + 6 处宿主接线；风险可控（旧 Activity 并存作回滚）。
- 缩小范围：只做 Apps + Settings，不做 Terminal/Pairing — 底栏 4 项设计会缺 1 项，不选。
- 先补裁判：现有裁判对新页面无覆盖，但新页面裁判可随页面同批交付（Widget 测试 + Kotlin 纯函数测试），无需前置阻塞。
- 暂不迁移：与产品目标（"打开 App 一眼是 One 家族"）冲突。

## 最短解阻条件（本次无阻塞）

无。唯一环境注意项是 Gradle 必须以 JDK 21 运行（`$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot'`）。
