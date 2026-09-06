import 'package:flutter/material.dart';
import 'package:manager_flutter/home/home_channel.dart';
import 'package:manager_flutter/home/home_models.dart';
import 'package:manager_flutter/onetools/glass.dart';

// 首页弹层：开机启动三开关 / 语言列表（从 home_screen.dart 原样搬出，只改名加前缀）。

/// 语言选择弹层。原 `_HomeScreenState._showLanguage`：唯一依赖的私有成员是 `context`，
/// 现改为参数传入；选中即关弹层并 `setLocale`（宿主随后 `recreate()`）。
Future<void> showHomeLanguageSheet(
  BuildContext context,
  HomeCopy copy,
  List<HomeLocale> locales,
) async {
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

class HomeBootDialog extends StatefulWidget {
  const HomeBootDialog({
    super.key,
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
  State<HomeBootDialog> createState() => _HomeBootDialogState();
}

class _HomeBootDialogState extends State<HomeBootDialog> {
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
