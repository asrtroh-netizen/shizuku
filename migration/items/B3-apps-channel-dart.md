# B3 · apps_channel.dart

- 分片：B apps（Wave 1 试点）
- 目标：`manager_flutter/lib/apps/apps_channel.dart`
- 契约：RULEBOOK §1.7、§9
- 参考：`manager_flutter/lib/home/home_channel.dart`（逐行同模式；不要 import 它，复制模式）

## 做什么

```dart
class AppsChannel {
  static const MethodChannel _method = MethodChannel('shizuku/apps');
  static Future<Map<String, dynamic>> getState();
  static Future<Map<String, dynamic>> toggle(String packageName, int uid);
  static Future<Uint8List?> getIcon(String packageName, int uid, int sizePx); // 解 base64；失败/unavailable → null
}
```

`_invokeMap` 与 `HomeChannel._invokeMap` 同义（JSON 字符串 → Map；`PlatformException` / `MissingPluginException` → `{'ok': false, 'unavailable': true}`；空 → `{}`）。

## 完成判据

`flutter analyze` 无问题；B5 测试通过 mock handler 覆盖 `getState` / `toggle`。
