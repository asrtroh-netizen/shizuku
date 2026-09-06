import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/apps/apps_models.dart';
import 'package:manager_flutter/apps/apps_screen.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/theme/app_theme.dart';
import 'package:manager_flutter/widgets/glass_notice_card.dart';

const MethodChannel _channel = MethodChannel('shizuku/apps');

const Map<String, dynamic> _copy = <String, dynamic>{
  'title': 'Apps title',
  'empty': 'Nothing declared Shizuku yet',
  'requiresRoot': 'needs root',
  'adbLimitedTitle': 'ADB is limited here',
  'adbLimitedMessage': 'Your manufacturer limits adb.',
  'notRunning': 'Service is down',
  'back': 'Back',
  'ok': 'Got it',
};

Map<String, dynamic> _row({
  required String packageName,
  required Object? uid,
  int userId = 0,
  required String label,
  bool requiresRoot = false,
  bool granted = false,
}) =>
    <String, dynamic>{
      'packageName': packageName,
      'uid': uid,
      'userId': userId,
      'label': label,
      'requiresRoot': requiresRoot,
      'granted': granted,
    };

String _snapshot({
  bool running = true,
  bool adbLimited = false,
  Object? apps = const <Object>[],
  Map<String, dynamic> extra = const <String, dynamic>{},
}) =>
    jsonEncode(<String, dynamic>{
      'ok': true,
      'running': running,
      'adbLimited': adbLimited,
      'apps': apps,
      'copy': _copy,
      ...extra,
    });

final List<Map<String, dynamic>> _twoApps = <Map<String, dynamic>>[
  _row(
    packageName: 'com.example.alpha',
    uid: 10101,
    label: 'Alpha',
    requiresRoot: true,
    granted: true,
  ),
  _row(
    packageName: 'com.example.beta',
    uid: 1010102,
    userId: 10,
    label: 'Beta - Work (10)',
  ),
];

void _mock(Future<Object?> Function(MethodCall call)? handler) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_channel, handler);
}

/// 只回 getState；其它方法（getIcon 等）回 `{ok:false}`。
void _mockState(String stateJson, {List<MethodCall>? log}) {
  _mock((call) async {
    log?.add(call);
    if (call.method == 'getState') return stateJson;
    return jsonEncode(<String, dynamic>{'ok': false});
  });
}

