import 'package:flutter/material.dart';
import 'package:manager_flutter/onetools/glass.dart';

/// 弹窗语气。
///
/// [neutral]：玻璃面板（首页 / 设置页的确认弹窗）。
/// [error]：`errorContainer` 底 + `onErrorContainer` 字、无模糊（应用页「ADB 受限」，
/// 对齐 Compose 原样）；确认键退为同色 [TextButton]，不在错误面上再压一枚主色 [FilledButton]。
enum GlassAlertTone { neutral, error }

/// 确认类弹窗：原 `home_screen` / `apps_screen` / `settings_screen` 三份私有
/// `_GlassAlert` 的并集，任何一处只改构造名即可等价迁移。
///
/// 用法沿用首页：`showDialog(context: context, builder: (_) => GlassAlert(...))`，
/// 不传 `barrierColor`、不包 [GlassDialogBackdrop]。
///
/// 按钮顺序（从左到右）：取消 → 第三键 → 确认；取消键只在 [cancelLabel] 非空时出现
/// （原首页 `showCancel: false` ⇔ 这里不传 [cancelLabel]），第三键只在 [extraLabel]
/// 非空时出现。
///
/// `Navigator.pop` 的返回值（哨兵与原首页实现逐字一致）：
/// - 取消 → `false`
/// - 第三键 → `'extra'`
/// - 确认 → 没有 [extraLabel] 时 `true`；有 [extraLabel] 时 `'confirm'`
///   （三键弹窗的结果类型因此是 `String` 或 `false`，首页「查看命令」据此区分复制 / 发送）
/// - 点遮罩关闭 → `null`（`showDialog` 默认）
///
/// **调用方的类型参数**：带 [extraLabel] 时结果混有 `String` 与 `bool`，必须用
/// `showDialog<Object?>`；写成 `showDialog<String>` 会在按取消时抛 `TypeError` 且弹窗关不掉
/// （R2 I-5 修过的 bug）。不带 [extraLabel] 时用 `showDialog<bool>` 即可。
///
/// 迁移差异说明：原 apps 版确认键 `pop()` 回 `null`，其调用方是 `showDialog<void>` 且忽略结果，
/// 换成本组件后回 `true`，对该调用方无差别；原 settings 版取消 / 确认即 `false` / `true`，不变。
///
/// [icon] 非空时画在标题之上并居中，标题随之居中（apps 版形态）；颜色随语气：
/// 错误面 `onErrorContainer`，玻璃面 `colorScheme.primary`。
/// [monospace] 只影响正文字体（首页 ADB 命令）。
class GlassAlert extends StatelessWidget {
  const GlassAlert({
    super.key,
    required this.title,
    required this.body,
    required this.confirmLabel,
    this.cancelLabel,
    this.extraLabel,
    this.monospace = false,
    this.tone = GlassAlertTone.neutral,
    this.icon,
  });

  final String title;
  final String body;
  final String confirmLabel;
  final String? cancelLabel;
  final String? extraLabel;
  final bool monospace;
  final GlassAlertTone tone;
  final IconData? icon;

  /// 正文可滚动区高度上限（三份原实现均为 240）。
  static const double _maxBodyHeight = 240;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final text = theme.textTheme;
    final error = tone == GlassAlertTone.error;
    final Color? fg = error ? cs.onErrorContainer : null;
    final ButtonStyle? textButtonStyle =
        error ? TextButton.styleFrom(foregroundColor: cs.onErrorContainer) : null;
    final cancelLabel = this.cancelLabel;
    final extraLabel = this.extraLabel;
    final icon = this.icon;
    final Object confirmResult = extraLabel != null ? 'confirm' : true;

    final content = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (icon != null) ...[
          Icon(icon, color: fg ?? cs.primary),
          const SizedBox(height: Glass.space12),
        ],
        Text(
          title,
          textAlign: icon != null ? TextAlign.center : null,
          style: text.titleMedium?.copyWith(color: fg),
        ),
        const SizedBox(height: Glass.space12),
        ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: _maxBodyHeight),
          child: SingleChildScrollView(
            child: Text(
              body,
              style: text.bodyMedium?.copyWith(
                color: fg,
                fontFamily: monospace ? 'monospace' : null,
              ),
            ),
          ),
        ),
        const SizedBox(height: Glass.space16),
        Wrap(
          alignment: WrapAlignment.end,
          spacing: Glass.space8,
          children: [
            if (cancelLabel != null)
              TextButton(
                style: textButtonStyle,
                onPressed: () => Navigator.pop(context, false),
                child: Text(cancelLabel),
              ),
            if (extraLabel != null)
              TextButton(
                style: textButtonStyle,
                onPressed: () => Navigator.pop(context, 'extra'),
                child: Text(extraLabel),
              ),
            if (error)
              TextButton(
                style: textButtonStyle,
                onPressed: () => Navigator.pop(context, confirmResult),
                child: Text(confirmLabel),
              )
            else
              FilledButton(
                onPressed: () => Navigator.pop(context, confirmResult),
                child: Text(confirmLabel),
              ),
          ],
        ),
      ],
    );

    return Dialog(
      backgroundColor: Colors.transparent,
      child: error
          ? Material(
              color: cs.errorContainer,
              borderRadius: BorderRadius.circular(Glass.radiusHero),
              child: Padding(
                padding: const EdgeInsets.all(Glass.padCard),
                child: content,
              ),
            )
          : GlassPanel(
              radius: Glass.radiusHero,
              padding: const EdgeInsets.all(Glass.padCard),
              child: content,
            ),
    );
  }
}
