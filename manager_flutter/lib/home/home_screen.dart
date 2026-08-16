import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:manager_flutter/home/home_channel.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';

/// P0 首页：只渲染 Ultra 门面卡（含点阵笑脸）。跑着笑，没跑哭。
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  static const EventChannel _events = EventChannel('shizuku/home/events');

  StreamSubscription<dynamic>? _sub;
  bool _running = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _sub = _events.receiveBroadcastStream().listen(
      (_) {
        if (mounted) _refresh();
      },
      onError: (_) {},
    );
    _refresh();
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

  Future<void> _refresh() async {
    final map = await HomeChannel.getState();
    if (!mounted) return;
    setState(() => _running = map['running'] == true);
  }

  @override
  Widget build(BuildContext context) {
    final state = _running
        ? OneChannelState.ready
        : OneChannelState.inactive;
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(
            Glass.pageMargin,
            Glass.space16,
            Glass.pageMargin,
            Glass.space24,
          ),
          children: [
            OneStatusHero(
              state: state,
              eyebrow: 'Service',
              title: _running ? 'Shizuku' : 'Not activated',
              pill: _running ? 'Active' : 'Inactive',
              stageLabels: const ['Inactive', 'Starting', 'Ready'],
              subtitle: _running ? null : 'Shizuku is not running',
              detail: _running
                  ? null
                  : 'Use wireless debugging or root to start.',
            ),
          ],
        ),
      ),
    );
  }
}
