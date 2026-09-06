import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:manager_flutter/apps/apps_channel.dart';
import 'package:manager_flutter/apps/apps_models.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/widgets/glass_alert.dart';
import 'package:manager_flutter/widgets/glass_notice_card.dart';

/// 原 Compose `AppsManagementActivity` 的整页换皮：授权应用列表（从首页推入）。
///
/// 业务逻辑全部在 Kotlin（`shizuku/apps`）：这里只画快照、转发点击。
/// 服务未运行时渲染占位卡、不渲染列表（GAP-3）；图标按需经 `getIcon` 拉 PNG 并按键缓存（GAP-4）。
/// 不订阅任何 EventChannel：每次推入都是新 State，加上 `initState` 拉一次、
/// `resumed` 重拉、`toggle` 直接带回快照，共同保证新鲜度。
class AppsScreen extends StatefulWidget {
  const AppsScreen({super.key});

  @override
  State<AppsScreen> createState() => _AppsScreenState();
}

class _AppsScreenState extends State<AppsScreen> with WidgetsBindingObserver {
  static const double _iconDp = 40;

  AppsSnapshot _snap = AppsSnapshot.empty;
  int _refreshSeq = 0;

  /// 图标缓存，键 = [appKey]；值为 null 表示已请求但失败（显示占位图标）。
  final Map<String, Uint8List?> _icons = <String, Uint8List?>{};
  final Set<String> _iconRequests = <String>{};

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
    final map = await AppsChannel.getState();
    if (!mounted || seq != _refreshSeq) return;
    setState(() => _snap = AppsSnapshot.fromJson(map));
  }

  Future<void> _toggle(AppRow row) async {
    final map = await AppsChannel.toggle(row.packageName, row.uid);
    if (!mounted) return;
    if (map.containsKey('running') || map.containsKey('copy')) {
      // 写操作直接带回整页快照；让在途的 getState 作废，免得旧快照盖掉新状态。
      _refreshSeq++;
      setState(() => _snap = AppsSnapshot.fromJson(map));
    } else {
      await _refresh();
    }
    if (map['result'] != 'adbLimited' || !mounted) return;
    final copy = _snap.copy;
    await showDialog<void>(
      context: context,
      builder: (ctx) => GlassAlert(
        title: copy.adbLimitedTitle,
        body: copy.adbLimitedMessage,
        confirmLabel: copy.ok,
        tone: GlassAlertTone.error,
        icon: Icons.info_outline,
      ),
    );
  }

  void _ensureIcon(AppRow row, int sizePx) {
    final key = appKey(row);
    if (_icons.containsKey(key) || !_iconRequests.add(key)) return;
    AppsChannel.getIcon(row.packageName, row.uid, sizePx).then((bytes) {
      if (!mounted) return;
      setState(() => _icons[key] = bytes);
    });
  }

  @override
  Widget build(BuildContext context) {
    final snap = _snap;
    final copy = snap.copy;
    final rows = snap.running ? snap.apps : const <AppRow>[];
    final String? notice = !snap.running
        ? copy.notRunning
        : (rows.isEmpty ? copy.empty : null);
    final sizePx =
        (_iconDp * MediaQuery.devicePixelRatioOf(context)).round();
    final itemCount = 1 + (notice != null ? 1 : rows.length);

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        bottom: false,
        child: ListView.separated(
          padding: EdgeInsets.fromLTRB(
            Glass.pageMargin,
            Glass.space12,
            Glass.pageMargin,
            glassPageBottomPadding(context),
          ),
          itemCount: itemCount,
          separatorBuilder: (_, _) => const SizedBox(height: Glass.cardGap),
          itemBuilder: (context, index) {
            if (index == 0) {
              return _Header(title: copy.title, back: copy.back);
            }
            if (notice != null) {
              return GlassNoticeCard(icon: Icons.info_outline, text: notice);
            }
            final row = rows[index - 1];
            _ensureIcon(row, sizePx);
            return _AppCard(
              row: row,
              icon: _icons[appKey(row)],
              iconSize: _iconDp,
              requiresRootLabel: copy.requiresRoot,
              onToggle: () => _toggle(row),
            );
          },
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.title, required this.back});

  final String title;
  final String back;

  @override
  Widget build(BuildContext context) {
    final heading = Text(title, style: Theme.of(context).textTheme.titleLarge);
    if (!Navigator.canPop(context)) return heading;
    return Row(
      children: [
        IconButton(
          icon: const Icon(Icons.arrow_back_outlined),
          tooltip: back,
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        Expanded(child: heading),
      ],
    );
  }
}

/// 列表行：主题 `Card`（`Glass.apply` 已给半透明填充 + 发丝边，无模糊），
/// 不用 `GlassPanel`（RULEBOOK §3.2 v1.1）。外边距归零，让行宽与页头 / 占位卡对齐、
/// 行距只由 `Glass.cardGap` 决定。
class _AppCard extends StatelessWidget {
  const _AppCard({
    required this.row,
    required this.icon,
    required this.iconSize,
    required this.requiresRootLabel,
    required this.onToggle,
  });

  final AppRow row;
  final Uint8List? icon;
  final double iconSize;
  final String requiresRootLabel;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final text = theme.textTheme;
    return Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onToggle,
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: Glass.space20,
            vertical: 18,
          ),
          child: Row(
            children: [
              _AppIcon(bytes: icon, size: iconSize),
              const SizedBox(width: Glass.space16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(row.label, style: text.titleMedium),
                    const SizedBox(height: Glass.space4),
                    Text(
                      row.packageName,
                      style: text.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
                    ),
                    if (row.requiresRoot) ...[
                      const SizedBox(height: Glass.space6),
                      Text(
                        requiresRootLabel,
                        style: text.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                      ),
                    ],
                  ],
                ),
              ),
              Switch(value: row.granted, onChanged: (_) => onToggle()),
            ],
          ),
        ),
      ),
    );
  }
}

class _AppIcon extends StatelessWidget {
  const _AppIcon({required this.bytes, required this.size});

  final Uint8List? bytes;
  final double size;

  @override
  Widget build(BuildContext context) {
    final bytes = this.bytes;
    final fallback = Icon(
      Icons.android_outlined,
      color: Theme.of(context).colorScheme.onSurfaceVariant,
    );
    return ClipRRect(
      borderRadius: BorderRadius.circular(Glass.radiusTile),
      child: SizedBox(
        width: size,
        height: size,
        child: bytes == null
            ? fallback
            : Image.memory(
                bytes,
                width: size,
                height: size,
                fit: BoxFit.cover,
                gaplessPlayback: true,
                errorBuilder: (_, _, _) => fallback,
              ),
      ),
    );
  }
}
