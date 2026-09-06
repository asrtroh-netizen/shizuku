import 'package:flutter/material.dart';
import 'package:manager_flutter/onetools/glass.dart';

/// 占位 / 空状态 / 提示卡：一张 [GlassPanel] + 左侧 `colorScheme.primary` 图标 + 文案。
///
/// 原 `nav/app_shell.dart` 的 `_PlaceholderTab` 卡、`apps/apps_screen.dart` 的 `_NoticeCard`、
/// 配对页「不支持」占位统一用它（RULEBOOK R2 §R4）。
///
/// 只传 [text] 时它就是这张卡的唯一一行，按 `titleMedium w700` 画——与 `_PlaceholderTab`
/// 逐像素一致；再传 [title] 时 [title] 成为标题（`titleMedium w700`），[text] 退为其下的
/// 正文（`bodyMedium`）。图标在行内竖直居中（与 [ListTile] 两行形态一致）。
class GlassNoticeCard extends StatelessWidget {
  const GlassNoticeCard({
    super.key,
    required this.icon,
    required this.text,
    this.title,
  });

  final IconData icon;
  final String text;
  final String? title;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final title = this.title;
    final headline = theme.textTheme.titleMedium?.copyWith(
      fontWeight: FontWeight.w700,
    );
    return GlassPanel(
      padding: const EdgeInsets.all(Glass.padCard),
      child: Row(
        children: [
          Icon(icon, color: theme.colorScheme.primary),
          const SizedBox(width: Glass.space12),
          Expanded(
            child: title == null
                ? Text(text, style: headline)
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: headline),
                      const SizedBox(height: Glass.space4),
                      Text(text, style: theme.textTheme.bodyMedium),
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}