Future<void> _pumpApps(WidgetTester tester) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      home: const AppsScreen(),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  tearDown(() => _mock(null));

  testWidgets('no host: fallback notRunning card renders, nothing crashes',
      (tester) async {
    _mock((call) async => throw MissingPluginException());
    await _pumpApps(tester);

    expect(find.text(AppsCopy.fallback.notRunning), findsOneWidget);
    expect(find.text(AppsCopy.fallback.title), findsOneWidget);
    expect(find.byIcon(Icons.info_outline), findsOneWidget);
    expect(find.byType(Switch), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('full snapshot: two cards, two switches, one granted, one root',
      (tester) async {
    _mockState(_snapshot(apps: _twoApps));
    await _pumpApps(tester);

    expect(find.text('Apps title'), findsOneWidget);
    expect(find.text('Alpha'), findsOneWidget);
    expect(find.text('com.example.alpha'), findsOneWidget);
    expect(find.text('Beta - Work (10)'), findsOneWidget);
    expect(find.text('com.example.beta'), findsOneWidget);
    // 列表行是主题 Card（§3.2 v1.1），页面级 GlassPanel 只留给占位 / 空状态卡。
    expect(find.byType(Card), findsNWidgets(2));
    expect(find.byType(GlassPanel), findsNothing);
    expect(find.byType(Switch), findsNWidgets(2));
    expect(find.text('needs root'), findsOneWidget);

    final switches =
        tester.widgetList<Switch>(find.byType(Switch)).toList(growable: false);
    expect(switches.where((s) => s.value).length, 1);
    expect(switches.first.value, isTrue);
    expect(switches.last.value, isFalse);

    // getIcon 回 ok:false → 每张卡显示占位图标。
    expect(find.byIcon(Icons.android_outlined), findsNWidgets(2));
    expect(find.text('Nothing declared Shizuku yet'), findsNothing);
    expect(find.text('Service is down'), findsNothing);
  });

  testWidgets('switch tap sends toggle {packageName, uid}; adbLimited opens alert',
      (tester) async {
    final calls = <MethodCall>[];
    _mock((call) async {
      calls.add(call);
      switch (call.method) {
        case 'getState':
          return _snapshot(apps: _twoApps);
        case 'toggle':
          return _snapshot(
            apps: _twoApps,
            adbLimited: true,
            extra: const <String, dynamic>{'result': 'adbLimited'},
          );
        default:
          return jsonEncode(<String, dynamic>{'ok': false});
      }
    });
    await _pumpApps(tester);

    await tester.tap(find.byType(Switch).first);
    await tester.pumpAndSettle();

    final toggles = calls.where((c) => c.method == 'toggle').toList();
    expect(toggles, hasLength(1));
    expect(
      toggles.single.arguments,
      <String, dynamic>{'packageName': 'com.example.alpha', 'uid': 10101},
    );
    expect(find.text('ADB is limited here'), findsOneWidget);
    expect(find.text('Your manufacturer limits adb.'), findsOneWidget);
    expect(find.text('Got it'), findsOneWidget);

    await tester.tap(find.text('Got it'));
    await tester.pumpAndSettle();
    expect(find.text('ADB is limited here'), findsNothing);
  });

  testWidgets('bad input: apps is a string -> empty-state card, no crash',
      (tester) async {
    _mockState(_snapshot(apps: 'nope'));
    await _pumpApps(tester);

    expect(find.text('Nothing declared Shizuku yet'), findsOneWidget);
    expect(find.byIcon(Icons.info_outline), findsOneWidget);
    expect(find.byType(Switch), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('bad input: uid "x" -> row keeps label and toggles with uid -1',
      (tester) async {
    final calls = <MethodCall>[];
    _mockState(
      _snapshot(
        apps: <Object>[
          _row(packageName: 'com.example.odd', uid: 'x', label: 'Odd'),
        ],
      ),
      log: calls,
    );
    await _pumpApps(tester);

    expect(find.text('Odd'), findsOneWidget);
    expect(find.byType(Switch), findsOneWidget);

    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();
    final toggle = calls.singleWhere((c) => c.method == 'toggle');
    expect(
      toggle.arguments,
      <String, dynamic>{'packageName': 'com.example.odd', 'uid': -1},
    );
  });

  testWidgets('running=false with non-empty apps -> placeholder only (GAP-3)',
      (tester) async {
    _mockState(_snapshot(running: false, apps: _twoApps));
    await _pumpApps(tester);

    expect(find.text('Service is down'), findsOneWidget);
    expect(find.byType(GlassNoticeCard), findsOneWidget);
    expect(find.byType(Card), findsNothing);
    expect(find.byType(Switch), findsNothing);
    expect(find.text('Alpha'), findsNothing);
    expect(find.text('Beta - Work (10)'), findsNothing);
    expect(find.text('Nothing declared Shizuku yet'), findsNothing);
  });

  test('AppRow.fromJson: uid "x" -> -1, missing fields -> defaults', () {
    final row = AppRow.fromJson(<String, dynamic>{
      'packageName': 'a.b',
      'uid': 'x',
      'label': 'A',
    });
    expect(row.uid, -1);
    expect(row.userId, -1);
    expect(row.packageName, 'a.b');
    expect(row.label, 'A');
    expect(row.requiresRoot, isFalse);
    expect(row.granted, isFalse);
    expect(appKey(row), 'a.b#-1');
  });

  test('AppsSnapshot.fromJson: empty map -> empty; apps non-list -> []', () {
    expect(AppsSnapshot.fromJson(const <String, dynamic>{}), same(AppsSnapshot.empty));
    final snap = AppsSnapshot.fromJson(<String, dynamic>{
      'running': true,
      'apps': 'nope',
      'copy': 'not-a-map',
    });
    expect(snap.running, isTrue);
    expect(snap.apps, isEmpty);
    expect(snap.copy.title, AppsCopy.fallback.title);
  });
}
