import 'dart:async';

import 'package:flutter/material.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/pairing/pairing_channel.dart';
import 'package:manager_flutter/pairing/pairing_models.dart';

/// 原 Compose `AdbPairingTutorialActivity` 的整页换皮：无线配对引导（**推入页**，顶部返回箭头）。
///
/// 业务逻辑全部在 Kotlin（`shizuku/pairing`，逐行复刻 Activity 的状态机）：这里只画快照、转发点击。
/// 页面进入 = `getState` 画出来 → `start`（等价于 Activity `onCreate` 的按条件启动配对服务）；
/// `resumed` 重拉；订阅 `shizuku/pairing/events` 的 `"changed"` 后重拉（推入页允许 EventChannel，RULEBOOK §1.1 v1.2）。
///
/// 卡片按 Compose 顺序：通知 → 通知监听（仅自动配对开着时）→ 本地网络 → 服务启动失败 → MIUI 提示 → 三步引导。
/// 前三项合并为一张"状态"[GlassPanel]（分段），三步合并为一张，MIUI 一张；
/// "服务启动失败"是全页唯一用 `errorContainer` 的卡（主题 [Card] 换底色），其余警告只用 `error` 色的图标。
/// 同时在场的 [GlassPanel] 最多 3 张（服务启动失败与三步引导互斥），满足 RULEBOOK §3.1 的 ≤ 4。
class PairingScreen extends StatefulWidget {
  const PairingScreen({super.key});

  @override
  State<PairingScreen> createState() => _PairingScreenState();
}

class _PairingScreenState extends State<PairingScreen>
    with WidgetsBindingObserver {
  StreamSubscription<dynamic>? _sub;
  PairingSnapshot _snap = PairingSnapshot.empty;
  int _refreshSeq = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // 先订阅：Kotlin 以"事件流被监听"判定配对页在场，宿主 onResume 才会按条件补启动。
    _sub = PairingChannel.events.receiveBroadcastStream().listen(
      (_) {
        if (mounted) _refresh();
      },
      onError: (_) {},
    );
    _enter();
  }

  @override
  void dispose() {
    _sub?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  /// 页面进入（对齐 Activity `onCreate`）：先拉快照画出来，再让 Kotlin 跑 `start`。
  Future<void> _enter() async {
    await _refresh();
    await _apply(PairingChannel.start());
  }

  Future<void> _refresh() async {
    final seq = ++_refreshSeq;
    final map = await PairingChannel.getState();
    if (!mounted || seq != _refreshSeq) return;
    setState(() => _snap = PairingSnapshot.fromJson(map));
  }

  /// 会改状态的动作：带回整页快照就直接 `setState`（并让在途的 getState 作废），否则重拉。
  Future<void> _apply(Future<Map<String, dynamic>> call) async {
    final map = await call;
    if (!mounted) return;
    if (map.containsKey('copy')) {
      _refreshSeq++;
      setState(() => _snap = PairingSnapshot.fromJson(map));
    } else {
      await _refresh();
    }
  }

  void _requestLocalNetworkPermission() {
    _apply(PairingChannel.requestLocalNetworkPermission());
  }

  @override
  Widget build(BuildContext context) {
    final snap = _snap;
    final copy = snap.copy;
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: CustomScrollView(
        slivers: [
          GlassSliverAppBar(
            title: Text(copy.title),
            leading: IconButton(
              icon: const Icon(Icons.arrow_back_outlined),
              tooltip: copy.back,
              onPressed: () => Navigator.of(context).maybePop(),
            ),
          ),
          SliverPadding(
            padding: EdgeInsets.fromLTRB(
              Glass.pageMargin,
              Glass.space12,
              Glass.pageMargin,
              Glass.space24 + MediaQuery.viewPaddingOf(context).bottom,
            ),
            sliver: SliverList.list(children: _cards(snap)),
          ),
        ],
      ),
    );
  }

  List<Widget> _cards(PairingSnapshot snap) {
    final copy = snap.copy;
    if (!snap.supported) {
      // Activity 版整类 @RequiresApi(R)，没有对应文案；按 E4 用 copy.title 作占位（RULEBOOK §3.6 空状态形态）。
      return [
        _SectionPanel(
          rows: [
            _PairingRow(
              kind: _RowKind.callout,
              icon: Icons.info_outline,
              title: copy.title,
            ),
          ],
        ),
      ];
    }
    final cards = <Widget>[
      _SectionPanel(rows: _statusRows(snap)),
      if (snap.pairingServiceStartFailed)
        _ErrorCard(
          title: copy.serviceStartFailed,
          body: copy.pairingServiceFailed,
          action: copy.retry,
          onAction: _requestLocalNetworkPermission,
        ),
      if (snap.showMiuiHint)
        _SectionPanel(
          rows: [
            _PairingRow(
              kind: _RowKind.warning,
              icon: Icons.warning_amber_outlined,
              title: copy.miui,
              body: copy.miui2,
            ),
          ],
        ),
      if (snap.readyForPairing)
        _SectionPanel(
          rows: [
            _PairingRow(
              kind: _RowKind.step,
              icon: Icons.looks_one_outlined,
              title: copy.steps,
              body: copy.leftIsClickable,
              action: copy.developmentSettings,
              actionIcon: Icons.open_in_new_outlined,
              onAction: PairingChannel.openDeveloperOptions,
            ),
            _PairingRow(
              kind: _RowKind.step,
              icon: Icons.looks_two_outlined,
              title: copy.enterPairingCode,
            ),
            _PairingRow(
              kind: _RowKind.step,
              icon: Icons.looks_3_outlined,
              title: copy.finish,
            ),
          ],
        ),
    ];
    return [
      for (var i = 0; i < cards.length; i++) ...[
        if (i != 0) const SizedBox(height: Glass.cardGap),
        cards[i],
      ],
    ];
  }

  /// 状态卡的分段（同 Compose 前三个 item 的条件与顺序）。
  List<Widget> _statusRows(PairingSnapshot snap) {
    final copy = snap.copy;
    return [
      if (snap.notificationEnabled)
        _PairingRow(
          kind: _RowKind.callout,
          icon: Icons.notifications_active_outlined,
          title: copy.notification,
        )
      else
        _PairingRow(
          kind: _RowKind.warning,
          icon: Icons.warning_amber_outlined,
          title: copy.permissionMissing,
          body: copy.notificationBlocked,
          action: copy.notificationSettings,
          actionIcon: Icons.open_in_new_outlined,
          onAction: PairingChannel.openNotificationOptions,
        ),
      if (snap.showNotificationListenerHint)
        _PairingRow(
          kind: _RowKind.warning,
          icon: Icons.warning_amber_outlined,
          title: copy.permissionMissing,
          body: copy.autoPairingNotificationAccessTooltip,
          action: copy.ok,
          actionIcon: Icons.open_in_new_outlined,
          onAction: PairingChannel.openNotificationAccessSettings,
        ),
      if (snap.notificationEnabled)
        if (snap.localNetworkPermissionGranted)
          _PairingRow(
            kind: _RowKind.callout,
            icon: Icons.info_outline,
            title: copy.network,
            body: copy.networkLimitationNotForeground,
          )
        else
          _PairingRow(
            kind: _RowKind.warning,
            icon: Icons.warning_amber_outlined,
            title: copy.permissionMissing,
            body: copy.networkBlocked,
            action: copy.retry,
            actionIcon: Icons.refresh_outlined,
            onAction: _requestLocalNetworkPermission,
          ),
    ];
  }
}

