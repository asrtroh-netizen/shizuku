import 'package:flutter/material.dart';
import 'package:manager_flutter/home/home_models.dart';

// 首页页头：右上角 Lang 芯片 + 日/月滑块（从 home_screen.dart 原样搬出，只改名加前缀）。

class HomeTopCapsules extends StatelessWidget {
  const HomeTopCapsules({
    super.key,
    required this.copy,
    required this.dark,
    required this.onLang,
    required this.onTheme,
  });

  final HomeCopy copy;
  final bool dark;
  final VoidCallback onLang;
  final VoidCallback onTheme;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Spacer(),
        HomeLangChip(label: copy.lang, onTap: onLang),
        const SizedBox(width: 8),
        HomeThemeSlide(dark: dark, copy: copy, onTap: onTheme),
      ],
    );
  }
}

class HomeLangChip extends StatelessWidget {
  const HomeLangChip({super.key, required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Material(
      color: cs.primary,
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(999),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 6,
                height: 6,
                decoration: BoxDecoration(
                  color: cs.onPrimary,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                label,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: cs.onPrimary,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class HomeThemeSlide extends StatelessWidget {
  const HomeThemeSlide({
    super.key,
    required this.dark,
    required this.copy,
    required this.onTap,
  });

  final bool dark;
  final HomeCopy copy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    const trackW = 56.0;
    const trackH = 30.0;
    const knob = 24.0;
    const pad = 3.0;
    final cs = Theme.of(context).colorScheme;
    final iconTint = cs.onSurface;
    final offset = dark ? trackW - knob - pad : pad;
    return Semantics(
      button: true,
      label: dark ? copy.themeDark : copy.themeLight,
      child: GestureDetector(
        onTap: onTap,
        child: SizedBox(
          width: trackW,
          height: trackH,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: ColoredBox(
              color: cs.surfaceContainerHighest,
              child: Stack(
                children: [
                  Positioned.fill(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 7),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.light_mode_outlined,
                            size: 14,
                            color: iconTint.withValues(alpha: dark ? 0.35 : 0.9),
                          ),
                          Icon(
                            Icons.dark_mode_outlined,
                            size: 14,
                            color: iconTint.withValues(alpha: dark ? 0.9 : 0.35),
                          ),
                        ],
                      ),
                    ),
                  ),
                  AnimatedPositioned(
                    duration: const Duration(milliseconds: 220),
                    curve: Curves.easeOut,
                    left: offset,
                    top: pad,
                    width: knob,
                    height: knob,
                    child: DecoratedBox(
                      decoration: const BoxDecoration(
                        color: Colors.white,
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            blurRadius: 2,
                            offset: Offset(0, 1),
                            color: Color(0x33000000),
                          ),
                        ],
                      ),
                      child: Center(
                        child: Icon(
                          dark
                              ? Icons.dark_mode_outlined
                              : Icons.light_mode_outlined,
                          size: 14,
                          color: iconTint,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
