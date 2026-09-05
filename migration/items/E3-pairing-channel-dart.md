# E3 · pairing_channel.dart

- 分片：E pairing（Wave 3）
- 目标：`manager_flutter/lib/pairing/pairing_channel.dart`
- 契约：RULEBOOK §1.7、§12

`PairingChannel`：`getState()`、`start()`、`openDeveloperOptions()`、`openNotificationOptions()`、`openNotificationAccessSettings()`、`requestLocalNetworkPermission()`；`static const EventChannel events = EventChannel('shizuku/pairing/events')`。

完成判据：`flutter analyze` 无问题；E5 覆盖。
