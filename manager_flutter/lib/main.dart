import 'dart:ui' show PlatformDispatcher;

import 'package:flutter/material.dart';
import 'package:manager_flutter/apps/apps_screen.dart';
import 'package:manager_flutter/nav/app_shell.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/settings/settings_screen.dart';
import 'package:manager_flutter/terminal/terminal_screen.dart';
import 'package:manager_flutter/theme/app_theme.dart';

void main() => runApp(
      ShizukuFlutterApp(
        initialTab: initialTabFromRoute(
          PlatformDispatcher.instance.defaultRouteName,
        ),
      ),
    );

/// 宿主 `FlutterHostActivity.getInitialRoute()` 在设置页触发 recreate 后给出
/// `/tab/<n>`（RULEBOOK §10 GAP-7）；其它任何路由都回到首页。
int initialTabFromRoute(String route) {
  final match = RegExp(r'^/tab/(\d)$').firstMatch(route);
  if (match == null) return 0;
  return int.parse(match.group(1)!);
}

class ShizukuFlutterApp extends StatelessWidget {
  const ShizukuFlutterApp({super.key, this.initialTab = 0});

  final int initialTab;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Shizuku',
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ThemeMode.system,
      // 初始路由固定为 '/'：`/tab/<n>` 只是给上面的解析用，不是 Navigator 路由。
      initialRoute: '/',
      builder: (context, child) =>
          GlassBackdrop(child: child ?? const SizedBox.shrink()),
      home: AppShell(
        initialIndex: initialTab,
        appsTab: const AppsScreen(),
        terminalTab: const TerminalScreen(),
        settingsTab: const SettingsScreen(),
      ),
    );
  }
}
