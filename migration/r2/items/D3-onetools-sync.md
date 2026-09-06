# D3 · onetools 回同步 Ultra 真源（one_status_hero / dot_matrix_face）

- 分片：D3（Wave 1）· 模型 Fable 5.1
- 目标（manifest target）：`migration/r2/receipts/D3-onetools-sync.txt`（协调者验证后写入）
- 修改：`manager_flutter/lib/onetools/one_status_hero.dart`、`manager_flutter/lib/onetools/dot_matrix_face.dart`
- **不碰**：其它任何文件（含测试）
- 真源（只读）：`E:\GQ\Ultra\OneIms Ultra\oneims_flutter\lib\onetools\one_status_hero.dart`、`...\dot_matrix_face.dart`
- 契约：R2 RULEBOOK §R7；v1.3 RULEBOOK §3.3（复制只换 import 前缀）

## 步骤

1. 用 PowerShell 读取 Ultra 两个文件，`package:oneims_flutter/` → `package:manager_flutter/`，行尾统一 LF、UTF-8 无 BOM，整文件覆盖写入本仓库对应路径。
2. 自查：`Compare-Object`（两边都去 `\r`、统一前缀）→ 零差异；`git diff --stat` 两个文件。
3. 核对 API：新版 `OneStatusHero` 多了可选 `secondaryActionLabel` / `onSecondaryAction`；`home_screen.dart` 现有调用（只传 state/eyebrow/title/pill/stageLabels/subtitle/detail）必须**无需改动**即可编译。若 Ultra 版有任何**必填**新参数或改名，停止并在报告里列出（不要去改 home_screen.dart——那是 D1 的文件）。
4. 验证：`dart analyze lib/onetools`；`flutter test test/home_hero_face_test.dart test/widget_test.dart`（`widget_test` 若因 D1 并行改动报 `onOpenSettings` 相关错误，注明即可）。

## 完成判据

两文件与 Ultra 仅 import 前缀不同；`home_hero_face_test.dart` 不改而全绿。
