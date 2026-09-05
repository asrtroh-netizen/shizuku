import 'package:flutter/material.dart';
import 'package:manager_flutter/onetools/glass.dart';

/// 高价值 chrome 的液态玻璃面：取样背景 → 轻模糊 → 轻微透镜 → 可读性罩。
///
/// 只给悬浮底栏用。列表卡 / StatusHero 继续走 [GlassPanel] 或半透明皮，禁止套本组件。
class LiquidGlass extends StatefulWidget {
  const LiquidGlass({
    super.key,
    required this.child,
    this.radius = Glass.radiusCard,
    this.blur = Glass.blurLiquid,
    this.refraction = Glass.liquidRefraction,
  });

  final Widget child;
  final double radius;
  final double blur;
  final double refraction;

  @override
  State<LiquidGlass> createState() => _LiquidGlassState();
}

class _LiquidGlassState extends State<LiquidGlass> {
  Size _size = Size.zero;

  void _scheduleMeasure() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final box = context.findRenderObject() as RenderBox?;
      if (box == null || !box.hasSize) return;
      if (box.size == _size) return;
      setState(() => _size = box.size);
    });
  }

  @override
  Widget build(BuildContext context) {
    _scheduleMeasure();
    final theme = Theme.of(context);
    final dark = Glass.isDark(theme);
    final scheme = theme.colorScheme;
    final radius = BorderRadius.circular(widget.radius);
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: radius,
        boxShadow: Glass.shadow(dark, strong: true),
      ),
      child: ClipRRect(
        borderRadius: radius,
        child: BackdropFilter(
          filter: Glass.liquidBackdropFilter(
            _size,
            blur: widget.blur,
            refraction: widget.refraction,
          ),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: Glass.liquidFill(scheme, dark),
              borderRadius: radius,
              border: Border.all(
                color: Glass.stroke(scheme, dark),
                width: Glass.strokeWidth,
              ),
            ),
            child: Stack(
              children: [
                Positioned.fill(
                  child: IgnorePointer(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: Glass.liquidHighlight(dark),
                        borderRadius: radius,
                      ),
                    ),
                  ),
                ),
                widget.child,
              ],
            ),
          ),
        ),
      ),
    );
  }
}
