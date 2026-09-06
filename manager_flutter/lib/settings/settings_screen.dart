import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/settings/settings_channel.dart';
import 'package:manager_flutter/settings/settings_models.dart';
import 'package:manager_flutter/widgets/glass_alert.dart';
import 'package:manager_flutter/widgets/glass_choice_dialog.dart';

/// 原 Compose `SettingsActivity` 的整页换皮：启动 / 界面两组（底栏 Tab，无返回箭头）。
/// 语言改在首页 Lang 芯片，本页不再放语言组。
///
/// 业务逻辑全部在 Kotlin（`shizuku/settings`）：这里只画快照、转发点击。
/// 每组一张页面级 [GlassPanel]（共 2 张），组内每行是普通 Row。
/// 深色模式单选走 [GlassChoiceDialog]；缺权限 / 缺通知监听确认框用共享 [GlassAlert]。
/// 写操作直接用 Kotlin 带回的整页快照 `setState`；夜间模式 / 主题类开关改后的宿主 `recreate()`
/// 由 Kotlin 在回复之后自行调度，Dart 不处理。
/// 不订阅 EventChannel：`AppShell` 每次切到本 Tab 都用新 `ValueKey` 重建本页，加上 `initState`
/// 拉一次、`resumed` 重拉、写操作带回快照，共同保证新鲜度。
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen>
    with WidgetsBindingObserver {
  SettingsSnapshot _snap = SettingsSnapshot.empty;
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
    final map = await SettingsChannel.getState();
    if (!mounted || seq != _refreshSeq) return;
    setState(() => _snap = SettingsSnapshot.fromJson(map));
  }

  /// 写操作：带回整页快照就直接 `setState`，否则重拉；返回原始 map 供调用方读附加标记。
  Future<Map<String, dynamic>> _write(
    Future<Map<String, dynamic>> call,
  ) async {
    final map = await call;
    if (!mounted) return map;
    if (map.containsKey('copy') || map.containsKey('bootRoot')) {
      // 让在途的 getState 作废，免得旧快照盖掉新状态。
      _refreshSeq++;
      setState(() => _snap = SettingsSnapshot.fromJson(map));
    } else {
      await _refresh();
    }
    return map;
  }

  Future<void> _toggle(String key, bool checked) async {
    final map = await _write(SettingsChannel.setBool(key, checked));
    if (!mounted) return;
    final copy = _snap.copy;
    if (map['needGrant'] == true) {
      final raw = map['grantCmd'];
      final grantCmd = raw is String ? raw : '';
      final manual = await showDialog<bool>(
        context: context,
        builder: (ctx) => GlassAlert(
          title: copy.permissionMissing,
          body: '${copy.wirelessBootPermissionTooltip}\n\n$grantCmd',
          confirmLabel: copy.manual,
          cancelLabel: copy.cancel,
        ),
      );
      if (manual == true) {
        await SettingsChannel.invoke('openWirelessGuide');
        await SettingsChannel.invoke('copyText', <String, dynamic>{
          'text': grantCmd,
        });
      }
    } else if (map['needNotificationAccess'] == true) {
      final go = await showDialog<bool>(
        context: context,
        builder: (ctx) => GlassAlert(
          title: copy.permissionMissing,
          body: copy.autoPairingNotificationAccessTooltip,
          confirmLabel: copy.ok,
          cancelLabel: copy.cancel,
        ),
      );
      if (go == true) await SettingsChannel.invoke('openNotificationAccess');
    }
  }

  Future<void> _pickNightMode() async {
    final snap = _snap;
    final mode = await showDialog<int>(
      context: context,
      builder: (ctx) => GlassChoiceDialog(
        title: snap.copy.darkTheme,
        closeTooltip: snap.copy.cancel,
        children: [
          for (final option in snap.nightModeOptions)
            _ChoiceRow(
              label: option.label,
              selected: option.value == snap.nightMode,
              onTap: () => Navigator.pop(ctx, option.value),
            ),
        ],
      ),
    );
    if (mode == null || !mounted) return;
    await _write(SettingsChannel.setNightMode(mode));
  }

  Future<void> _editPort() async {
    final snap = _snap;
    final port = await showDialog<String>(
      context: context,
      builder: (ctx) => _PortDialog(copy: snap.copy, initial: snap.tcpipPort),
    );
    if (port == null || !mounted) return;
    await _write(SettingsChannel.setTcpipPort(port));
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
            _SectionPanel(
              title: copy.startup,
              rows: [
                if (snap.supportsStartOnBoot) ...[
                  _SettingsRow(
                    icon: Icons.power_settings_new_outlined,
                    title: copy.startOnBoot,
                    summary: copy.startOnBootSummary,
                    value: snap.bootRoot,
                    onChanged: (v) => _toggle(SettingsKeys.startOnBoot, v),
                  ),
                  _SettingsRow(
                    icon: Icons.wifi_outlined,
                    title: copy.startOnBootWireless,
                    summary: copy.startOnBootWirelessSummary,
                    value: snap.bootWireless,
                    onChanged: (v) =>
                        _toggle(SettingsKeys.startOnBootWireless, v),
                  ),
                  _SettingsRow(
                    icon: Icons.notifications_active_outlined,
                    title: copy.autoPairing,
                    summary: copy.autoPairingSummary,
                    value: snap.autoPairing,
                    onChanged: (v) => _toggle(SettingsKeys.autoPairing, v),
                  ),
                  _SettingsRow(
                    icon: Icons.settings_ethernet_outlined,
                    title: copy.watchdogAdb,
                    summary: copy.watchdogAdbSummary,
                    value: snap.watchdog,
                    onChanged: (v) => _toggle(SettingsKeys.watchdogAdb, v),
                  ),
                ],
                _SettingsRow(
                  icon: Icons.settings_ethernet_outlined,
                  title: copy.tcpipPort,
                  summary: copy.tcpipPortSummary,
                  onTap: _editPort,
                ),
              ],
            ),
            const SizedBox(height: Glass.cardGap),
            _SectionPanel(
              title: copy.userInterface,
              rows: [
                _SettingsRow(
                  icon: Icons.dark_mode_outlined,
                  title: copy.darkTheme,
                  summary: snap.nightModeLabel(),
                  onTap: _pickNightMode,
                ),
                _SettingsRow(
                  icon: Icons.dark_mode_outlined,
                  title: copy.blackNightTheme,
                  summary: copy.blackNightThemeSummary,
                  value: snap.blackNightTheme,
                  onChanged: (v) => _toggle(SettingsKeys.blackNightTheme, v),
                ),
                _SettingsRow(
                  icon: Icons.palette_outlined,
                  title: copy.useSystemColor,
                  summary: copy.useSystemColorSummary,
                  value: snap.useSystemColor,
                  onChanged: (v) => _toggle(SettingsKeys.useSystemColor, v),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// 一组设置：页面级玻璃卡（对齐 Compose `SettingsSectionCard`）——组标题 + 行 + 行间分割线。
/// 内部垫一层透明 `Material`，让行的水波纹画在玻璃面之上而不是被面盖住。
class _SectionPanel extends StatelessWidget {
  const _SectionPanel({required this.title, required this.rows});

  final String title;
  final List<Widget> rows;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return GlassPanel(
      padding: const EdgeInsets.symmetric(vertical: Glass.space8),
      child: Material(
        type: MaterialType.transparency,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: Glass.space20,
                vertical: Glass.space12,
              ),
              child: Text(title, style: theme.textTheme.titleLarge),
            ),
            for (var i = 0; i < rows.length; i++) ...[
              rows[i],
              if (i != rows.length - 1)
                const Divider(
                  height: 1,
                  indent: Glass.space20,
                  endIndent: Glass.space20,
                ),
            ],
          ],
        ),
      ),
    );
  }
}

