import 'package:flutter/material.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/terminal/terminal_channel.dart';
import 'package:manager_flutter/terminal/terminal_models.dart';

/// 原 Compose `ShellTutorialActivity` 的整页换皮：rish 教程（底栏 Tab，无返回箭头）。
///
/// 业务逻辑全部在 Kotlin（`shizuku/terminal`）：导出 / 打开指南只发方法名。
/// 页面 = 页头 + 4 张页面级 [GlassPanel]（说明卡 + 三步），与 Compose 的
/// `CalloutCard` + 3 × `StepCard` 一一对应（RULEBOOK §3.1 一页 ≤ 4 张）。
/// 不订阅任何事件通道（RULEBOOK §1.1 v1.2 / GAP-15）：`AppShell` 每次切到本 Tab
/// 都用新 `ValueKey` 重建本页，加上 `initState` 拉一次、`resumed` 重拉，足够保证新鲜度。
class TerminalScreen extends StatefulWidget {
  const TerminalScreen({super.key});

  @override
  State<TerminalScreen> createState() => _TerminalScreenState();
}

class _TerminalScreenState extends State<TerminalScreen>
    with WidgetsBindingObserver {
  TerminalSnapshot _snap = TerminalSnapshot.empty;
  int _refreshSeq = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final seq = ++_refreshSeq;
    final map = await TerminalChannel.getState();
    if (!mounted || seq != _refreshSeq) return;
    setState(() => _snap = TerminalSnapshot.fromJson(map));
  }

  @override
  Widget build(BuildContext context) {
    final snap = _snap;
    final copy = snap.copy;
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        bottom: false,
        child: ListView(
          padding: EdgeInsets.fromLTRB(
            Glass.pageMargin,
            Glass.space12,
            Glass.pageMargin,
            glassDockScrollPadding(context),
          ),
          children: [
            Text(copy.title, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: Glass.cardGap),
            _CalloutCard(
              text: copy.rishDescription,
              openLabel: copy.open,
              onTap: TerminalChannel.openGuide,
            ),
            const SizedBox(height: Glass.cardGap),
            _StepCard(
              icon: Icons.looks_one_outlined,
              title: copy.tutorial1,
              code: [snap.shName, snap.dexName],
              summary: copy.tutorial1Description,
              action: FilledButton(
                onPressed: TerminalChannel.exportFiles,
                child: Text(copy.exportFiles),
              ),
            ),
            const SizedBox(height: Glass.cardGap),
            _StepCard(
              icon: Icons.looks_two_outlined,
              title: copy.tutorial2,
              summary: copy.tutorial2Description,
            ),
            const SizedBox(height: Glass.cardGap),
            _StepCard(
              icon: Icons.looks_3_outlined,
              title: copy.tutorial3,
              summary: copy.tutorial3Description,
            ),
          ],
        ),
      ),
    );
  }
}

/// 说明卡（对齐 Compose `CalloutCard`）：整卡可点 = 打开 rish 文档；
/// 尾部 open-in-new 图标的语义标签 = `copy.open`（Compose 的 contentDescription）。
/// 标题不再重复：页头已经是同一条 `home_terminal_title`。
class _CalloutCard extends StatelessWidget {
  const _CalloutCard({
    required this.text,
    required this.openLabel,
    required this.onTap,
  });

  final String text;
  final String openLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GlassPanel(
      onTap: onTap,
      padding: const EdgeInsets.all(Glass.padCard),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.help_outline, color: cs.tertiary),
          const SizedBox(width: Glass.space16),
          Expanded(
            child: Text(
              text,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: cs.onSurfaceVariant,
              ),
            ),
          ),
          const SizedBox(width: Glass.space12),
          Icon(
            Icons.open_in_new_outlined,
            semanticLabel: openLabel,
            color: cs.primary,
          ),
        ],
      ),
    );
  }
}

/// 步骤卡（对齐 Compose `StepCard`）：序号图标 + 标题 + 可选代码块 + 说明 + 可选动作按钮。
class _StepCard extends StatelessWidget {
  const _StepCard({
    required this.icon,
    required this.title,
    required this.summary,
    this.code,
    this.action,
  });

  final IconData icon;
  final String title;
  final String summary;
  final List<String>? code;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final text = theme.textTheme;
    final code = this.code;
    final action = this.action;
    return GlassPanel(
      padding: const EdgeInsets.all(Glass.padCard),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: cs.onSurfaceVariant),
          const SizedBox(width: Glass.space12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: text.titleMedium),
                if (code != null) ...[
                  const SizedBox(height: Glass.space8),
                  _CodeBlock(lines: code),
                ],
                const SizedBox(height: Glass.space8),
                Text(
                  summary,
                  style: text.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
                ),
                if (action != null) ...[
                  const SizedBox(height: Glass.space12),
                  Align(alignment: Alignment.centerRight, child: action),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 等宽代码块：文件名 / 命令逐行展示。底色与描边走 [Glass] 令牌（通透面 + 发丝边，无模糊）。
class _CodeBlock extends StatelessWidget {
  const _CodeBlock({required this.lines});

  final List<String> lines;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final dark = Glass.isDark(theme);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: Glass.space12,
        vertical: Glass.space8,
      ),
      decoration: BoxDecoration(
        color: Glass.fill(cs, dark),
        borderRadius: BorderRadius.circular(Glass.radiusTile),
        border: Border.all(
          color: Glass.stroke(cs, dark),
          width: Glass.strokeWidth,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final line in lines)
            Text(
              line,
              style: theme.textTheme.bodyMedium?.copyWith(
                fontFamily: 'monospace',
              ),
            ),
        ],
      ),
    );
  }
}
