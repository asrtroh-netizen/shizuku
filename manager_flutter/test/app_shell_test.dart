import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/home/home_screen.dart';
import 'package:manager_flutter/nav/app_shell.dart';
import 'package:manager_flutter/nav/glass_dock.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';
import 'package:manager_flutter/theme/app_theme.dart';

const MethodChannel _homeChannel = MethodChannel('shizuku/home');

/// 打桩 `shizuku/home`：记录每次调用的方法名。
/// [state] 为 `null` 表示无宿主（每个方法都抛 [MissingPluginException]）；
/// 否则 `getState` 返回它的 JSON，其余方法返回 `{"ok":true}`。
List<String> mockHome({Map<String, Object?>? state}) {
  final calls = <String>[];
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(_homeChannel, (call) async {
    calls.add(call.method);
    if (state == null) throw MissingPluginException();
    if (call.method == 'getState') return jsonEncode(state);
    return jsonEncode(const <String, Object?>{'ok': true});
  });
  addTearDown(() => messenger.setMockMethodCallHandler(_homeChannel, null));
  return calls;
}

Future<void> pumpShell(WidgetTester tester) async {
  await tester.pumpWidget(
    MaterialApp(theme: AppTheme.light(), home: const AppShell()),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

Finder dockLabel(String text) =>
    find.descendant(of: find.byType(NavigationBar), matching: find.text(text));

Finder dockIcon(IconData icon) =>
    find.descendant(of: find.byType(GlassDock), matching: find.byIcon(icon));

int stackIndex(WidgetTester tester) =>
    tester.widget<IndexedStack>(find.byType(IndexedStack)).index!;

void main() {
  testWidgets(
    'no host: shell renders dock with fallback labels and home hero',
    (tester) async {
      mockHome(state: null);
      await pumpShell(tester);

      expect(find.byType(AppShell), findsOneWidget);
      expect(find.byType(GlassDock), findsOneWidget);
      expect(find.byType(NavigationDestination), findsNWidgets(4));
      expect(dockLabel('Shizuku'), findsOneWidget);
      expect(dockLabel('Apps'), findsOneWidget);
      expect(dockLabel('Terminal'), findsOneWidget);
      expect(dockLabel('Settings'), findsOneWidget);
      expect(dockIcon(Icons.home_outlined), findsOneWidget);
      expect(dockIcon(Icons.apps_outlined), findsOneWidget);
      expect(dockIcon(Icons.terminal_outlined), findsOneWidget);
      expect(dockIcon(Icons.settings_outlined), findsOneWidget);
      expect(stackIndex(tester), 0);
      expect(find.byType(HomeScreen), findsOneWidget);
      expect(find.byType(OneStatusHero), findsOneWidget);
    },
  );

  testWidgets('tapping a destination switches tab; home stays alive', (
    tester,
  ) async {
    mockHome(state: null);
    await pumpShell(tester);

    final homeWidget = tester.widget<HomeScreen>(find.byType(HomeScreen));
    final homeState = tester.state(find.byType(HomeScreen));

    await tester.tap(dockIcon(Icons.apps_outlined));
    await tester.pumpAndSettle();

    expect(stackIndex(tester), 1);
    expect(find.byIcon(Icons.hourglass_empty_outlined), findsOneWidget);
    // 底栏标签 + 占位卡标签各一份。
    expect(find.text('Apps'), findsNWidgets(2));
    // 非选中页不在台上，但仍挂在树里（IndexedStack 保活）。
    expect(find.byType(HomeScreen), findsNothing);
    expect(find.byType(HomeScreen, skipOffstage: false), findsOneWidget);

    await tester.tap(dockIcon(Icons.home_outlined));
    await tester.pumpAndSettle();

    expect(stackIndex(tester), 0);
    expect(find.byType(OneStatusHero), findsOneWidget);
    expect(
      identical(tester.widget<HomeScreen>(find.byType(HomeScreen)), homeWidget),
      isTrue,
    );
    expect(identical(tester.state(find.byType(HomeScreen)), homeState), isTrue);
  });

  testWidgets('home apps tile switches to the Apps tab instead of openApps', (
    tester,
  ) async {
    final calls = mockHome(
      state: <String, Object?>{
        'ok': true,
        'running': true,
        'permission': true,
        'grantedCount': 3,
        'showWireless': false,
        'appsSub': '3 apps authorized',
        'copy': <String, Object?>{'tabApps': 'Apps'},
      },
    );
    await pumpShell(tester);
    expect(calls, contains('getState'));

    await tester.ensureVisible(find.text('Application management'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Application management'));
    await tester.pumpAndSettle();

    // 确认弹窗：canOpen 时确认键文案是 copy.appsOpen。
    await tester.tap(find.text('Tap to manage authorized apps'));
    await tester.pumpAndSettle();

    expect(stackIndex(tester), 1);
    expect(find.byIcon(Icons.hourglass_empty_outlined), findsOneWidget);
    expect(calls, isNot(contains('openApps')));
  });

  testWidgets('bad input: non-string tabApps falls back to "Apps"', (
    tester,
  ) async {
    mockHome(
      state: <String, Object?>{
        'ok': true,
        'running': false,
        'copy': <String, Object?>{'tabApps': 123, 'tabHome': 'Shizuku'},
      },
    );
    await pumpShell(tester);

    expect(dockLabel('Apps'), findsOneWidget);
    expect(dockLabel('123'), findsNothing);
    expect(dockLabel('Shizuku'), findsOneWidget);
  });
}