/// 一行设置（对齐 Compose `SettingsRow`）：图标 / 标题 + 副标题 / 可选开关。
/// 有开关的行整行可点即切换；无开关的行走 [onTap]（为 null 时是静态行）。
class _SettingsRow extends StatelessWidget {
  const _SettingsRow({
    required this.icon,
    required this.title,
    required this.summary,
    this.value,
    this.onChanged,
    this.onTap,
  });

  final IconData icon;
  final String title;
  final String summary;
  final bool? value;
  final ValueChanged<bool>? onChanged;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final text = theme.textTheme;
    final value = this.value;
    final onChanged = this.onChanged;
    final hasSwitch = value != null && onChanged != null;
    return InkWell(
      onTap: hasSwitch ? () => onChanged(!value) : onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: Glass.space20,
          vertical: Glass.space16,
        ),
        child: Row(
          children: [
            Icon(icon, color: cs.primary),
            const SizedBox(width: Glass.space16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: text.titleMedium),
                  const SizedBox(height: Glass.space4),
                  Text(
                    summary,
                    style: text.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
                  ),
                ],
              ),
            ),
            if (hasSwitch) ...[
              const SizedBox(width: Glass.space12),
              Switch(value: value, onChanged: onChanged),
            ],
          ],
        ),
      ),
    );
  }
}

