import 'package:flutter/material.dart';
import 'package:manager_flutter/home/home_screen.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/theme/app_theme.dart';

void main() => runApp(const ShizukuFlutterApp());

class ShizukuFlutterApp extends StatelessWidget {
  const ShizukuFlutterApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Shizuku',
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ThemeMode.system,
      builder: (context, child) =>
          GlassBackdrop(child: child ?? const SizedBox.shrink()),
      home: const HomeScreen(),
    );
  }
}
