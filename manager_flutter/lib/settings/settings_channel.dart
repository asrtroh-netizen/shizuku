import 'dart:convert';

import 'package:flutter/services.dart';

/// 「设置」Tab 的方法通道（`shizuku/settings`）。每个方法返回 JSON 字符串；
/// 写操作直接带回整页快照（可附 `needGrant/grantCmd`、`needNotificationAccess`）。
/// 无宿主时读到 `unavailable`，Widget 测试仍能画出整页。
class SettingsChannel {
  const SettingsChannel._();

  static const MethodChannel _method = MethodChannel('shizuku/settings');

  static Future<Map<String, dynamic>> getState() => _invokeMap('getState');

  /// [key] 必须是 `SettingsKeys.*` 之一（Kotlin `ShizukuSettings` / `ThemeHelper` 的实际字符串值）。
  static Future<Map<String, dynamic>> setBool(String key, bool checked) =>
      _invokeMap('setBool', <String, dynamic>{'key': key, 'checked': checked});

  /// 空串 = 清除；非法端口由 Kotlin `parseTcpipPort` 拒绝（走 `PlatformException` → `unavailable`）。
  static Future<Map<String, dynamic>> setTcpipPort(String port) =>
      _invokeMap('setTcpipPort', <String, dynamic>{'port': port});

  static Future<Map<String, dynamic>> setNightMode(int mode) =>
      _invokeMap('setNightMode', <String, dynamic>{'mode': mode});

  static Future<Map<String, dynamic>> setLocale(String tag) =>
      _invokeMap('setLocale', <String, dynamic>{'tag': tag});

  /// 无参数动作：`openNotificationAccess` / `openTranslation` / `openWirelessGuide` / `copyText {text}`。
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
