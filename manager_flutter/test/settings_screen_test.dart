import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/settings/settings_models.dart';
import 'package:manager_flutter/settings/settings_screen.dart';
import 'package:manager_flutter/theme/app_theme.dart';
import 'package:manager_flutter/widgets/glass_choice_dialog.dart';

const MethodChannel _channel = MethodChannel('shizuku/settings');

const String _grantCmd =
    'adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS';

/// 每条文案都带 "!" 或独特措辞，证明画到屏幕上的是 JSON 下发的、不是 fallback。
const Map<String, dynamic> _copy = <String, dynamic>{
  'title': 'Settings title',
  'startup': 'Startup!',
  'startOnBoot': 'Root boot!',
  'startOnBootSummary': 'root boot summary',
  'startOnBootWireless': 'Wireless boot!',
  'startOnBootWirelessSummary': 'wireless boot summary',
  'autoPairing': 'Auto pairing!',
  'autoPairingSummary': 'auto pairing summary',
  'watchdogAdb': 'Watchdog!',
  'watchdogAdbSummary': 'watchdog summary',
  'tcpipPort': 'TCP port!',
  'tcpipPortSummary': 'tcp summary',
  'tcpipPortDisabled': 'Off!',
  'dialogAdbInvalidPort': 'Bad port!',
  'language': 'Language!',
  'languageSystem': 'System lang!',
  'translationContributors': 'Contributors!',
  'translationContributorsSummary': 'people',
  'translation': 'Translate!',
  'translationSummary': 'help translate',
  'userInterface': 'Appearance!',
  'darkTheme': 'Dark theme!',
  'followSystem': 'Follow!',
  'blackNightTheme': 'Black night!',
  'blackNightThemeSummary': 'black summary',
  'useSystemColor': 'System color!',
  'useSystemColorSummary': 'system color summary',
  'permissionMissing': 'Permission missing!',
  'wirelessBootPermissionTooltip': 'Need WRITE_SECURE_SETTINGS',
  'autoPairingNotificationAccessTooltip': 'Need notification access',
  'manual': 'Manual!',
  'cancel': 'Cancel!',
  'ok': 'OK!',
};

const List<Map<String, dynamic>> _nightModes = <Map<String, dynamic>>[
  <String, dynamic>{'value': 1, 'label': 'Off!'},
  <String, dynamic>{'value': 2, 'label': 'On!'},
  <String, dynamic>{'value': -1, 'label': 'Follow system!'},
];

const List<Map<String, dynamic>> _locales = <Map<String, dynamic>>[
  <String, dynamic>{'tag': 'SYSTEM', 'label': 'Follow System!', 'selected': false},
  <String, dynamic>{'tag': 'en', 'label': 'English!', 'selected': false},
  <String, dynamic>{'tag': 'zh-CN', 'label': '简体中文', 'selected': true},
];

String _snapshot({
  bool supportsStartOnBoot = true,
  bool bootRoot = false,
  bool bootWireless = false,
  bool autoPairing = false,
  bool watchdog = false,
  Object? tcpipPort = '',
  int nightMode = -1,
  Object? nightModeOptions = _nightModes,
  bool blackNightTheme = false,
  bool useSystemColor = false,
  Object? locales = _locales,
  Map<String, dynamic> extra = const <String, dynamic>{},
}) =>
    jsonEncode(<String, dynamic>{
      'ok': true,
      'supportsStartOnBoot': supportsStartOnBoot,
      'bootRoot': bootRoot,
      'bootWireless': bootWireless,
      'autoPairing': autoPairing,
      'watchdog': watchdog,
      'tcpipPort': tcpipPort,
      'nightMode': nightMode,
      'nightModeOptions': nightModeOptions,
      'blackNightTheme': blackNightTheme,
      'useSystemColor': useSystemColor,
      'locales': locales,
      'translationUrl': 'https://rikka.app/contribute_translation/',
      'copy': _copy,
      ...extra,
    });

const Set<String> _writeMethods = <String>{
  'setBool',
  'setTcpipPort',
  'setNightMode',
  'setLocale',
};

void _mock(Future<Object?> Function(MethodCall call)? handler) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_channel, handler);
}

