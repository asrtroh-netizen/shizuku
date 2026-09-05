import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:manager_flutter/home/home_channel.dart';
import 'package:manager_flutter/home/home_models.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';

/// 原 Compose 首页的整页换皮：门面 / 无线 / 快捷 / 开机 / 更新 / Lang+日月。
class HomeScreen extends StatefulWidget {
  const HomeScreen({
    super.key,
    this.onOpenApps,
    this.onOpenTerminal,
    this.onOpenSettings,
    this.onOpenPairing,
  });

  final VoidCallback? onOpenApps;
  final VoidCallback? onOpenTerminal;
  final VoidCallback? onOpenSettings;
  final VoidCallback? onOpenPairing;

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
    final state =
        running ? OneChannelState.ready : OneChannelState.inactive;
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(
            Glass.pageMargin,
            Glass.space12,
            Glass.pageMargin,
            Glass.space24,
          ),
          children: [
            _TopCapsules(
              copy: copy,
              dark: snap.dark,
              onLang: () => _showLanguage(copy, snap.locales),
              onTheme: () => _call('toggleTheme'),
            ),
            const SizedBox(height: Glass.cardGap),
            OneStatusHero(
              state: state,
              eyebrow: copy.heroEyebrow,
              title: running ? copy.appName : copy.heroTitleInactive,
              pill: running ? copy.heroPillReady : copy.heroPillInactive,
              stageLabels: [
                copy.stageInactive,
                copy.start,
                copy.stageReady,
              ],
              subtitle: running ? null : copy.heroSubtitle,
              detail: running ? null : copy.heroDetail,
            ),
            if (snap.showWireless) ...[
              const SizedBox(height: Glass.cardGap),
              _ActionCard(
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
                          onPressed: () {
                            if (widget.onOpenPairing != null) {
                              widget.onOpenPairing!();
                            } else {
                              _call('openPairing');
                            }
                          },
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
            _QuickGrid(
              snap: snap,
              onApps: () => _onApps(),
              onTerminal: () => _onTerminal(),
              onRoot: () => _onRoot(),
              onAdb: () => _onAdb(),
            ),
            const SizedBox(height: Glass.cardGap),
            _ActionCard(
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
              _LimitedBanner(
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
                label: Text(
                  _checkingUpdate ? copy.checkingUpdate : copy.checkUpdate,
                ),
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
      builder: (ctx) => _GlassAlert(
        title: copy.appsTitle,
        body: snap.appsSub,
        confirmLabel: canOpen ? copy.appsOpen : copy.ok,
        cancelLabel: copy.cancel,
        showCancel: canOpen,
      ),
    );
    if (go == true && canOpen) {
      if (widget.onOpenApps != null) {
        widget.onOpenApps!();
      } else {
        await _call('openApps');
      }
    }
  }

  Future<void> _onTerminal() async {
    final snap = _snap;
    final copy = snap.copy;
    final enabled = snap.running && snap.permission;
    final go = await showDialog<bool>(
      context: context,
      builder: (ctx) => _GlassAlert(
        title: copy.terminalTitle,
        body: enabled ? copy.terminalBody : copy.terminalOff,
        confirmLabel: copy.ok,
        cancelLabel: copy.cancel,
        showCancel: true,
      ),
    );
    if (go == true && enabled) {
      if (widget.onOpenTerminal != null) {
        widget.onOpenTerminal!();
      } else {
        await _call('openTerminal');
      }
    }
  }

  Future<void> _onRoot() async {
    final snap = _snap;
    final copy = snap.copy;
    if (!snap.rooted) {
      await showDialog<void>(
        context: context,
        builder: (ctx) => _GlassAlert(
          title: copy.rootTitle,
          body: copy.rootUnavailable,
          confirmLabel: copy.ok,
          cancelLabel: copy.cancel,
          showCancel: false,
        ),
      );
      return;
    }
    final go = await showDialog<bool>(
      context: context,
      builder: (ctx) => _GlassAlert(
        title: copy.rootTitle,
        body: copy.rootConfirm,
        confirmLabel: snap.rootRestart ? copy.restart : copy.start,
        cancelLabel: copy.cancel,
        showCancel: true,
      ),
    );
    if (go == true) await _call('startRoot');
  }

  Future<void> _onAdb() async {
    final snap = _snap;
    final copy = snap.copy;
    final view = await showDialog<bool>(
      context: context,
      builder: (ctx) => _GlassAlert(
        title: copy.adbTitle,
        body: copy.adbSub,
        confirmLabel: copy.adbViewCommand,
        cancelLabel: copy.cancel,
        showCancel: true,
      ),
    );
    if (view != true || !mounted) return;
    final action = await showDialog<String>(
      context: context,
      builder: (ctx) => _GlassAlert(
        title: copy.adbViewCommand,
        body: snap.adbCommand,
        confirmLabel: copy.adbCopy,
        extraLabel: copy.adbSend,
        cancelLabel: copy.cancel,
        showCancel: true,
        monospace: true,
      ),
    );
    if (action == 'confirm') await _call('copyAdbCommand');
    if (action == 'extra') await _call('sendAdbCommand');
  }

  Future<void> _showBootSheet() async {
    await showDialog<void>(
      context: context,
      builder: (ctx) {
        return _BootDialog(
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
                builder: (c) => _GlassAlert(
                  title: title,
                  body: message,
                  confirmLabel: _snap.copy.adbCopy,
                  cancelLabel: _snap.copy.cancel,
                  showCancel: true,
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
        );
      },
    );
    await _refresh();
  }

  Future<void> _showLanguage(HomeCopy copy, List<HomeLocale> locales) async {
    await showDialog<void>(
      context: context,
      builder: (ctx) => Dialog(
        backgroundColor: Colors.transparent,
        child: GlassPanel(
          radius: Glass.radiusHero,
          padding: const EdgeInsets.all(Glass.padCard),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxHeight: 420, maxWidth: 420),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(copy.language, style: Theme.of(ctx).textTheme.titleMedium),
                const SizedBox(height: 12),
                Flexible(
                  child: ListView(
                    shrinkWrap: true,
                    children: [
                      for (final loc in locales)
                        ListTile(
                          title: Text(loc.label),
                          selected: loc.selected,
                          onTap: () {
                            Navigator.pop(ctx);
                            HomeChannel.invoke('setLocale', {'tag': loc.tag});
                          },
                        ),
                    ],
                  ),
                ),
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: () => Navigator.pop(ctx),
                    child: Text(copy.cancel),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
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

class _TopCapsules extends StatelessWidget {
  const _TopCapsules({
    required this.copy,
    required this.dark,
    required this.onLang,
    required this.onTheme,
  });

  final HomeCopy copy;
  final bool dark;
  final VoidCallback onLang;
  final VoidCallback onTheme;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Spacer(),
        _LangChip(label: copy.lang, onTap: onLang),
        const SizedBox(width: 8),
        _ThemeSlide(dark: dark, copy: copy, onTap: onTheme),
      ],
    );
  }
}

class _LangChip extends StatelessWidget {
  const _LangChip({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Material(
      color: cs.primary,
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(999),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 6,
                height: 6,
                decoration: BoxDecoration(
                  color: cs.onPrimary,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                label,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: cs.onPrimary,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ThemeSlide extends StatelessWidget {
  const _ThemeSlide({
    required this.dark,
    required this.copy,
    required this.onTap,
  });

  final bool dark;
  final HomeCopy copy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    const trackW = 56.0;
    const trackH = 30.0;
    const knob = 24.0;
    const pad = 3.0;
    const iconTint = Color(0xFF3A4A6B);
    final cs = Theme.of(context).colorScheme;
    final offset = dark ? trackW - knob - pad : pad;
    return Semantics(
      button: true,
      label: dark ? copy.themeDark : copy.themeLight,
      child: GestureDetector(
        onTap: onTap,
        child: SizedBox(
          width: trackW,
          height: trackH,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: ColoredBox(
              color: cs.surfaceContainerHighest,
              child: Stack(
                children: [
                  Positioned.fill(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 7),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.light_mode_outlined,
                            size: 14,
                            color: iconTint.withValues(alpha: dark ? 0.35 : 0.9),
                          ),
                          Icon(
                            Icons.dark_mode_outlined,
                            size: 14,
                            color: iconTint.withValues(alpha: dark ? 0.9 : 0.35),
                          ),
                        ],
                      ),
                    ),
                  ),
                  AnimatedPositioned(
                    duration: const Duration(milliseconds: 220),
                    curve: Curves.easeOut,
                    left: offset,
                    top: pad,
                    width: knob,
                    height: knob,
                    child: DecoratedBox(
                      decoration: const BoxDecoration(
                        color: Colors.white,
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            blurRadius: 2,
                            offset: Offset(0, 1),
                            color: Color(0x33000000),
                          ),
                        ],
                      ),
                      child: Center(
                        child: Icon(
                          dark
                              ? Icons.dark_mode_outlined
                              : Icons.light_mode_outlined,
                          size: 14,
                          color: iconTint,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ActionCard extends StatelessWidget {
  const _ActionCard({
    required this.icon,
    required this.title,
    required this.children,
  });

  final IconData icon;
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GlassPanel(
      padding: const EdgeInsets.all(Glass.padCard),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: cs.primary),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          ...children,
        ],
      ),
    );
  }
}

class _QuickGrid extends StatelessWidget {
  const _QuickGrid({
    required this.snap,
    required this.onApps,
    required this.onTerminal,
    required this.onRoot,
    required this.onAdb,
  });

  final HomeSnapshot snap;
  final VoidCallback onApps;
  final VoidCallback onTerminal;
  final VoidCallback onRoot;
  final VoidCallback onAdb;

  @override
  Widget build(BuildContext context) {
    final copy = snap.copy;
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: _QuickTile(
                icon: Icons.apps_outlined,
                title: copy.appsTitle,
                subtitle: snap.appsSub,
                dimmed: !snap.running,
                onTap: onApps,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _QuickTile(
                icon: Icons.terminal,
                title: copy.terminalTitle,
                subtitle: copy.terminalSub,
                dimmed: !(snap.running && snap.permission),
                onTap: onTerminal,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _QuickTile(
                icon: Icons.admin_panel_settings_outlined,
                title: copy.rootTitle,
                subtitle: snap.rootSub,
                dimmed: !snap.rooted,
                onTap: onRoot,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _QuickTile(
                icon: Icons.dns_outlined,
                title: copy.adbTitle,
                subtitle: copy.adbSub,
                dimmed: false,
                onTap: onAdb,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _QuickTile extends StatelessWidget {
  const _QuickTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.dimmed,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final bool dimmed;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GlassPanel(
      onTap: onTap,
      padding: const EdgeInsets.all(18),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 124),
        child: Opacity(
          opacity: dimmed ? 0.55 : 1,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, color: dimmed ? cs.onSurface.withValues(alpha: 0.4) : cs.primary),
              const SizedBox(height: 10),
              Text(
                title,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
              ),
              const SizedBox(height: 4),
              Text(
                subtitle,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: cs.onSurface.withValues(alpha: dimmed ? 0.45 : 0.78),
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LimitedBanner extends StatelessWidget {
  const _LimitedBanner({required this.title, required this.onTap});

  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Material(
      color: cs.errorContainer,
      borderRadius: BorderRadius.circular(Glass.radiusCard),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(Glass.radiusCard),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              Icon(Icons.warning_amber_outlined, color: cs.error),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _GlassAlert extends StatelessWidget {
  const _GlassAlert({
    required this.title,
    required this.body,
    required this.confirmLabel,
    required this.cancelLabel,
    required this.showCancel,
    this.extraLabel,
    this.monospace = false,
  });

  final String title;
  final String body;
  final String confirmLabel;
  final String cancelLabel;
  final bool showCancel;
  final String? extraLabel;
  final bool monospace;

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.transparent,
      child: GlassPanel(
        radius: Glass.radiusHero,
        padding: const EdgeInsets.all(Glass.padCard),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 240),
              child: SingleChildScrollView(
                child: Text(
                  body,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontFamily: monospace ? 'monospace' : null,
                      ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Wrap(
              alignment: WrapAlignment.end,
              spacing: 8,
              children: [
                if (showCancel)
                  TextButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: Text(cancelLabel),
                  ),
                if (extraLabel != null)
                  TextButton(
                    onPressed: () => Navigator.pop(context, 'extra'),
                    child: Text(extraLabel!),
                  ),
                FilledButton(
                  onPressed: () => Navigator.pop(context, extraLabel != null ? 'confirm' : true),
                  child: Text(confirmLabel),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _BootDialog extends StatefulWidget {
  const _BootDialog({
    required this.snap,
    required this.onRoot,
    required this.onWireless,
    required this.onWatchdog,
  });

  final HomeSnapshot snap;
  final Future<bool> Function(bool value) onRoot;
  final Future<bool> Function(bool value) onWireless;
  final Future<bool> Function(bool value) onWatchdog;

  @override
  State<_BootDialog> createState() => _BootDialogState();
}

class _BootDialogState extends State<_BootDialog> {
  late bool _root = widget.snap.bootRoot;
  late bool _wireless = widget.snap.bootWireless;
  late bool _watchdog = widget.snap.watchdog;

  Future<void> _applySwitch({
    required bool root,
    required bool wireless,
    required bool watchdog,
    required Future<bool> Function() commit,
  }) async {
    final prevRoot = _root;
    final prevWireless = _wireless;
    final prevWatchdog = _watchdog;
    setState(() {
      _root = root;
      _wireless = wireless;
      _watchdog = watchdog;
    });
    final ok = await commit();
    if (!mounted) return;
    if (!ok) {
      setState(() {
        _root = prevRoot;
        _wireless = prevWireless;
        _watchdog = prevWatchdog;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final copy = widget.snap.copy;
    return Dialog(
      backgroundColor: Colors.transparent,
      child: GlassPanel(
        radius: Glass.radiusHero,
        padding: const EdgeInsets.all(Glass.padCard),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(copy.bootTitle, style: Theme.of(context).textTheme.titleMedium),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(copy.bootRoot),
              value: _root,
              onChanged: (v) => _applySwitch(
                root: v,
                wireless: v ? false : _wireless,
                watchdog: _watchdog,
                commit: () => widget.onRoot(v),
              ),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(copy.bootWireless),
              value: _wireless,
              onChanged: (v) => _applySwitch(
                root: v ? false : _root,
                wireless: v,
                watchdog: _watchdog,
                commit: () => widget.onWireless(v),
              ),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(copy.watchdog),
              value: _watchdog,
              onChanged: (v) => _applySwitch(
                root: _root,
                wireless: _wireless,
                watchdog: v,
                commit: () => widget.onWatchdog(v),
              ),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text(copy.ok),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
