import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:manager_flutter/onetools/dot_matrix_face.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/onetools/one_palette.dart';

/// 通道三态。顺序即阶段顺序：需要动手 → 忙 → 办妥。
///
/// 原版曾把「就绪」再拆出一个「休眠（授权好但闲置）」，但这个区分对用户没有
/// 可操作差异——都授权好了，闲不闲置不改变他下一步要做什么。故并回就绪，
/// 首页只留三态框。
enum OneChannelState {
  /// 通道没起来——需要用户动手，卡面转红。
  inactive,

  /// 正在激活，忙态。
  activating,

  /// 就绪（含原「休眠」：授权完成，无论是否正在用都算办妥）。
  ready,
}

/// 三态的行为判定，照搬原版 `ChannelCardPolicy`。
extension OneChannelStatePolicy on OneChannelState {
  /// 需要用户注意：卡面走 errorContainer。
  bool get isAlert => this == OneChannelState.inactive;

  /// 忙：按钮转圈且不可点。
  bool get isBusy => this == OneChannelState.activating;

  /// 稳态：收起副文案与主按钮，只留一行标题加状态药丸。
  ///
  /// 这条是整张卡的克制来源——事情办妥了就不该继续占版面喊话。
  bool get isSettled => this == OneChannelState.ready;

  /// 阶段进度点亮到第几颗（1..3）。
  int get litStageCount => index + 1;
}

/// One 家族的门面卡：三态 + 阶段进度 + 状态药丸。
///
/// 材质走液态玻璃（真模糊 + 通透填充 + 发丝边）。左侧状态标仍是原版对号 /
/// 警告 / 刷新；点阵笑/哭脸放在标题右侧空隙，状态不只靠颜色。
class OneStatusHero extends StatelessWidget {
  const OneStatusHero({
    super.key,
    required this.state,
    this.eyebrow,
    required this.title,
    required this.pill,
    required this.stageLabels,
    this.subtitle,
    this.detail,
    this.actionLabel,
    this.actionSub,
    this.onAction,
    this.secondaryActionLabel,
    this.onSecondaryAction,
    this.busyLabel,
    this.trailing,
  }) : assert(stageLabels.length == 3, '阶段进度固定三段，与三态一一对应');

  final OneChannelState state;

  /// 卡片顶部的小字眉题。为空时整行不渲染。
  final String? eyebrow;
  final String title;

  /// 标题右侧的状态药丸。
  final String pill;

  /// 三段阶段标签，顺序对应 [OneChannelState] 的三个取值。
  final List<String> stageLabels;

  /// 副标题与详情：只在非稳态显示。
  final String? subtitle;
  final String? detail;

  /// 主按钮：只在非稳态显示。
  final String? actionLabel;
  final String? actionSub;
  final VoidCallback? onAction;

  /// 左侧次按钮（OneKuku「配对」）。与 [actionLabel] 同行；为空则只渲染主按钮。
  final String? secondaryActionLabel;
  final VoidCallback? onSecondaryAction;

  /// 忙态按钮文案；不传保持原版「处理中」。
  final String? busyLabel;

  /// 右上角挂件（例如设备详情入口）。
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final dark = theme.brightness == Brightness.dark;

