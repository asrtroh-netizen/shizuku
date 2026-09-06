import 'package:flutter/material.dart';
import 'package:manager_flutter/apps/apps_screen.dart';
import 'package:manager_flutter/home/home_channel.dart';
import 'package:manager_flutter/home/home_models.dart';
import 'package:manager_flutter/home/home_screen.dart';
import 'package:manager_flutter/nav/glass_dock.dart';
import 'package:manager_flutter/pairing/pairing_screen.dart';
import 'package:manager_flutter/settings/settings_screen.dart';
import 'package:manager_flutter/terminal/terminal_screen.dart';

/// 应用壳：悬浮液态玻璃 Dock + [IndexedStack] 保活 2 页（首页 / 设置）。
///
/// 应用管理与终端是推入页，不再占底栏。底栏标签来自首页快照 `copy` 的
/// `tabHome` / `tabSettings`（无宿主时用 [HomeCopy.fallback]）。
class AppShell extends StatefulWidget {
  const AppShell({
    super.key,
    this.initialIndex = 0,
  });

  final int initialIndex;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> with WidgetsBindingObserver {
  late int _index = widget.initialIndex.clamp(0, 1);
  HomeCopy _copy = HomeCopy.fallback;
  int _refreshSeq = 0;

  /// 设置 Tab 每次被选中都换 key 重建：等价于 Compose 每次新开 Activity 都
  /// 重新加载，页面无需订阅事件通道也能拿到新鲜快照。
  int _settingsGen = 0;

  /// 首页只建一次：切 Tab 时 Dock 重建不该连带重建首页。
  late final Widget _home = HomeScreen(
    onOpenApps: () => _push(const AppsScreen()),
    onOpenTerminal: () => _push(const TerminalScreen()),
    onOpenPairing: () => _push(const PairingScreen()),
  );

  /// 配对 / 应用 / 终端共用：两页同时订阅同一 EventChannel 会互相顶掉 sink。
  bool _stackPageOpen = false;

  Future<void> _push(Widget page) async {
    if (_stackPageOpen) return;
    _stackPageOpen = true;
    try {
      await Navigator.of(context).push(
        MaterialPageRoute<void>(builder: (_) => page),
      );
    } finally {
      _stackPageOpen = false;
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
      if (index == 1) _settingsGen++;
      _index = index;
    });
  }

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
          KeyedSubtree(
            key: ValueKey('tab-settings-$_settingsGen'),
            child: const SettingsScreen(),
          ),
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
            icon: const Icon(Icons.settings_outlined),
            label: copy.tabSettings,
          ),
        ],
      ),
    );
  }
}
