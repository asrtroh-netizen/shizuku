import 'dart:convert';

import 'package:flutter/services.dart';

/// 「终端」Tab 的方法通道（`shizuku/terminal`）。每个方法返回 JSON 字符串；
/// 无宿主时读到 `unavailable`，Widget 测试仍能画出整页。
/// 不设事件通道（RULEBOOK §1.1 v1.2：被 `ValueKey` 重建的 Tab 一律只走方法通道）。
class TerminalChannel {
  const TerminalChannel._();

  static const MethodChannel _method = MethodChannel('shizuku/terminal');

  static Future<Map<String, dynamic>> getState() => _invokeMap('getState');

  /// 触发系统文档树选择；返回 `{ok:true}` 只表示选择器已拉起，写入在 Kotlin 侧后台完成。
  static Future<Map<String, dynamic>> exportFiles() =>
      _invokeMap('exportFiles');

  /// 打开 rish 文档（`Helps.RISH`）。
  static Future<Map<String, dynamic>> openGuide() => _invokeMap('openGuide');

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