    final content = scheme.onSurface;
    final glass = Glass.fill(scheme, dark);
    final fill = switch (state) {
      _ when state.isAlert => scheme.errorContainer.withValues(alpha: 0.38),
      _ when state.isBusy => scheme.primaryContainer.withValues(alpha: 0.32),
      _ => glass,
    };
    final mood = state.isSettled ? DotMatrixMood.smile : DotMatrixMood.frown;
    // 左侧状态标仍是原版对号 / 警告 / 刷新，点阵脸只填标题右侧空隙。
    final icon = switch (state) {
      _ when state.isAlert => Icons.warning_rounded,
      _ when state.isBusy => Icons.refresh_rounded,
      _ => Icons.check_circle_rounded,
    };
    // 圆角走全局门面卡标度（22）；左右留白交给页面统一外边距。
    const radius = Glass.radiusHero;

    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        boxShadow: Glass.shadow(dark, strong: true),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: BackdropFilter(
          filter: ImageFilter.blur(
            sigmaX: Glass.blurBar,
            sigmaY: Glass.blurBar,
          ),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: fill,
              border: Border.all(
                color: Glass.stroke(scheme, dark),
                width: Glass.strokeWidth,
              ),
              borderRadius: BorderRadius.circular(radius),
            ),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _header(
                    theme,
                    scheme,
                    content,
                    icon,
                    mood,
                    semanticLabel: switch (state) {
                      OneChannelState.inactive => stageLabels[0],
                      OneChannelState.activating => stageLabels[1],
                      OneChannelState.ready => stageLabels[2],
                    },
                  ),
                  const SizedBox(height: 18),
                  _StageProgress(
                    litCount: state.litStageCount,
                    labels: stageLabels,
                    contentColor: content,
                  ),
                  if (!state.isSettled && actionLabel != null) ...[
                    const SizedBox(height: 18),
                    _action(theme, content),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _header(
    ThemeData theme,
    ColorScheme scheme,
    Color content,
    IconData icon,
    DotMatrixMood mood, {
    required String semanticLabel,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 38, color: content),
        const SizedBox(width: 18),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (eyebrow != null) ...[
                Text(
                  eyebrow!,
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: content.withValues(alpha: 0.72),
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
              ],
              Row(
                children: [
                  Flexible(
                    child: Text(
                      title,
                      style: theme.textTheme.titleLarge?.copyWith(
                        color: content,
                        fontWeight: FontWeight.w600,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 10),
                  _pill(theme, content),
                ],
              ),
              if (!state.isSettled) ...[
                if (subtitle != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    subtitle!,
                    style: theme.textTheme.bodyLarge?.copyWith(color: content),
                  ),
                ],
                if (detail != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    detail!,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: content.withValues(alpha: 0.78),
                    ),
                  ),
                ],
              ],
            ],
          ),
        ),
        const SizedBox(width: 12),
        _FaceWell(
          mood: mood,
          content: content,
          scheme: scheme,
          semanticLabel: semanticLabel,
        ),
        if (trailing != null) ...[const SizedBox(width: 8), trailing!],
      ],
    );
  }

  Widget _pill(ThemeData theme, Color content) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: content.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        pill,
        style: theme.textTheme.labelLarge?.copyWith(
          color: content,
          fontWeight: FontWeight.w600,
        ),
        maxLines: 1,
      ),
    );
  }

  ButtonStyle get _heroButtonStyle => FilledButton.styleFrom(
    // 家族的主动作一律白底黑字药丸，不吃 primary：它只在告警/忙态
    // 出现，那时卡面是粉或灰，不会白压白。
    backgroundColor: OneHero.buttonSurface,
    foregroundColor: OneHero.onButtonSurface,
    disabledBackgroundColor: OneHero.buttonSurface.withValues(alpha: 0.38),
    disabledForegroundColor: OneHero.onButtonSurface.withValues(alpha: 0.38),
    minimumSize: const Size(0, 52),
    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    shape: const StadiumBorder(),
  );

  Widget _heroButton({
    required String label,
    required VoidCallback? onPressed,
    bool expanded = false,
  }) {
    final button = FilledButton(
      onPressed: onPressed,
      style: _heroButtonStyle,
      child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
    );
    return expanded ? Expanded(child: button) : button;
  }

  Widget _action(ThemeData theme, Color content) {
    final busy = state.isBusy;
    final showPair = !busy &&
        secondaryActionLabel != null &&
        secondaryActionLabel!.isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (busy)
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: null,
              style: _heroButtonStyle,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: OneHero.onButtonSurface,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Text(busyLabel ?? '处理中'),
                ],
              ),
            ),
          )
        else if (showPair)
          Row(
            children: [
              _heroButton(
                label: secondaryActionLabel!,
                onPressed: onSecondaryAction,
                expanded: true,
              ),
              const SizedBox(width: 10),
              _heroButton(
                label: actionLabel ?? '',
                onPressed: onAction,
                expanded: true,
              ),
            ],
          )
        else
          SizedBox(
            width: double.infinity,
            child: _heroButton(
              label: actionLabel ?? '',
              onPressed: onAction,
            ),
          ),
        if (actionSub != null) ...[
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              actionSub!,
              style: theme.textTheme.bodySmall?.copyWith(
                color: content.withValues(alpha: 0.72),
              ),
            ),
          ),
        ],
      ],
    );
  }
}

/// 点阵脸坐在一层更实的小玻璃砖上，对齐参考图里嵌套的磨砂方块。
class _FaceWell extends StatelessWidget {
  const _FaceWell({
    required this.mood,
    required this.content,
    required this.scheme,
    required this.semanticLabel,
  });

  final DotMatrixMood mood;
  final Color content;
  final ColorScheme scheme;
  final String semanticLabel;

  @override
  Widget build(BuildContext context) {
    final dark = Glass.isDark(Theme.of(context));
    const radius = Glass.radiusTile;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Glass.fillStrong(scheme, dark),
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(
          color: Glass.stroke(scheme, dark),
          width: Glass.strokeWidth,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: DotMatrixFace(
          mood: mood,
          color: content,
          size: 36,
          semanticLabel: semanticLabel,
        ),
      ),
    );
  }
}

/// 三段阶段进度：圆点 + 连线 + 标签，点亮到 [litCount]。
class _StageProgress extends StatelessWidget {
  const _StageProgress({
    required this.litCount,
    required this.labels,
    required this.contentColor,
  });

  final int litCount;
  final List<String> labels;
  final Color contentColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final children = <Widget>[];

    for (var i = 0; i < labels.length; i++) {
      final lit = i < litCount;
      final stageColor = lit
          ? contentColor
          : contentColor.withValues(alpha: 0.32);

      if (i > 0) {
        children.add(
          Expanded(
            child: Container(
              height: 2,
              // 连线只在「已经走过这一段」时点亮，所以判据和圆点一致。
              color: contentColor.withValues(alpha: lit ? 0.55 : 0.18),
            ),
          ),
        );
      }

      children.add(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(
                color: stageColor,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              labels[i],
              style: theme.textTheme.labelSmall?.copyWith(color: stageColor),
              maxLines: 1,
            ),
          ],
        ),
      );
    }

    return Row(crossAxisAlignment: CrossAxisAlignment.center, children: children);
  }
}
