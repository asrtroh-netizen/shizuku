# K3 · 死代码与死依赖清理

- 分片：K3（Wave 1）· 模型 Fable 5.1
- 目标（manifest target）：`migration/r2/receipts/K3-dead-code.txt`（协调者验证后写入）
- 删除：`manager/src/main/java/moe/shizuku/manager/ktx/RecyclerView.kt`、`manager/src/main/java/moe/shizuku/manager/widget/VerticalPaddingDecoration.java`、`manager/src/main/java/moe/shizuku/manager/widget/CheckedImageView.java`
- 修改：`manager/build.gradle`（只删依赖行）
- **不碰**：`gradle/libs.versions.toml`、`proguard-rules.pro`、任何其它文件
- 契约：R2 RULEBOOK §R6；SPEC §8 依赖锁定

## 步骤

1. 引用核查（把每条命令与输出贴进报告）：
   - `rg -n "FixedAlwaysClipToPaddingEdgeEffectFactory|ktx\.RecyclerView" manager/src` → 应只命中自身文件。
   - `rg -n "VerticalPaddingDecoration|CheckedImageView" manager/src manager/src/main/res` → 应只命中自身文件（注意 XML 布局里也可能以全限定名引用，`res/layout/*.xml` 要查）。
   - `rg -n "observeAsState|compose\.runtime\.livedata" manager/src/main/java` → 零。
   - `rg -n "material\.icons" manager/src/main/java` → 零（`material3` 自带的 `Icons.Default.*` 也算命中，命中则**不删** icons-extended 并注明）。
   - `rg -n "@Preview|compose\.ui\.tooling" manager/src/main/java` → 零。
2. 删除三个源文件；若 `widget/` 目录因此为空，目录本身随之消失即可。
3. `manager/build.gradle` 删除这四行（其余任何一行都不动）：
   ```
   implementation libs.androidx.compose.ui.tooling.preview
   implementation libs.androidx.compose.material.icons.extended
   implementation libs.androidx.compose.runtime.livedata
   debugImplementation libs.androidx.compose.ui.tooling
   ```
   保留：`platform(libs.androidx.compose.bom)`、`activity.compose`、`compose.ui`、`compose.material3`、全部 `rikka.*` / `lifecycle.*` / `fragment.ktx` / `borderview` / `libs/lifecycle-shared-viewmodel-noshim.jar`（`StarterActivity` 与 `AppActivity` 基类链仍在用）。
4. 复查：`rg -n "runtime.livedata|icons.extended|ui.tooling" manager/build.gradle` → 零。

## 报告

- 每条 rg 的原文；`git diff manager/build.gradle`；被删文件列表。
- 若任何一条核查有命中，**不删该项**，在报告 OBSERVATIONS 写明命中处。

## 完成判据

协调者：`:manager:testDebugUnitTest` 通过 + `:manager:assembleDebug` 通过（R8 收尾时再验 release）。
