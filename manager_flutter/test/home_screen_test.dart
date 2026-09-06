import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/home/home_cards.dart';
import 'package:manager_flutter/home/home_header.dart';
import 'package:manager_flutter/home/home_models.dart';
import 'package:manager_flutter/home/home_screen.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';
import 'package:manager_flutter/theme/app_theme.dart';
import 'package:manager_flutter/widgets/glass_alert.dart';

const MethodChannel _home = MethodChannel('shizuku/home');

/// `EventChannel('shizuku/home/events')` 的 listen / cancel 也经同名方法通道下发；
/// 打桩成成功，免得每个用例都带一条「无实现」噪声。
const MethodChannel _homeEvents = MethodChannel('shizuku/home/events');

/// 打桩 `shizuku/home`，记录每次调用。[state] 为 `null` 表示无宿主（每个方法都抛
/// [MissingPluginException]）；否则 `getState` 返回它的 JSON，其余方法返回 `{"ok":true}`。
List<MethodCall> mockHome({Map<String, Object?>? state}) {
  final calls = <MethodCall>[];
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(_home, (call) async {
    calls.add(call);
    if (state == null) throw MissingPluginException();
    if (call.method == 'getState') return jsonEncode(state);
    return jsonEncode(const <String, Object?>{'ok': true});
  });
  messenger.setMockMethodCallHandler(_homeEvents, (call) async => null);
  addTearDown(() {
    messenger.setMockMethodCallHandler(_home, null);
    messenger.setMockMethodCallHandler(_homeEvents, null);
  });
  return calls;
}

Iterable<String> methods(List<MethodCall> calls) => calls.map((c) => c.method);

/// 一份「服务在跑」的整页快照；文案全部自定义，好证明渲染的是快照而不是 fallback。
Map<String, Object?> runningState({
  bool adbLimited = false,
  bool showWireless = true,
  bool showPair = true,
}) =>
    <String, Object?>{
      'ok': true,
      'running': true,
      'permission': true,
      'grantedCount': 3,
      'rooted': false,
      'showWireless': showWireless,
      'showPair': showPair,
      'adbLimited': adbLimited,
      'adbCommand': 'adb shell sh /sdcard/start.sh',
      'appsSub': '3 apps authorized',
      'copy': <String, Object?>{
        'appsTitle': 'Manage apps',
        'appsOpen': 'Open the list',
        'terminalTitle': 'Shell',
        'terminalBody': 'Run through Shizuku',
        'adbTitle': 'PC ADB',
        'adbSub': 'From your computer',
        'adbViewCommand': 'Show command',
        'adbCopy': 'Copy it',
        'adbSend': 'Send it',
        'adbLimited': 'Extra step needed',
        'pairing': 'Pair now',
        'quickTitle': 'Quick actions',
        'ok': 'Sure',
        'cancel': 'Never mind',
      },
    };

