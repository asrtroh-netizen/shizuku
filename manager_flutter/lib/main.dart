import 'dart:ui' show PlatformDispatcher;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:manager_flutter/home/home_channel.dart';
import 'package:manager_flutter/home/home_models.dart';
import 'package:manager_flutter/nav/app_shell.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/theme/app_theme.dart';

void main() => runApp(
      ShizukuFlutterApp(
        initialTab: initialTabFromRoute(
          PlatformDispatcher.instance.defaultRouteName,
        ),
      ),
    );

/// 底栏只有首页 / 设置：0 = 首页，1 = 设置。
///
/// 宿主 `FlutterHostActivity.getInitialRoute()` 在设置页 recreate 后给出 `/tab/<n>`。
/// `/tab/3` 是两 Tab 之前的设置下标，仍映射到设置；其它未知路由回首页。
int initialTabFromRoute(String route) {
  final match = RegExp(r'^/tab/(\d)$').firstMatch(route);
  if (match == null) return 0;
  final n = int.parse(match.group(1)!);
  if (n == 1 || n == 3) return 1;
  return 0;
}

class ShizukuFlutterApp extends StatefulWidget {
  const ShizukuFlutterApp({super.key, this.initialTab = 0});

  final int initialTab;

  @override
  State<ShizukuFlutterApp> createState() => _ShizukuFlutterAppState();
}

class _ShizukuFlutterAppState extends State<ShizukuFlutterApp>
    with WidgetsBindingObserver {
  /// 先按系统亮度猜一帧，等首页快照回来再改成 `ShizukuSettings.NIGHT_MODE`。
  bool _dark =
      PlatformDispatcher.instance.platformBrightness == Brightness.dark;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _syncTheme();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangePlatformBrightness() {
    _syncTheme();
  }

  Future<void> _syncTheme() async {
    final map = await HomeChannel.getState();
    if (!mounted) return;
    final dark = HomeSnapshot.fromJson(map).dark;
    setState(() => _dark = dark);
    SystemChrome.setSystemUIOverlayStyle(
      SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
        systemNavigationBarColor: Colors.transparent,
        systemNavigationBarIconBrightness:
            dark ? Brightness.light : Brightness.dark,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Shizuku',
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      // 必须跟首页快照的 `dark`（即 NIGHT_MODE），不能 ThemeMode.system：
      // FlutterHostActivity 不是 AppCompatActivity，系统亮度不会跟着
      // AppCompatDelegate.setDefaultNightMode 变，滑块会动、整页却不变。
      themeMode: _dark ? ThemeMode.dark : ThemeMode.light,
      initialRoute: '/',
      builder: (context, child) =>
          GlassBackdrop(child: child ?? const SizedBox.shrink()),
      home: AppShell(initialIndex: widget.initialTab),
    );
  }
}
