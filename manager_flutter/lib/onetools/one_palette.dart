/// One 家族的经典黑白色板，从 OneIMS `ui/theme/Theme.kt` 逐字段搬来。
///
/// 这套色板的立身之本是「全局蓝换白」：强调色一律走黑白灰，除了报错红之外
/// 不出现任何彩色。OneTools 与 eSIM 同属一个家族，配色身份必须是同一副骨相。
///
/// 与 OneIMS 的唯一一处有意偏离在 [oneBlackWhiteLight] 的 `primary`：原版亮色
/// 主题里 `primary = Color.White`，那是因为 OneIMS 的主按钮全部硬编码白底黑字
/// 且只出现在粉色/灰色卡片上（见 `StatusHero` 的 `if (!settled)`），不会白压白。
/// Obtainium 侧有几十个直接吃 `primary` 的 FilledButton 散落在浅色背景上，
/// 照搬会让它们集体隐形，所以亮色主题的 `primary` 取近黑、暗色主题取白——
/// 强调色依旧只有黑白，但对比度在任何页面都成立。需要原版白按钮观感的地方
/// 用 [OneHero] 那组常量硬编码，不走 `primary`。
library;

import 'package:flutter/material.dart';

/// OneIMS 的招牌近黑，白卡上的文字与图标都用它。
const Color oneInk = Color(0xFF1A1B20);

/// StatusHero 稳态时的硬编码配色：白卡 + 近黑字，不随主题漂移。
///
/// 这是家族里辨识度最高的一块，OneIMS / OneKuku / OneTools 三边一致，
/// 所以它刻意不走 `colorScheme`——动态色开着的时候也得是这副样子。
abstract final class OneHero {
  /// 稳态（就绪）卡面。
  static const Color surface = Colors.white;

  /// 稳态卡面上的内容色。
  static const Color onSurface = oneInk;

  /// 主按钮：白底黑字药丸。只在非稳态出现，那时卡面是粉色或灰色，不会白压白。
  static const Color buttonSurface = Colors.white;
  static const Color onButtonSurface = Colors.black;
}

/// 亮色：背景 `#F9F9FF`，内容 `#1A1B20`。
const ColorScheme oneBlackWhiteLight = ColorScheme(
  brightness: Brightness.light,
  primary: oneInk,
  onPrimary: Colors.white,
  primaryContainer: Color(0xFFF0F0F4),
  onPrimaryContainer: oneInk,
  secondary: Color(0xFF575E71),
  onSecondary: Colors.white,
  secondaryContainer: Color(0xFFE8E8EF),
  onSecondaryContainer: Color(0xFF141B2C),
  tertiary: Color(0xFF5F5E62),
  onTertiary: Colors.white,
  tertiaryContainer: Color(0xFFE8E8EF),
  onTertiaryContainer: Color(0xFF1B1B1F),
  error: Color(0xFFBA1A1A),
  onError: Colors.white,
  errorContainer: Color(0xFFF9DEDC),
  onErrorContainer: Color(0xFF410E0B),
  surface: Color(0xFFF9F9FF),
  onSurface: oneInk,
  surfaceDim: Color(0xFFDAD9E0),
  surfaceBright: Color(0xFFF9F9FF),
  surfaceContainerLowest: Colors.white,
  surfaceContainerLow: Color(0xFFF3F3FA),
  surfaceContainer: Color(0xFFEDEDF4),
  surfaceContainerHigh: Color(0xFFE7E8EE),
  surfaceContainerHighest: Color(0xFFE2E2E9),
  onSurfaceVariant: Color(0xFF44474F),
  outline: Color(0xFF74777F),
  outlineVariant: Color(0xFFC4C6D0),
  shadow: Colors.black,
  scrim: Colors.black,
  inverseSurface: Color(0xFF2F3036),
  onInverseSurface: Color(0xFFF0F0F7),
  inversePrimary: Colors.white,
  surfaceTint: Color(0xFF74777F),
);

/// 暗色：背景 `#111318`，内容 `#E2E2E9`，强调色回到纯白。
const ColorScheme oneBlackWhiteDark = ColorScheme(
  brightness: Brightness.dark,
  primary: Colors.white,
  onPrimary: oneInk,
  primaryContainer: Color(0xFF2B2930),
  onPrimaryContainer: Color(0xFFE2E2E9),
  secondary: Color(0xFFC8C5D0),
  onSecondary: Color(0xFF2A3042),
  secondaryContainer: Color(0xFF3F4759),
  onSecondaryContainer: Color(0xFFE4E7F6),
  tertiary: Color(0xFFC8C5D0),
  onTertiary: Color(0xFF303034),
  tertiaryContainer: Color(0xFF48464C),
  onTertiaryContainer: Color(0xFFE5E1E6),
  error: Color(0xFFFFB4AB),
  onError: Color(0xFF690005),
  errorContainer: Color(0xFF93000A),
  onErrorContainer: Color(0xFFFFDAD6),
  surface: Color(0xFF111318),
  onSurface: Color(0xFFE2E2E9),
  surfaceDim: Color(0xFF111318),
  surfaceBright: Color(0xFF37393E),
  surfaceContainerLowest: Color(0xFF0C0E13),
  surfaceContainerLow: Color(0xFF191C20),
  surfaceContainer: Color(0xFF1D2024),
  surfaceContainerHigh: Color(0xFF282A2F),
  surfaceContainerHighest: Color(0xFF33353A),
  onSurfaceVariant: Color(0xFFC4C6D0),
  outline: Color(0xFF8E9099),
  outlineVariant: Color(0xFF44474F),
  shadow: Colors.black,
  scrim: Colors.black,
  inverseSurface: Color(0xFFE2E2E9),
  onInverseSurface: Color(0xFF2F3036),
  inversePrimary: oneInk,
  surfaceTint: Color(0xFF8E9099),
);
