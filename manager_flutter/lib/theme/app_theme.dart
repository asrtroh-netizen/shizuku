import 'package:flutter/material.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/onetools/one_palette.dart';

/// 全应用主题：One 家族黑白色板 + 液态玻璃皮 + Montserrat。
class AppTheme {
  const AppTheme._();

  static ThemeData light() => Glass.apply(_base(oneBlackWhiteLight));

  static ThemeData dark() => Glass.apply(_base(oneBlackWhiteDark));

  static ThemeData _base(ColorScheme scheme) => ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    fontFamily: 'Montserrat',
  );
}
