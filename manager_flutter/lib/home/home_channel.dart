import 'dart:convert';

import 'package:flutter/services.dart';

/// 原首页动作通道。无宿主时读空、写 `unavailable`，Widget 测试仍能画出整页。
class HomeChannel {
  const HomeChannel._();

  static const MethodChannel _method = MethodChannel('shizuku/home');

  static Future<Map<String, dynamic>> getState() => _invokeMap('getState');

  static Future<Map<String, dynamic>> invoke(
    String method, [
    Map<String, dynamic>? args,
  ]) =>
      _invokeMap(method, args);

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
