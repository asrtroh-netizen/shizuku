import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:manager_flutter/home/home_cards.dart';
import 'package:manager_flutter/home/home_channel.dart';
import 'package:manager_flutter/home/home_dialogs.dart';
import 'package:manager_flutter/home/home_header.dart';
import 'package:manager_flutter/home/home_models.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';
import 'package:manager_flutter/widgets/glass_alert.dart';

/// 原 Compose 首页的整页换皮：门面 / 无线 / 快捷 / 开机 / 更新 / Lang+日月（页头 / 卡片 / 弹层拆在 `home_header` / `home_cards` / `home_dialogs`）。
class HomeScreen extends StatefulWidget {
  const HomeScreen({
    super.key,
    required this.onOpenApps,
    required this.onOpenTerminal,
    required this.onOpenPairing,
  });

  final VoidCallback onOpenApps;
  final VoidCallback onOpenTerminal;
  final VoidCallback onOpenPairing;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  static const EventChannel _events = EventChannel('shizuku/home/events');

  StreamSubscription<dynamic>? _sub;
  HomeSnapshot _snap = HomeSnapshot.empty;
  bool _checkingUpdate = false;
  int _refreshSeq = 0;

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
    final seq = ++_refreshSeq;
    final map = await HomeChannel.getState();
    if (!mounted || seq != _refreshSeq) return;
    setState(() => _snap = HomeSnapshot.fromJson(map));
  }

  Future<void> _call(String method, [Map<String, dynamic>? args]) async {
    final map = await HomeChannel.invoke(method, args);
    if (!mounted) return;
    if (map.containsKey('running') || map.containsKey('copy')) {
      setState(() => _snap = HomeSnapshot.fromJson(map));
    } else {
      await _refresh();
    }
  }

  void _toast(String message) {
    if (!mounted || message.isEmpty) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final snap = _snap;
    final copy = snap.copy;
    final running = snap.running;
    final state = running ? OneChannelState.ready : OneChannelState.inactive;
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
            HomeTopCapsules(
              copy: copy,
              dark: snap.dark,
              onLang: () => showHomeLanguageSheet(context, copy, snap.locales),
              onTheme: () => _call('toggleTheme'),
            ),
            const SizedBox(height: Glass.cardGap),
            OneStatusHero(
              state: state,
              eyebrow: copy.heroEyebrow,
              title: running ? copy.appName : copy.heroTitleInactive,
              pill: running ? copy.heroPillReady : copy.heroPillInactive,
              stageLabels: [copy.stageInactive, copy.start, copy.stageReady],
              subtitle: running ? null : copy.heroSubtitle,
              detail: running ? null : copy.heroDetail,
            ),
            if (snap.showWireless) ...[
              const SizedBox(height: Glass.cardGap),
              HomeActionCard(
                icon: Icons.wifi_outlined,
                title: copy.wirelessTitle,
                children: [
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    alignment: WrapAlignment.end,
                    children: [
                      OutlinedButton.icon(
                        onPressed: () => _call('openWirelessGuide'),
                        icon: const Icon(Icons.open_in_new, size: 18),
                        label: Text(copy.wirelessGuide),
                      ),
                      if (snap.showPair)
                        OutlinedButton.icon(
                          onPressed: widget.onOpenPairing,
                          icon: const Icon(Icons.link, size: 18),
                          label: Text(copy.pairing),
                        ),
                      FilledButton.icon(
                        onPressed: () => _call('startWireless'),
                        icon: const Icon(Icons.play_arrow, size: 18),
                        label: Text(copy.start),
                      ),
                    ],
                  ),
                ],
              ),
            ],
            const SizedBox(height: Glass.cardGap),
            Text(
              copy.quickTitle,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
            ),
            const SizedBox(height: Glass.space12),
            HomeQuickGrid(
              snap: snap,
              onApps: () => _onApps(),
              onTerminal: () => _onTerminal(),
              onRoot: () => _onRoot(),
              onAdb: () => _onAdb(),
            ),
            const SizedBox(height: Glass.cardGap),
            HomeActionCard(
              icon: Icons.power_settings_new,
              title: copy.bootTitle,
              children: [
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton(
                    onPressed: () => _showBootSheet(),
                    child: Text(copy.bootConfigure),
                  ),
                ),
              ],
            ),
            if (snap.adbLimited) ...[
              const SizedBox(height: Glass.cardGap),
              HomeLimitedBanner(
                title: copy.adbLimited,
                onTap: () => _call('openAdbPermissionHelp'),
              ),
            ],
            const SizedBox(height: Glass.cardGap),
            Align(
              alignment: Alignment.centerRight,
              child: OutlinedButton.icon(
                onPressed: _checkingUpdate ? null : _checkUpdate,
                icon: const Icon(Icons.info_outline, size: 18),
                label: Text(_checkingUpdate ? copy.checkingUpdate : copy.checkUpdate),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _onApps() async {
    final snap = _snap;
    final copy = snap.copy;
    final canOpen = snap.running && snap.grantedCount >= 0;
    final go = await showDialog<bool>(
      context: context,
      builder: (ctx) => GlassAlert(
        title: copy.appsTitle,
        body: snap.appsSub,
        confirmLabel: canOpen ? copy.appsOpen : copy.ok,
        cancelLabel: canOpen ? copy.cancel : null,
      ),
    );
    if (go == true && canOpen) widget.onOpenApps();
  }

  Future<void> _onTerminal() async {
    final snap = _snap;
    final copy = snap.copy;
    final enabled = snap.running && snap.permission;
    final go = await showDialog<bool>(
      context: context,
      builder: (ctx) => GlassAlert(
        title: copy.terminalTitle,
        body: enabled ? copy.terminalBody : copy.terminalOff,
        confirmLabel: copy.ok,
        cancelLabel: copy.cancel,
      ),
    );
    if (go == true && enabled) widget.onOpenTerminal();
  }

  Future<void> _onRoot() async {
    final snap = _snap;
    final copy = snap.copy;
    if (!snap.rooted) {
      await showDialog<void>(
        context: context,
        builder: (ctx) => GlassAlert(
          title: copy.rootTitle,
          body: copy.rootUnavailable,
          confirmLabel: copy.ok,
        ),
      );
      return;
    }
    final go = await showDialog<bool>(
      context: context,
      builder: (ctx) => GlassAlert(
        title: copy.rootTitle,
        body: copy.rootConfirm,
        confirmLabel: snap.rootRestart ? copy.restart : copy.start,
        cancelLabel: copy.cancel,
      ),
    );
    if (go == true) await _call('startRoot');
  }

  Future<void> _onAdb() async {
    final snap = _snap;
    final copy = snap.copy;
    final view = await showDialog<bool>(
      context: context,
      builder: (ctx) => GlassAlert(
        title: copy.adbTitle,
        body: copy.adbSub,
        confirmLabel: copy.adbViewCommand,
        cancelLabel: copy.cancel,
      ),
    );
    if (view != true || !mounted) return;
    // 结果混有 String（'confirm' / 'extra'）与 bool（取消 → false）：路由若定成 <String>，取消键会抛 TypeError 且关不掉（I-5）。
    final action = await showDialog<Object?>(
      context: context,
      builder: (ctx) => GlassAlert(
        title: copy.adbViewCommand,
        body: snap.adbCommand,
        confirmLabel: copy.adbCopy,
        extraLabel: copy.adbSend,
        cancelLabel: copy.cancel,
        monospace: true,
      ),
    );
    if (action == 'confirm') await _call('copyAdbCommand');
    if (action == 'extra') await _call('sendAdbCommand');
  }

  Future<void> _showBootSheet() async {
    await showDialog<void>(
      context: context,
      builder: (ctx) => HomeBootDialog(
        snap: _snap,
        onRoot: (v) async {
          await _call('setBootRoot', {'checked': v});
          return true;
        },
        onWireless: (v) async {
          final raw = await HomeChannel.invoke('setBootWireless', {'checked': v});
          if (!mounted) return false;
          final nested = raw['state'];
          if (nested is Map) {
            setState(() => _snap = HomeSnapshot.fromJson(Map<String, dynamic>.from(nested)));
          } else if (raw.containsKey('running')) {
            setState(() => _snap = HomeSnapshot.fromJson(raw));
          } else if (raw['needGrant'] != true) {
            await _refresh();
          }
          if (raw['needGrant'] == true) {
            final title = raw['title'] as String? ?? _snap.copy.bootWireless;
            final message = raw['message'] as String? ?? '';
            final cmd = raw['grantCmd'] as String? ?? '';
            if (!mounted) return false;
            final copyCmd = await showDialog<bool>(
              context: context,
              builder: (c) => GlassAlert(
                title: title,
                body: message,
                confirmLabel: _snap.copy.adbCopy,
                cancelLabel: _snap.copy.cancel,
              ),
            );
            if (copyCmd == true) {
              await HomeChannel.invoke('copyText', {'text': cmd});
            }
            return false;
          }
          return raw['ok'] != false && raw['unavailable'] != true;
        },
        onWatchdog: (v) async {
          await _call('setWatchdog', {'checked': v});
          return true;
        },
      ),
    );
    await _refresh();
  }

  Future<void> _checkUpdate() async {
    setState(() => _checkingUpdate = true);
    try {
      final raw = await HomeChannel.invoke('checkUpdate');
      final message = raw['message'] as String? ??
          (raw['unavailable'] == true ? 'unavailable' : '');
      _toast(message);
    } finally {
      if (mounted) setState(() => _checkingUpdate = false);
    }
  }
}