/// 一张页面级玻璃卡，内部分段：行与行之间一条分割线（分割线的高度即段间距）。
class _SectionPanel extends StatelessWidget {
  const _SectionPanel({required this.rows});

  final List<Widget> rows;

  @override
  Widget build(BuildContext context) {
    return GlassPanel(
      padding: const EdgeInsets.all(Glass.padCard),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < rows.length; i++) ...[
            rows[i],
            if (i != rows.length - 1) const Divider(height: Glass.space24),
          ],
        ],
      ),
    );
  }
}

/// 三种行的着色（对齐 Compose 三种卡）：
/// 说明 = 主色图标 + 正文；警告 = `error` 色图标（**不**铺 errorContainer）；步骤 = 序号图标 + 标题级文字 + `error` 色补充说明。
enum _RowKind { callout, warning, step }

/// 图标 / 标题 / 可选正文 / 可选动作按钮的一段。
class _PairingRow extends StatelessWidget {
  const _PairingRow({
    required this.kind,
    required this.icon,
    required this.title,
    this.body,
    this.action,
    this.actionIcon,
    this.onAction,
  });

  final _RowKind kind;
  final IconData icon;
  final String title;
  final String? body;
  final String? action;
  final IconData? actionIcon;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final text = theme.textTheme;
    final body = this.body;
    final action = this.action;
    final onAction = this.onAction;
    final iconColor = switch (kind) {
      _RowKind.callout => cs.primary,
      _RowKind.warning => cs.error,
      _RowKind.step => cs.onSurfaceVariant,
    };
    final titleStyle =
        kind == _RowKind.step ? text.titleMedium : text.bodyLarge;
    final bodyColor = kind == _RowKind.step ? cs.error : cs.onSurfaceVariant;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: iconColor),
        const SizedBox(width: Glass.space16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: titleStyle),
              if (body != null) ...[
                const SizedBox(height: Glass.space8),
                Text(body, style: text.bodyMedium?.copyWith(color: bodyColor)),
              ],
              if (action != null && onAction != null) ...[
                const SizedBox(height: Glass.space12),
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton.icon(
                    onPressed: onAction,
                    icon: Icon(actionIcon ?? Icons.open_in_new_outlined),
                    label: Text(action),
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// "服务启动失败"卡：全页唯一的 `errorContainer` 面（对齐 Compose 同名 `WarningCard`），
/// 用主题 [Card] 换底色而不是 [GlassPanel]——玻璃面的填充色不可换。
class _ErrorCard extends StatelessWidget {
  const _ErrorCard({
    required this.title,
    required this.body,
    required this.action,
    required this.onAction,
  });

  final String title;
  final String body;
  final String action;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final text = theme.textTheme;
    return Card(
      color: cs.errorContainer,
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(Glass.padCard),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.warning_amber_outlined, color: cs.onErrorContainer),
            const SizedBox(width: Glass.space16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: text.bodyLarge?.copyWith(color: cs.onErrorContainer),
                  ),
                  const SizedBox(height: Glass.space8),
                  Text(
                    body,
                    style:
                        text.bodyMedium?.copyWith(color: cs.onErrorContainer),
                  ),
                  const SizedBox(height: Glass.space12),
                  Align(
                    alignment: Alignment.centerRight,
                    child: FilledButton.icon(
                      onPressed: onAction,
                      icon: const Icon(Icons.refresh_outlined),
                      label: Text(action),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
