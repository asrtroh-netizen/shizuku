import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:manager_flutter/onetools/glass.dart';

/// 居中玻璃选择面：独家身份识别 / 信号格 / APN 救急共用同一框体。
///
/// [children] 路径按内容定高（身份识别那种小框）；[body] 路径给目录类，
/// 在上限内吃剩余高度，好让列表滚动而不是把卡片撑成贴底大面。
class GlassChoiceDialog extends StatelessWidget {
  const GlassChoiceDialog({
    super.key,
    required this.title,
    this.headerTrailing,
    this.children = const <Widget>[],
    this.body,
    this.closeTooltip = 'Close',
  });

  final String title;
  final Widget? headerTrailing;
  final List<Widget> children;
  final Widget? body;
  final String closeTooltip;

  static const BorderRadius _radius = BorderRadius.all(
    Radius.circular(Glass.radiusHero),
  );

  /// 内容定高小框的高度上限；目录类也用它，避免再回到 82% 贴屏。
  static const double _maxCardHeight = 420;

  static const Key cardKey = ValueKey<String>('glass-choice-card');

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = Glass.isDark(theme);
    final keyboard = MediaQuery.viewInsetsOf(context).bottom > 0;
    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: EdgeInsets.symmetric(
        horizontal: 24,
        vertical: keyboard ? 12 : 40,
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final maxH = math.min(constraints.maxHeight, _maxCardHeight);
          return ConstrainedBox(
            constraints: BoxConstraints(maxWidth: 420, maxHeight: maxH),
            child: SizedBox(
              width: double.infinity,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: _radius,
                  boxShadow: Glass.shadow(dark, strong: true),
                ),
                child: ClipRRect(
                  key: cardKey,
                  borderRadius: _radius,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: Glass.dialogFill(theme.colorScheme, dark),
                      borderRadius: _radius,
                      border: Border.all(
                        color: Glass.stroke(theme.colorScheme, dark),
                        width: Glass.strokeWidth,
                      ),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Stack(
                          children: [
                            Padding(
                              padding: const EdgeInsets.fromLTRB(48, 20, 48, 12),
                              child: Column(
                                children: [
                                  Text(
                                    title,
                                    textAlign: TextAlign.center,
                                    style: theme.textTheme.titleLarge?.copyWith(
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  if (headerTrailing != null) ...[
                                    const SizedBox(height: 12),
                                    Center(child: headerTrailing),
                                  ],
                                ],
                              ),
                            ),
                            Positioned(
                              top: 4,
                              right: 4,
                              child: IconButton(
                                onPressed: () => Navigator.of(context).pop(),
                                tooltip: closeTooltip,
                                icon: Icon(
                                  Icons.close,
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ),
                          ],
                        ),
                        Divider(
                          height: 1,
                          color: theme.colorScheme.outlineVariant.withValues(
                            alpha: 0.45,
                          ),
                        ),
                        Flexible(
                          fit: FlexFit.loose,
                          child:
                              body ??
                              ListView(
                                shrinkWrap: true,
                                padding: const EdgeInsets.fromLTRB(8, 8, 8, 16),
                                children: children,
                              ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

/// 弹层里的嵌套小框。父面 radiusHero=22、水平 inset=8 → 子面 14，同心嵌套。
class GlassChoiceWell extends StatelessWidget {
  const GlassChoiceWell({
    super.key,
    required this.child,
    this.onTap,
    this.enabled = true,
    this.emphasize = false,
    this.padding = const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
  });

  final Widget child;
  final VoidCallback? onTap;
  final bool enabled;
  final bool emphasize;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final dark = Glass.isDark(theme);
    final radius = BorderRadius.circular(Glass.radiusTile);
    final fill = emphasize
        ? Color.alphaBlend(
            scheme.primary.withValues(alpha: dark ? 0.22 : 0.12),
            Glass.fill(scheme, dark),
          )
        : Glass.fill(scheme, dark);
    final borderColor = emphasize
        ? scheme.primary.withValues(alpha: dark ? 0.45 : 0.35)
        : Glass.stroke(scheme, dark);
    final body = Padding(padding: padding, child: child);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: fill,
          borderRadius: radius,
          border: Border.all(color: borderColor, width: Glass.strokeWidth),
        ),
        child: Material(
          type: MaterialType.transparency,
          borderRadius: radius,
          clipBehavior: Clip.antiAlias,
          child: onTap == null
              ? body
              : InkWell(onTap: enabled ? onTap : null, child: body),
        ),
      ),
    );
  }
}
