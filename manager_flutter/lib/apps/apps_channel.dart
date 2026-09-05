import 'dart:convert';

import 'package:flutter/services.dart';

/// 「授权应用」Tab 的方法通道（`shizuku/apps`）。每个方法返回 JSON 字符串；
/// 无宿主时读到 `unavailable`，Widget 测试仍能画出整页。
class AppsChannel {
  const AppsChannel._();

  static const MethodChannel _method = MethodChannel('shizuku/apps');

  static Future<Map<String, dynamic>> getState() => _invokeMap('getState');

  /// 切换授权；返回整页快照 + `result: 'success' | 'adbLimited'`。
  static Future<Map<String, dynamic>> toggle(String packageName, int uid) =>
      _invokeMap('toggle', <String, dynamic>{
        'packageName': packageName,
        'uid': uid,
      });

  /// 应用图标 PNG 字节；找不到 / 解码失败 / 无宿主 → null。
  static Future<Uint8List?> getIcon(
    String packageName,
    int uid,
    int sizePx,
  ) async {
    final map = await _invokeMap('getIcon', <String, dynamic>{
      'packageName': packageName,
      'uid': uid,
      'sizePx': sizePx,
    });
    if (map['ok'] != true) return null;
    final png = map['png'];
    if (png is! String || png.isEmpty) return null;
    try {
      return base64Decode(png);
    } on FormatException {
      return null;
    }
  }

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