/// [GlassChoiceDialog] 里的单选项：选中项加强调底 + 勾。
class _ChoiceRow extends StatelessWidget {
  const _ChoiceRow({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return GlassChoiceWell(
      emphasize: selected,
      onTap: onTap,
      child: Row(
        children: [
          Expanded(child: Text(label, style: theme.textTheme.bodyLarge)),
          if (selected)
            Icon(Icons.check_outlined, color: theme.colorScheme.primary),
        ],
      ),
    );
  }
}

/// TCP/IP 端口输入框（对齐 Compose TcpIpPort 对话框）：只收数字；
/// 确定时空 → 返回 `''`（清除）；非 10..65535 → 行内错误 `dialogAdbInvalidPort`；合法 → 返回去空白的原串。
/// 取消 / 点外 → `null`。
class _PortDialog extends StatefulWidget {
  const _PortDialog({required this.copy, required this.initial});

  final SettingsCopy copy;
  final String initial;

  @override
  State<_PortDialog> createState() => _PortDialogState();
}

class _PortDialogState extends State<_PortDialog> {
  late final TextEditingController _controller =
      TextEditingController(text: widget.initial);
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final trimmed = _controller.text.trim();
    if (trimmed.isEmpty) {
      Navigator.pop(context, '');
      return;
    }
    final value = int.tryParse(trimmed);
    if (value == null || !TcpipPortRange.contains(value)) {
      setState(() => _error = widget.copy.dialogAdbInvalidPort);
      return;
    }
    Navigator.pop(context, trimmed);
  }

  @override
  Widget build(BuildContext context) {
    final copy = widget.copy;
    final text = Theme.of(context).textTheme;
    return Dialog(
      backgroundColor: Colors.transparent,
      child: GlassPanel(
        radius: Glass.radiusHero,
        padding: const EdgeInsets.all(Glass.padCard),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(copy.tcpipPort, style: text.titleMedium),
            const SizedBox(height: Glass.space12),
            Text(copy.tcpipPortSummary, style: text.bodyMedium),
            const SizedBox(height: Glass.space12),
            TextField(
              controller: _controller,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(
                hintText: copy.tcpipPortDisabled,
                errorText: _error,
              ),
              onChanged: (_) {
                if (_error != null) setState(() => _error = null);
              },
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: Glass.space16),
            Wrap(
              alignment: WrapAlignment.end,
              spacing: Glass.space8,
              children: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: Text(copy.cancel),
                ),
                FilledButton(onPressed: _submit, child: Text(copy.ok)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
