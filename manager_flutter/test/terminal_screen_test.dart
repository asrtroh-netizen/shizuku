import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/terminal/terminal_models.dart';
import 'package:manager_flutter/terminal/terminal_screen.dart';
import 'package:manager_flutter/theme/app_theme.dart';

const MethodChannel _channel = MethodChannel('shizuku/terminal');

const Map<String, dynamic> _copy = <String, dynamic>{
  'title': 'Terminal title',
  'back': 'Back',
  'open': 'Open doc',
  'rishDescription': 'Rish description text',
  'tutorial1': 'Step one title',
  'tutorial1Description': 'Step one body',
  'exportFiles': 'Export now',
  'tutorial2': 'Step two title',
  'tutorial2Description': 'Step two body',
  'tutorial3': 'Step three title',
  'tutorial3Description': 'Step three body',
};

String _snapshot({
  Object? shName = 'rish',
  Object? dexName = 'rish_shizuku.dex',
  Object? copy = _copy,
}) =>
    jsonEncode(<String, dynamic>{
      'ok': true,
      'shName': shName,
      'dexName': dexName,
      'copy': copy,
    });

void _mock(Future<Object?> Function(MethodCall call)? handler) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_channel, handler);
}

/// `getState` 回 [stateJson]，其它方法回 `{ok:true}`；返回调用日志。
List<MethodCall> _mockState(String stateJson) {
  final calls = <MethodCall>[];
  _mock((call) async {
    calls.add(call);
    if (call.method == 'getState') return stateJson;
    return jsonEncode(<String, dynamic>{'ok': true});
  });
  return calls;
}

int _count(List<MethodCall> calls, String method) =>
    calls.where((c) => c.method == method).length;

/// 页面是懒构建的 ListView；给一块高视口让 4 张卡都进视口，断言才看得到第三步。
Future<void> _pumpTerminal(WidgetTester tester) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      home: const TerminalScreen(),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  tearDown(() => _mock(null));

  testWidgets('no host: fallback title, default file names, four glass panels',
      (tester) async {
    _mock((call) async => throw MissingPluginException());
    await _pumpTerminal(tester);

    expect(find.text(TerminalCopy.fallback.title), findsOneWidget);
    expect(find.text('rish'), findsOneWidget);
    expect(find.text('rish_shizuku.dex'), findsOneWidget);
    expect(find.text(TerminalCopy.fallback.rishDescription), findsOneWidget);
    expect(find.text(TerminalCopy.fallback.tutorial1), findsOneWidget);
    expect(find.text(TerminalCopy.fallback.tutorial1Description), findsOneWidget);
    expect(find.text(TerminalCopy.fallback.exportFiles), findsOneWidget);
    expect(find.text(TerminalCopy.fallback.tutorial2), findsOneWidget);
    expect(find.text(TerminalCopy.fallback.tutorial3Description), findsOneWidget);
    // 说明卡 + 三步 = 4 张页面级 GlassPanel（§3.1 上限），没有列表 Card。
    expect(find.byType(GlassPanel), findsNWidgets(4));
    expect(find.byType(Card), findsNothing);
    expect(find.byType(FilledButton), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('full snapshot: custom shName/dexName and copy render',
      (tester) async {
    _mockState(_snapshot(shName: 'foo', dexName: 'foo.dex'));
    await _pumpTerminal(tester);

    expect(find.text('Terminal title'), findsOneWidget);
    expect(find.text('foo'), findsOneWidget);
    expect(find.text('foo.dex'), findsOneWidget);
    expect(find.text('Rish description text'), findsOneWidget);
    expect(find.text('Step one title'), findsOneWidget);
    expect(find.text('Step one body'), findsOneWidget);
    expect(find.text('Export now'), findsOneWidget);
    expect(find.text('Step two title'), findsOneWidget);
    expect(find.text('Step two body'), findsOneWidget);
    expect(find.text('Step three title'), findsOneWidget);
    expect(find.text('Step three body'), findsOneWidget);
    expect(find.text('rish'), findsNothing);
    expect(find.text(TerminalCopy.fallback.title), findsNothing);

    // 文件名走等宽；open-in-new 图标的语义标签 = copy.open（Compose contentDescription）。
    final foo = tester.widget<Text>(find.text('foo'));
    expect(foo.style?.fontFamily, 'monospace');
    final open = tester.widget<Icon>(find.byIcon(Icons.open_in_new_outlined));
    expect(open.semanticLabel, 'Open doc');
  });

  testWidgets('export button sends exportFiles; callout tap sends openGuide',
      (tester) async {
    final calls = _mockState(_snapshot());
    await _pumpTerminal(tester);
    expect(_count(calls, 'getState'), 1);

    await tester.tap(find.text('Export now'));
    await tester.pumpAndSettle();
    expect(_count(calls, 'exportFiles'), 1);
    expect(_count(calls, 'openGuide'), 0);
    expect(calls.singleWhere((c) => c.method == 'exportFiles').arguments, isNull);

    await tester.tap(find.byIcon(Icons.open_in_new_outlined));
    await tester.pumpAndSettle();
    expect(_count(calls, 'openGuide'), 1);
    expect(_count(calls, 'exportFiles'), 1);

    // 整张说明卡都可点（Compose Card.onClick）。
    await tester.tap(find.text('Rish description text'));
    await tester.pumpAndSettle();
    expect(_count(calls, 'openGuide'), 2);
  });

  testWidgets('bad input: copy is a string, shName is a number -> fallback, no crash',
      (tester) async {
    _mockState(_snapshot(shName: 42, copy: 'nope'));
    await _pumpTerminal(tester);

    expect(find.text(TerminalCopy.fallback.title), findsOneWidget);
    expect(find.text(TerminalCopy.fallback.exportFiles), findsOneWidget);
    expect(find.text('rish'), findsOneWidget);
    expect(find.text('rish_shizuku.dex'), findsOneWidget);
    expect(find.text('42'), findsNothing);
    expect(find.text('Terminal title'), findsNothing);
    expect(find.byType(GlassPanel), findsNWidgets(4));
    expect(tester.takeException(), isNull);
  });

  test('TerminalSnapshot.fromJson: empty -> empty; bad types -> defaults', () {
    expect(
      TerminalSnapshot.fromJson(const <String, dynamic>{}),
      same(TerminalSnapshot.empty),
    );
    final snap = TerminalSnapshot.fromJson(<String, dynamic>{
      'shName': '',
      'dexName': 7,
      'copy': <Object>['x'],
    });
    expect(snap.shName, 'rish');
    expect(snap.dexName, 'rish_shizuku.dex');
    expect(snap.copy.title, TerminalCopy.fallback.title);
  });

  test('TerminalCopy.fromJson: keeps given keys, fills the rest from fallback',
      () {
    expect(TerminalCopy.fromJson(null), same(TerminalCopy.fallback));
    final copy = TerminalCopy.fromJson(<String, dynamic>{
      'title': 'T',
      'exportFiles': 7,
      'open': '',
    });
    expect(copy.title, 'T');
    expect(copy.exportFiles, TerminalCopy.fallback.exportFiles);
    expect(copy.open, TerminalCopy.fallback.open);
    expect(copy.tutorial3, TerminalCopy.fallback.tutorial3);
  });
}
