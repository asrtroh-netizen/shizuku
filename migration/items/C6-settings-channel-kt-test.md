# C6 · SettingsChannelTest.kt

- 分片：C settings（Wave 2）
- 目标：`manager/src/test/java/moe/shizuku/manager/flutter/SettingsChannelTest.kt`
- 契约：RULEBOOK §1.8、§7.2

## 必须覆盖（只测 C1 纯函数）

1. `applyBootToggle`：开 Root → 无线变 false；开无线 → Root 变 false；关任一项不影响另一项。
2. `parseTcpipPort`：`""` → null；`"5555"` → 5555；`"0"`、`"65536"`、`"abc"` → 抛 `IllegalArgumentException`。
3. `buildSettingsStateMap`：字段名精确（§10），`tcpipPort` 未设时为 `""` 而非 null。

## 完成判据

协调者运行 `:manager:testDebugUnitTest` 全绿；零 `android.*` / `org.json` import。
