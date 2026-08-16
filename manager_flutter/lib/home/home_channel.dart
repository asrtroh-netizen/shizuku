import 'dart:convert';

import 'package:flutter/services.dart';

/// 首页只读通道：`shizuku/home`。无宿主时返回空 Map，UI 走未运行哭脸。
class HomeChannel {
  const HomeChannel._();

  static const MethodChannel _method = MethodChannel('shizuku/home');

  static Future<Map<String, dynamic>> getState() async {
    try {
      final raw = await _method.invokeMethod<String>('getState');
      if (raw == null || raw.isEmpty) return const <String, dynamic>{};
      final decoded = jsonDecode(raw);
      return decoded is Map
          ? Map<String, dynamic>.from(decoded)
          : const <String, dynamic>{};
    } on PlatformException {
      return const <String, dynamic>{};
    } on MissingPluginException {
      return const <String, dynamic>{};
    }
  }
}
