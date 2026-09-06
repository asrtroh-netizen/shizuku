import 'package:flutter/material.dart';
import 'package:manager_flutter/home/home_channel.dart';
import 'package:manager_flutter/home/home_models.dart';
import 'package:manager_flutter/home/home_screen.dart';
import 'package:manager_flutter/nav/glass_dock.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/pairing/pairing_screen.dart';
import 'package:manager_flutter/widgets/glass_notice_card.dart';

/// 应用壳：悬浮液态玻璃 Dock + [IndexedStack] 保活 4 页（首页 / 应用 / 终端 / 设置）。
///
/// 底栏标签来自首页快照 `copy` 的 `tabHome / tabApps / tabTerminal / tabSettings`
/// （RULEBOOK §3.7），无宿主时用 [HomeCopy.fallback]。[appsTab] / [terminalTab] /
/// [settingsTab] 为 `null` 时渲染占位卡，后续 Wave 由协调者换成真实页面。
class AppShell extends StatefulWidget {
  const AppShell({
    super.key,
    this.initialIndex = 0,
    this.appsTab,
    this.terminalTab,
    this.settingsTab,
  });

  final int initialIndex;
  final Widget? appsTab;
  final Widget? terminalTab;
  final Widget? settingsTab;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> with WidgetsBindingObserver {
  late int _index = widget.initialIndex.clamp(0, 3);
  HomeCopy _copy = HomeCopy.fallback;
  int _refreshSeq = 0;

  /// 非首页 Tab 每次被选中都换 key 重建：等价于 Compose 每次新开 Activity 都
  /// 重新加载，页面无需订阅事件通道也能拿到新鲜快照（RULEBOOK §9 刷新时机）。
  final List<int> _tabGen = [0, 0, 0, 0];

  /// 首页只建一次：切 Tab 时 Dock 重建不该连带重建首页。
  late final Widget _home = HomeScreen(
    onOpenApps: () => _select(1),
    onOpenTerminal: () => _select(2),
    onOpenPairing: _openPairing,
  );

  /// 配对页在栈上时不再推第二个：两页同时订阅 `shizuku/pairing/events`
  /// 会互相顶掉 sink（GAP-15 同机制）。
  bool _pairingOpen = false;

  Future<void> _openPairing() async {
    if (_pairingOpen) return;
    _pairingOpen = true;
    try {
      await Navigator.of(context).push(
        MaterialPageRoute<void>(builder: (_) => const PairingScreen()),
      );
    } finally {
      _pairingOpen = false;
    }
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshCopy();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refreshCopy();
  }

  Future<void> _refreshCopy() async {
    final seq = ++_refreshSeq;
    final map = await HomeChannel.getState();
    if (!mounted || seq != _refreshSeq) return;
    setState(() => _copy = HomeSnapshot.fromJson(map).copy);
  }

  void _select(int index) {
    if (index == _index) return;
    setState(() {
      if (index != 0) _tabGen[index]++;
      _index = index;
    });
  }

  Widget _tab(int index, Widget? page, String label) => KeyedSubtree(
        key: ValueKey('tab-$index-${_tabGen[index]}'),
        child: page ?? _PlaceholderTab(label: label),
      );

  @override
  Widget build(BuildContext context) {
    final copy = _copy;
    return Scaffold(
      backgroundColor: Colors.transparent,
      extendBody: true,
      body: IndexedStack(
        index: _index,
        children: [
          _home,
          _tab(1, widget.appsTab, copy.tabApps),
          _tab(2, widget.terminalTab, copy.tabTerminal),
          _tab(3, widget.settingsTab, copy.tabSettings),
        ],
      ),
      bottomNavigationBar: GlassDock(
        selectedIndex: _index,
        onDestinationSelected: _select,
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.home_outlined),
            label: copy.tabHome,
          ),
          NavigationDestination(
            icon: const Icon(Icons.apps_outlined),
            label: copy.tabApps,
          ),
          NavigationDestination(
            icon: const Icon(Icons.terminal_outlined),
            label: copy.tabTerminal,
          ),
          NavigationDestination(
            icon: const Icon(Icons.settings_outlined),
            label: copy.tabSettings,
          ),
        ],
      ),
    );
  }
}

/// 尚未接入真实页面的 Tab 占位：一张玻璃提示卡 + 沙漏 + 该 Tab 的标签。
class _PlaceholderTab extends StatelessWidget {
  const _PlaceholderTab({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: ListView(
        padding: EdgeInsets.fromLTRB(
          Glass.pageMargin,
          Glass.space12,
          Glass.pageMargin,
          glassDockScrollPadding(context),
        ),
        children: [
          GlassNoticeCard(icon: Icons.hourglass_empty_outlined, text: label),
        ],
      ),
    );
  }
}