/// 打桩宿主：`getState` 回 [state]；四个写方法回 [write] 的结果（默认原样回 [state]）；
/// 其它动作回 `{ok:true}`。返回按序记录的全部调用。
List<MethodCall> _mockHost({
  required String state,
  Future<Object?> Function(MethodCall call)? write,
}) {
  final calls = <MethodCall>[];
  _mock((call) async {
    calls.add(call);
    if (call.method == 'getState') return state;
    if (_writeMethods.contains(call.method)) {
      return write == null ? state : await write(call);
    }
    return jsonEncode(<String, dynamic>{'ok': true});
  });
  return calls;
}

/// 三组卡叠起来超过默认 600 逻辑像素高，ListView 会懒构建；把测试视口拉高让整页都在树里。
Future<void> _pumpSettings(WidgetTester tester) async {
  tester.view.physicalSize = const Size(800, 2400);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      home: const SettingsScreen(),
    ),
  );
  await tester.pumpAndSettle();
}

/// 设置行 = 带文字的 InkWell（组标题没有 InkWell 祖先，所以不会误中）。
Finder _row(String title) => find.widgetWithText(InkWell, title);

List<bool> _switchValues(WidgetTester tester) => tester
    .widgetList<Switch>(find.byType(Switch))
    .map((s) => s.value)
    .toList(growable: false);

Iterable<MethodCall> _callsOf(List<MethodCall> calls, String method) =>
    calls.where((c) => c.method == method);