Future<void> pumpHome(
  WidgetTester tester, {
  VoidCallback? onOpenApps,
  VoidCallback? onOpenTerminal,
  VoidCallback? onOpenPairing,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      home: HomeScreen(
        onOpenApps: onOpenApps ?? () {},
        onOpenTerminal: onOpenTerminal ?? () {},
        onOpenPairing: onOpenPairing ?? () {},
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

Future<void> tapAndSettle(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

int count(List<MethodCall> calls, String method) =>
    calls.where((c) => c.method == method).length;

/// PC ADB 磁贴 → 「查看命令」→ 三键弹窗（复制 / 发送 / 取消）已打开。
Future<void> openCommandDialog(WidgetTester tester) async {
  await tapAndSettle(tester, find.text('From your computer'));
  await tapAndSettle(tester, find.text('Show command'));
  expect(find.byType(GlassAlert), findsOneWidget);
  expect(find.text('adb shell sh /sdcard/start.sh'), findsOneWidget);
}

void main() {
  testWidgets('no host: whole page renders fallback copy, nothing crashes',
      (tester) async {
    mockHome(state: null);
    await pumpHome(tester);

    const f = HomeCopy.fallback;
    expect(find.byType(OneStatusHero), findsOneWidget);
    expect(find.text(f.lang), findsOneWidget);
    expect(find.text(f.wirelessTitle), findsOneWidget);
    expect(find.text(f.quickTitle), findsOneWidget);
    expect(find.text(f.appsTitle), findsOneWidget);
    expect(find.text(f.terminalTitle), findsOneWidget);
    expect(find.text(f.rootTitle), findsOneWidget);
    expect(find.byType(HomeLimitedBanner), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('full snapshot renders snapshot copy, not fallback',
      (tester) async {
    final calls = mockHome(state: runningState());
    await pumpHome(tester);

    expect(methods(calls), contains('getState'));
    expect(find.text('Manage apps'), findsOneWidget);
    expect(find.text('3 apps authorized'), findsOneWidget);
    expect(find.text('Shell'), findsOneWidget);
    expect(find.text('From your computer'), findsOneWidget);
    expect(find.text('Pair now'), findsOneWidget);
    expect(find.text(HomeCopy.fallback.appsTitle), findsNothing);
  });

  testWidgets('apps tile: confirm fires onOpenApps and never calls openApps',
      (tester) async {
    var opened = 0;
    final calls = mockHome(state: runningState());
    await pumpHome(tester, onOpenApps: () => opened++);

    await tapAndSettle(tester, find.text('Manage apps'));
    // 确认弹窗：正文是 appsSub，确认键是 copy.appsOpen（canOpen），带取消键。
    expect(find.byType(GlassAlert), findsOneWidget);
    expect(find.text('Never mind'), findsOneWidget);
    await tapAndSettle(tester, find.text('Open the list'));

    expect(opened, 1);
    expect(find.byType(GlassAlert), findsNothing);
    expect(methods(calls), isNot(contains('openApps')));
  });

  testWidgets('apps tile: cancel does not fire onOpenApps', (tester) async {
    var opened = 0;
    mockHome(state: runningState());
    await pumpHome(tester, onOpenApps: () => opened++);

    await tapAndSettle(tester, find.text('Manage apps'));
    await tapAndSettle(tester, find.text('Never mind'));

    expect(opened, 0);
    expect(find.byType(GlassAlert), findsNothing);
  });

  testWidgets('terminal tile: confirm fires onOpenTerminal, no openTerminal',
      (tester) async {
    var opened = 0;
    final calls = mockHome(state: runningState());
    await pumpHome(tester, onOpenTerminal: () => opened++);

    await tapAndSettle(tester, find.text('Shell'));
    expect(find.text('Run through Shizuku'), findsOneWidget);
    await tapAndSettle(tester, find.text('Sure'));

    expect(opened, 1);
    expect(methods(calls), isNot(contains('openTerminal')));
  });

  testWidgets('theme slide sends toggleTheme', (tester) async {
    final calls = mockHome(state: runningState());
    await pumpHome(tester);
    await tester.tap(find.byType(HomeThemeSlide));
    await tester.pump();
    expect(methods(calls), contains('toggleTheme'));
  });

  testWidgets('pairing button fires onOpenPairing directly, no openPairing',
      (tester) async {
    var opened = 0;
    final calls = mockHome(state: runningState(showPair: true));
    await pumpHome(tester, onOpenPairing: () => opened++);

    await tapAndSettle(tester, find.text('Pair now'));

    expect(opened, 1);
    expect(find.byType(GlassAlert), findsNothing);
    expect(methods(calls), isNot(contains('openPairing')));
  });

  testWidgets('showPair=false hides the pairing button', (tester) async {
    mockHome(state: runningState(showPair: false));
    await pumpHome(tester);
    expect(find.text('Pair now'), findsNothing);
  });

  testWidgets('I-1: list bottom padding clears the dock; SafeArea(bottom: false)',
      (tester) async {
    mockHome(state: null);
    await pumpHome(tester);

    final list = tester.widget<ListView>(find.byType(ListView));
    final padding = list.padding!.resolve(TextDirection.ltr);
    expect(padding.bottom, greaterThanOrEqualTo(Glass.dockHeight));
    expect(padding.left, Glass.pageMargin);
    expect(padding.right, Glass.pageMargin);
    expect(padding.top, Glass.space12);

    final safeArea = tester.widget<SafeArea>(find.descendant(
      of: find.byType(HomeScreen),
      matching: find.byType(SafeArea),
    ));
    expect(safeArea.bottom, isFalse);
    expect(safeArea.top, isTrue);
  });

  testWidgets('adbLimited=true shows HomeLimitedBanner; tap sends openAdbPermissionHelp',
      (tester) async {
    final calls = mockHome(state: runningState(adbLimited: true));
    await pumpHome(tester);

    await tester.dragUntilVisible(
      find.byType(HomeLimitedBanner),
      find.byType(ListView),
      const Offset(0, -200),
    );
    await tester.pumpAndSettle();
    expect(find.byType(HomeLimitedBanner), findsOneWidget);
    expect(find.text('Extra step needed'), findsOneWidget);

    await tester.tap(find.byType(HomeLimitedBanner));
    await tester.pumpAndSettle();
    expect(methods(calls), contains('openAdbPermissionHelp'));
  });

  testWidgets('adbLimited=false: no banner', (tester) async {
    mockHome(state: runningState(adbLimited: false));
    await pumpHome(tester);
    expect(find.byType(HomeLimitedBanner, skipOffstage: false), findsNothing);
  });

  testWidgets('PC ADB: "Send it" -> sendAdbCommand; "Copy it" -> copyAdbCommand',
      (tester) async {
    final calls = mockHome(state: runningState());
    await pumpHome(tester);

    await tapAndSettle(tester, find.text('From your computer'));
    await tapAndSettle(tester, find.text('Show command'));
    expect(find.text('adb shell sh /sdcard/start.sh'), findsOneWidget);
    await tapAndSettle(tester, find.text('Send it'));
    expect(methods(calls), contains('sendAdbCommand'));
    expect(methods(calls), isNot(contains('copyAdbCommand')));

    await tapAndSettle(tester, find.text('From your computer'));
    await tapAndSettle(tester, find.text('Show command'));
    await tapAndSettle(tester, find.text('Copy it'));
    expect(methods(calls), contains('copyAdbCommand'));
  });

  testWidgets('I-5: cancel on the command dialog closes it without an exception',
      (tester) async {
    final calls = mockHome(state: runningState());
    await pumpHome(tester);
    await openCommandDialog(tester);

    await tester.tap(find.text('Never mind'));
    await tester.pumpAndSettle();

    // 修复前：取消键 pop(false) 撞上 showDialog<String> 的路由 → TypeError，弹窗关不掉。
    expect(tester.takeException(), isNull);
    expect(find.byType(GlassAlert), findsNothing);
    expect(find.text('adb shell sh /sdcard/start.sh'), findsNothing);
    expect(count(calls, 'copyAdbCommand'), 0);
    expect(count(calls, 'sendAdbCommand'), 0);
  });

  testWidgets(
      'I-5: command dialog routes Send -> sendAdbCommand, Copy -> copyAdbCommand, dismiss -> nothing',
      (tester) async {
    final calls = mockHome(state: runningState());
    await pumpHome(tester);

    await openCommandDialog(tester);
    await tapAndSettle(tester, find.text('Send it'));
    expect(find.byType(GlassAlert), findsNothing);
    expect(count(calls, 'sendAdbCommand'), 1);
    expect(count(calls, 'copyAdbCommand'), 0);

    await openCommandDialog(tester);
    await tapAndSettle(tester, find.text('Copy it'));
    expect(find.byType(GlassAlert), findsNothing);
    expect(count(calls, 'sendAdbCommand'), 1);
    expect(count(calls, 'copyAdbCommand'), 1);

    // 点遮罩 → showDialog 回 null → 两条通道都不走。
    await openCommandDialog(tester);
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(find.byType(GlassAlert), findsNothing);
    expect(count(calls, 'sendAdbCommand'), 1);
    expect(count(calls, 'copyAdbCommand'), 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('bad input: copy / locales not maps, running not bool -> fallback, no crash',
      (tester) async {
    mockHome(
      state: <String, Object?>{
        'ok': true,
        'running': 'yes',
        'adbLimited': 1,
        'showPair': 'true',
        'copy': 'not-a-map',
        'locales': 'nope',
      },
    );
    await pumpHome(tester);

    expect(find.byType(OneStatusHero), findsOneWidget);
    expect(find.text(HomeCopy.fallback.quickTitle), findsOneWidget);
    expect(find.text(HomeCopy.fallback.pairing), findsNothing);
    expect(find.byType(HomeLimitedBanner, skipOffstage: false), findsNothing);
    expect(tester.takeException(), isNull);
  });

  group('GlassAlert contract (shared by apps / settings after D2)', () {
    Future<Object?> open(
      WidgetTester tester,
      GlassAlert alert,
      String buttonToTap,
    ) async {
      Future<Object?>? result;
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () {
                result = showDialog<Object?>(
                  context: context,
                  builder: (_) => alert,
                );
              },
              child: const Text('open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      await tester.tap(find.text(buttonToTap));
      await tester.pumpAndSettle();
      return result;
    }

    const alert = GlassAlert(
      title: 'T',
      body: 'B',
      confirmLabel: 'Yes',
      cancelLabel: 'No',
    );
    const threeKeys = GlassAlert(
      title: 'T',
      body: 'B',
      confirmLabel: 'Copy',
      extraLabel: 'Send',
      cancelLabel: 'No',
    );

    testWidgets('cancel -> false, confirm -> true', (tester) async {
      expect(await open(tester, alert, 'No'), isFalse);
      expect(await open(tester, alert, 'Yes'), isTrue);
    });

    testWidgets('with extraLabel: confirm -> "confirm", extra -> "extra"',
        (tester) async {
      expect(await open(tester, threeKeys, 'Copy'), 'confirm');
      expect(await open(tester, threeKeys, 'Send'), 'extra');
    });

    testWidgets('no cancelLabel -> no cancel button; error tone paints errorContainer',
        (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          home: const GlassAlert(
            title: 'Limited',
            body: 'Body',
            confirmLabel: 'OK',
            tone: GlassAlertTone.error,
            icon: Icons.info_outline,
          ),
        ),
      );
      await tester.pump();

      expect(find.byType(TextButton), findsOneWidget);
      expect(find.byType(FilledButton), findsNothing);
      expect(find.byType(GlassPanel), findsNothing);
      expect(find.byIcon(Icons.info_outline), findsOneWidget);
      final cs = AppTheme.light().colorScheme;
      final material = tester.widget<Material>(find.ancestor(
        of: find.text('Limited'),
        matching: find.byType(Material),
      ).first);
      expect(material.color, cs.errorContainer);
    });

    testWidgets('neutral tone uses GlassPanel + FilledButton confirm',
        (tester) async {
      await tester.pumpWidget(
        MaterialApp(theme: AppTheme.light(), home: alert),
      );
      await tester.pump();
      expect(find.byType(GlassPanel), findsOneWidget);
      expect(find.byType(FilledButton), findsOneWidget);
      expect(find.byType(TextButton), findsOneWidget);
    });
  });
}
