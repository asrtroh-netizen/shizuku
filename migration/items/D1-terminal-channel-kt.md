# D1 · TerminalChannel.kt

- 分片：D terminal（Wave 2）
- 目标：`manager/src/main/java/moe/shizuku/manager/flutter/TerminalChannel.kt`
- 契约：RULEBOOK §1、§6.4、§11；GAP-5
- 只读参考：`shell/ShellTutorialActivity.kt`（导出逻辑逐行复刻）、`shell/ShellTutorialComposeScreen.kt`（全部 `R.string` key → copy）

## 做什么

```kotlin
class TerminalChannel(private val activity: ComponentActivity, private val scope: CoroutineScope) {
    private val openDocumentsTree: ActivityResultLauncher<Uri?> = activity.registerForActivityResult(OpenDocumentTree()) { tree -> ... }  // 构造期注册（GAP-5）
    fun register(messenger: BinaryMessenger)
    companion object { const val CHANNEL = "shizuku/terminal"; const val EVENTS = "shizuku/terminal/events" }
}
```

- `getState` → `{ok, shName:"rish", dexName:"rish_shizuku.dex", copy:{...}}`；copy 覆盖 Compose 页每一段文案（标题、说明、步骤、按钮：导出 / 查看指南 等，key 用 R.string 名去 `home_terminal_` / `shell_` 前缀的小驼峰，并在文件顶部注释列出映射表）。
- `exportFiles` → `openDocumentsTree.launch(null)`；回调里在 `Dispatchers.IO` 执行删除旧 `rish` / `rish_shizuku.dex` + 从 `assets` 写入（逐行同 `ShellTutorialActivity`），完成后主线程 `eventSink?.success("changed")`。
- `openGuide` → `CustomTabsHelper.launchUrlOrCopy(activity, Helps.RISH.get())`。
- 接线片段（报告里给出）：`terminalChannel = TerminalChannel(this, ioScope); terminalChannel.register(messenger)`，并注明必须在 `onCreate`/`configureFlutterEngine` 期间构造。

## 完成判据

协调者 survey build 通过；`registerForActivityResult` 不在 `STARTED` 后调用（否则运行时 IllegalStateException——在报告里提醒协调者）。