void main() {
  tearDown(() => _mock(null));

  testWidgets('no host: two fallback sections, startup+appearance entries, 6 switches, no crash',
      (tester) async {
    _mock((call) async => throw MissingPluginException());
    await _pumpSettings(tester);

    const f = SettingsCopy.fallback;
    expect(find.text(f.title), findsOneWidget);
    expect(find.text(f.startup), findsOneWidget);
    expect(find.text(f.language), findsNothing);
    expect(find.text(f.userInterface), findsOneWidget);

    for (final title in <String>[
      f.startOnBoot,
      f.startOnBootWireless,
      f.autoPairing,
      f.watchdogAdb,
      f.tcpipPort,
      f.darkTheme,
      f.blackNightTheme,
      f.useSystemColor,
    ]) {
      expect(_row(title), findsOneWidget, reason: title);
    }
    expect(_row(f.translationContributors), findsNothing);
    expect(_row(f.translation), findsNothing);
    expect(find.byType(Switch), findsNWidgets(6));
    // 两组各一张页面级 GlassPanel；行不是 Card。
    expect(find.byType(GlassPanel), findsNWidgets(2));
    expect(find.byType(Card), findsNothing);
    expect(find.text('Follow system'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('full snapshot: copy from JSON, switch values follow fields',
      (tester) async {
    _mockHost(
      state: _snapshot(
        bootRoot: true,
        bootWireless: false,
        autoPairing: true,
        watchdog: false,
        nightMode: 2,
        blackNightTheme: true,
        useSystemColor: false,
      ),
    );
    await _pumpSettings(tester);

    expect(find.text('Settings title'), findsOneWidget);
    expect(find.text('Startup!'), findsOneWidget);
    expect(find.text('Appearance!'), findsOneWidget);
    expect(find.text('root boot summary'), findsOneWidget);
    // 派生副标题：深色模式 = nightMode 对应 label。
    expect(find.text('On!'), findsOneWidget);

    expect(
      _switchValues(tester),
      <bool>[true, false, true, false, true, false],
    );
    expect(find.text(SettingsCopy.fallback.startup), findsNothing);
  });

  testWidgets(
      'wireless boot tap sends setBool{start_on_boot_wireless,true}; '
      'needGrant opens alert with grantCmd; manual -> openWirelessGuide + copyText',
      (tester) async {
    final calls = _mockHost(
      state: _snapshot(),
      write: (call) async => _snapshot(
        extra: const <String, dynamic>{
          'needGrant': true,
          'grantCmd': _grantCmd,
        },
      ),
    );
    await _pumpSettings(tester);

    await tester.tap(_row('Wireless boot!'));
    await tester.pumpAndSettle();

    final setBools = _callsOf(calls, 'setBool').toList();
    expect(setBools, hasLength(1));
    expect(
      setBools.single.arguments,
      <String, dynamic>{'key': 'start_on_boot_wireless', 'checked': true},
    );

    expect(find.text('Permission missing!'), findsOneWidget);
    expect(find.textContaining('Need WRITE_SECURE_SETTINGS'), findsOneWidget);
    expect(find.textContaining(_grantCmd), findsOneWidget);
    expect(find.text('Manual!'), findsOneWidget);
    expect(find.text('Cancel!'), findsOneWidget);
    // 未落盘：开关仍为关。
    expect(_switchValues(tester)[1], isFalse);

    await tester.tap(find.text('Manual!'));
    await tester.pumpAndSettle();

    expect(find.text('Permission missing!'), findsNothing);
    expect(_callsOf(calls, 'openWirelessGuide'), hasLength(1));
    final copyText = _callsOf(calls, 'copyText').single;
    expect(copyText.arguments, <String, dynamic>{'text': _grantCmd});
  });

  testWidgets('needGrant alert cancel does not open guide or copy',
      (tester) async {
    final calls = _mockHost(
      state: _snapshot(),
      write: (call) async => _snapshot(
        extra: const <String, dynamic>{'needGrant': true, 'grantCmd': _grantCmd},
      ),
    );
    await _pumpSettings(tester);

    await tester.tap(find.byType(Switch).at(1));
    await tester.pumpAndSettle();
    expect(find.text('Permission missing!'), findsOneWidget);

    await tester.tap(find.text('Cancel!'));
    await tester.pumpAndSettle();

    expect(find.text('Permission missing!'), findsNothing);
    expect(_callsOf(calls, 'openWirelessGuide'), isEmpty);
    expect(_callsOf(calls, 'copyText'), isEmpty);
  });

  testWidgets('auto pairing needNotificationAccess -> alert; OK -> openNotificationAccess',
      (tester) async {
    final calls = _mockHost(
      state: _snapshot(),
      write: (call) async => _snapshot(
        extra: const <String, dynamic>{'needNotificationAccess': true},
      ),
    );
    await _pumpSettings(tester);

    await tester.tap(_row('Auto pairing!'));
    await tester.pumpAndSettle();

    expect(
      _callsOf(calls, 'setBool').single.arguments,
      <String, dynamic>{'key': 'auto_pairing_enabled', 'checked': true},
    );
    expect(find.text('Permission missing!'), findsOneWidget);
    expect(find.text('Need notification access'), findsOneWidget);

    await tester.tap(find.text('OK!'));
    await tester.pumpAndSettle();

    expect(_callsOf(calls, 'openNotificationAccess'), hasLength(1));
    expect(find.text('Permission missing!'), findsNothing);
  });

  testWidgets('switch tap toggles with the opposite value and applies returned snapshot',
      (tester) async {
    final calls = _mockHost(
      state: _snapshot(watchdog: true),
      write: (call) async => _snapshot(watchdog: false),
    );
    await _pumpSettings(tester);
    expect(_switchValues(tester)[3], isTrue);

    await tester.tap(find.byType(Switch).at(3));
    await tester.pumpAndSettle();

    expect(
      _callsOf(calls, 'setBool').single.arguments,
      <String, dynamic>{'key': 'watchdog_enabled_adb', 'checked': false},
    );
    expect(_switchValues(tester)[3], isFalse);
    // 写操作带回快照 → 不再二次 getState。
    expect(_callsOf(calls, 'getState'), hasLength(1));
  });

  testWidgets('theme switches send ThemeHelper keys', (tester) async {
    final calls = _mockHost(state: _snapshot());
    await _pumpSettings(tester);

    await tester.tap(_row('Black night!'));
    await tester.pumpAndSettle();
    await tester.tap(_row('System color!'));
    await tester.pumpAndSettle();

    expect(
      _callsOf(calls, 'setBool').map((c) => c.arguments).toList(),
      <Map<String, dynamic>>[
        <String, dynamic>{'key': 'black_night_theme', 'checked': true},
        <String, dynamic>{'key': 'use_system_color', 'checked': true},
      ],
    );
  });

  testWidgets('dark theme row opens GlassChoiceDialog; picking Off sends setNightMode{1}',
      (tester) async {
    final calls = _mockHost(state: _snapshot(nightMode: -1));
    await _pumpSettings(tester);

    await tester.tap(_row('Dark theme!'));
    await tester.pumpAndSettle();

    expect(find.byType(GlassChoiceDialog), findsOneWidget);
    expect(find.text('Off!'), findsOneWidget);
    expect(find.text('On!'), findsOneWidget);
    // 行副标题 + 弹层选项各一份。
    expect(find.text('Follow system!'), findsNWidgets(2));

    await tester.tap(find.text('Off!'));
    await tester.pumpAndSettle();

    expect(_callsOf(calls, 'setNightMode').single.arguments, <String, dynamic>{'mode': 1});
  });

  testWidgets('port dialog: invalid -> inline error, no call; valid -> setTcpipPort; blank -> clear',
      (tester) async {
    final calls = _mockHost(state: _snapshot(tcpipPort: '5555'));
    await _pumpSettings(tester);

    await tester.tap(_row('TCP port!'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsOneWidget);
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, '5555');

    await tester.enterText(find.byType(TextField), '5');
    await tester.tap(find.text('OK!'));
    await tester.pumpAndSettle();
    expect(find.text('Bad port!'), findsOneWidget);
    expect(_callsOf(calls, 'setTcpipPort'), isEmpty);

    await tester.enterText(find.byType(TextField), '70000');
    await tester.tap(find.text('OK!'));
    await tester.pumpAndSettle();
    expect(find.text('Bad port!'), findsOneWidget);
    expect(_callsOf(calls, 'setTcpipPort'), isEmpty);

    await tester.enterText(find.byType(TextField), '6000');
    await tester.tap(find.text('OK!'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNothing);
    expect(
      _callsOf(calls, 'setTcpipPort').single.arguments,
      <String, dynamic>{'port': '6000'},
    );

    await tester.tap(_row('TCP port!'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '');
    await tester.tap(find.text('OK!'));
    await tester.pumpAndSettle();
    expect(
      _callsOf(calls, 'setTcpipPort').last.arguments,
      <String, dynamic>{'port': ''},
    );
  });

  testWidgets('port dialog cancel sends nothing', (tester) async {
    final calls = _mockHost(state: _snapshot());
    await _pumpSettings(tester);

    await tester.tap(_row('TCP port!'));
    await tester.pumpAndSettle();
    // 未设端口：输入框空、占位为 tcpipPortDisabled。
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, '');
    expect(find.text('Off!'), findsOneWidget);

    await tester.tap(find.text('Cancel!'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNothing);
    expect(_callsOf(calls, 'setTcpipPort'), isEmpty);
  });

  testWidgets('supportsStartOnBoot=false hides the four startup switches (Compose parity)',
      (tester) async {
    _mockHost(state: _snapshot(supportsStartOnBoot: false));
    await _pumpSettings(tester);

    expect(_row('Root boot!'), findsNothing);
    expect(_row('Wireless boot!'), findsNothing);
    expect(_row('Auto pairing!'), findsNothing);
    expect(_row('Watchdog!'), findsNothing);
    expect(_row('TCP port!'), findsOneWidget);
    expect(find.byType(Switch), findsNWidgets(2));
    expect(find.byType(GlassPanel), findsNWidgets(2));
  });

  testWidgets('write failure (PlatformException) falls back to re-pulling getState',
      (tester) async {
    final calls = _mockHost(
      state: _snapshot(),
      write: (call) async => throw PlatformException(code: call.method),
    );
    await _pumpSettings(tester);

    await tester.tap(find.byType(Switch).first);
    await tester.pumpAndSettle();

    expect(_callsOf(calls, 'setBool'), hasLength(1));
    expect(_callsOf(calls, 'getState'), hasLength(2));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'bad input: nightModeOptions is a string -> dark theme row still renders '
      'and opens; tcpipPort number 5555 -> shown as "5555"', (tester) async {
    _mockHost(
      state: _snapshot(
        nightModeOptions: 'nope',
        nightMode: 2,
        tcpipPort: 5555,
        locales: 42,
      ),
    );
    await _pumpSettings(tester);

    // 候选损坏 → 默认三选；nightMode=2 仍能映射到 "On"。
    expect(_row('Dark theme!'), findsOneWidget);
    expect(find.text('On'), findsOneWidget);

    await tester.tap(_row('Dark theme!'));
    await tester.pumpAndSettle();
    expect(find.byType(GlassChoiceDialog), findsOneWidget);
    expect(find.text('Off'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byIcon(Icons.close));
    await tester.pumpAndSettle();
    expect(find.byType(GlassChoiceDialog), findsNothing);

    await tester.tap(_row('TCP port!'));
    await tester.pumpAndSettle();
    expect(find.text('5555'), findsOneWidget);
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, '5555');
    expect(tester.takeException(), isNull);
  });

  test('SettingsSnapshot.fromJson: empty map -> empty; bad types -> defaults', () {
    expect(SettingsSnapshot.fromJson(const <String, dynamic>{}), same(SettingsSnapshot.empty));

    final snap = SettingsSnapshot.fromJson(<String, dynamic>{
      'supportsStartOnBoot': 'yes',
      'bootRoot': 'true',
      'bootWireless': 1,
      'tcpipPort': 5555,
      'nightMode': 'dark',
      'nightModeOptions': <Object>[
        <String, dynamic>{'value': 'x', 'label': 7},
      ],
      'locales': <Object>[
        <String, dynamic>{'tag': 3, 'selected': 'yes'},
      ],
      'translationUrl': 9,
      'copy': 'not-a-map',
    });

    expect(snap.supportsStartOnBoot, isTrue);
    expect(snap.bootRoot, isFalse);
    expect(snap.bootWireless, isFalse);
    expect(snap.tcpipPort, '5555');
    expect(snap.nightMode, -1);
    expect(snap.nightModeOptions.single.value, -1);
    expect(snap.nightModeOptions.single.label, '');
    expect(snap.locales.single.tag, 'SYSTEM');
    expect(snap.locales.single.label, 'SYSTEM');
    expect(snap.locales.single.selected, isFalse);
    expect(snap.translationUrl, '');
    expect(snap.copy.title, SettingsCopy.fallback.title);
  });

  test('derived labels fall back to followSystem / languageSystem', () {
    final snap = SettingsSnapshot.fromJson(<String, dynamic>{
      'nightMode': 99,
      'nightModeOptions': _nightModes,
      'locales': <Object>[
        <String, dynamic>{'tag': 'en', 'label': 'English', 'selected': false},
      ],
      'copy': _copy,
    });
    expect(snap.nightModeLabel(), 'Follow!');
    expect(snap.languageLabel(), 'System lang!');

    final picked = SettingsSnapshot.fromJson(<String, dynamic>{
      'nightMode': 1,
      'nightModeOptions': _nightModes,
      'locales': _locales,
      'copy': _copy,
    });
    expect(picked.nightModeLabel(), 'Off!');
    expect(picked.languageLabel(), '简体中文');
  });

  test('SettingsCopy.fromJson: non-string / empty values fall back per key', () {
    final copy = SettingsCopy.fromJson(<String, dynamic>{
      'title': 123,
      'startup': '',
      'manual': 'Do it',
    });
    expect(copy.title, SettingsCopy.fallback.title);
    expect(copy.startup, SettingsCopy.fallback.startup);
    expect(copy.manual, 'Do it');
    expect(SettingsCopy.fromJson(null), same(SettingsCopy.fallback));
  });

  test('SettingsKeys mirror Kotlin ShizukuSettings / ThemeHelper string values', () {
    expect(SettingsKeys.startOnBoot, 'start_on_boot');
    expect(SettingsKeys.startOnBootWireless, 'start_on_boot_wireless');
    expect(SettingsKeys.autoPairing, 'auto_pairing_enabled');
    expect(SettingsKeys.watchdogAdb, 'watchdog_enabled_adb');
    expect(SettingsKeys.blackNightTheme, 'black_night_theme');
    expect(SettingsKeys.useSystemColor, 'use_system_color');
  });

  test('TcpipPortRange matches Compose 10..65535', () {
    expect(TcpipPortRange.contains(9), isFalse);
    expect(TcpipPortRange.contains(10), isTrue);
    expect(TcpipPortRange.contains(65535), isTrue);
    expect(TcpipPortRange.contains(65536), isFalse);
  });
}
