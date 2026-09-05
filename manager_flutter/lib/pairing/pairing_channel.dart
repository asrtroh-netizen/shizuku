import 'dart:convert';

import 'package:flutter/services.dart';

/// 「无线配对」引导页的方法通道（`shizuku/pairing`）+ 事件通道（`shizuku/pairing/events`）。
/// 每个方法返回 JSON 字符串；[start] / [requestLocalNetworkPermission] 会改状态，直接带回整页快照；
/// 三个 `open*` 只回 `{ok:true}`。事件通道只推固定字符串 `"changed"`，页面收到后自己拉 [getState]。
/// 无宿主时读到 `unavailable`，Widget 测试仍能画出整页。
class PairingChannel {
  const PairingChannel._();

  static const MethodChannel _method = MethodChannel('shizuku/pairing');

  /// 推入页专用（RULEBOOK §1.1 v1.2：push/pop 串行，不受 GAP-15 影响）。
  static const EventChannel events = EventChannel('shizuku/pairing/events');

  static Future<Map<String, dynamic>> getState() => _invokeMap('getState');

  /// 页面进入 = Activity `onCreate` 的 `syncState(); startPairingIfReady()`；回整页快照。
  static Future<Map<String, dynamic>> start() => _invokeMap('start');

  static Future<Map<String, dynamic>> openDeveloperOptions() =>
      _invokeMap('openDeveloperOptions');

  static Future<Map<String, dynamic>> openNotificationOptions() =>
      _invokeMap('openNotificationOptions');

  static Future<Map<String, dynamic>> openNotificationAccessSettings() =>
      _invokeMap('openNotificationAccessSettings');

  /// 同 Compose `onRequestLocalNetworkPermission`（也是"服务启动失败"卡的重试）；回整页快照。
  static Future<Map<String, dynamic>> requestLocalNetworkPermission() =>
      _invokeMap('requestLocalNetworkPermission');

  static Future<Map<String, dynamic>> _invokeMap(
    String method, [
    Map<String, dynamic>? args,
  ]) async {
    try {
      final raw = await _method.invokeMethod<String>(method, args);
      if (raw == null || raw.isEmpty) return const <String, dynamic>{};
      final decoded = jsonDecode(raw);
      return decoded is Map
          ? Map<String, dynamic>.from(decoded)
          : const <String, dynamic>{};
    } on PlatformException {
      return const <String, dynamic>{'ok': false, 'unavailable': true};
    } on MissingPluginException {
      return const <String, dynamic>{'ok': false, 'unavailable': true};
    }
  }
}
