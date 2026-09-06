import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/apps/apps_screen.dart';
import 'package:manager_flutter/home/home_screen.dart';
import 'package:manager_flutter/nav/app_shell.dart';
import 'package:manager_flutter/nav/glass_dock.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/onetools/liquid_glass.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';
import 'package:manager_flutter/settings/settings_screen.dart';
import 'package:manager_flutter/theme/app_theme.dart';

const MethodChannel _homeChannel = MethodChannel('shizuku/home');
const MethodChannel _appsChannel = MethodChannel('shizuku/apps');
const MethodChannel _settingsChannel = MethodChannel('shizuku/settings');
const MethodChannel _terminalChannel = MethodChannel('shizuku/terminal');

/// 打桩 `shizuku/home`：记录每次调用的方法名。
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

void mockEmptyPageChannels() {
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  Future<String> empty(MethodCall call) async {
    if (call.method == 'getState') {
      return jsonEncode(const <String, Object?>{'ok': true});
    }
    return jsonEncode(const <String, Object?>{'ok': true});
  }

  messenger.setMockMethodCallHandler(_appsChannel, empty);
  messenger.setMockMethodCallHandler(_settingsChannel, empty);
  messenger.setMockMethodCallHandler(_terminalChannel, empty);
  addTearDown(() {
    messenger.setMockMethodCallHandler(_appsChannel, null);
    messenger.setMockMethodCallHandler(_settingsChannel, null);
    messenger.setMockMethodCallHandler(_terminalChannel, null);
  });
}

Future<void> pumpShell(WidgetTester tester) async {
  mockEmptyPageChannels();
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
  test('glassDockIslandWidth is compact for two destinations', () {
    expect(glassDockIslandWidth(2, 400), Glass.dockItemWidth * 2 + Glass.dockBarInset * 2);
    expect(glassDockIslandWidth(2, 400), lessThan(400 - Glass.pageMargin * 2));
    expect(glassDockIslandWidth(8, 400), 400 - Glass.pageMargin * 2);
  });

  testWidgets(
    'no host: shell renders two-item dock and home hero',
    (tester) async {
      mockHome(state: null);
      await pumpShell(tester);

      expect(find.byType(AppShell), findsOneWidget);
      expect(find.byType(GlassDock), findsOneWidget);
      expect(find.byType(NavigationDestination), findsNWidgets(2));
      expect(dockLabel('Shizuku'), findsOneWidget);
      expect(dockLabel('Settings'), findsOneWidget);
      expect(dockLabel('Apps'), findsNothing);
      expect(dockLabel('Terminal'), findsNothing);
      expect(dockIcon(Icons.home_outlined), findsOneWidget);
      expect(dockIcon(Icons.settings_outlined), findsOneWidget);
      expect(dockIcon(Icons.apps_outlined), findsNothing);
      expect(dockIcon(Icons.terminal_outlined), findsNothing);
      expect(stackIndex(tester), 0);
      expect(find.byType(HomeScreen), findsOneWidget);
      expect(find.byType(OneStatusHero), findsOneWidget);
    },
  );

  testWidgets('two-item dock island is compact and centered', (tester) async {
    mockHome(state: null);
    await pumpShell(tester);

    final island = tester.getRect(find.byType(LiquidGlass));
    final shell = tester.getRect(find.byType(AppShell));
    expect(island.width, glassDockIslandWidth(2, shell.width));
    expect(island.width, lessThan(shell.width - Glass.pageMargin * 2));
    expect((island.center.dx - shell.center.dx).abs(), lessThan(1));
  });

  testWidgets('tapping settings switches tab; home stays alive', (
    tester,
  ) async {
    mockHome(state: null);
    await pumpShell(tester);

    final homeWidget = tester.widget<HomeScreen>(find.byType(HomeScreen));
    final homeState = tester.state(find.byType(HomeScreen));

    await tester.tap(dockIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();

    expect(stackIndex(tester), 1);
    expect(find.byType(SettingsScreen), findsOneWidget);
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

  testWidgets('home apps tile pushes AppsScreen instead of switching tabs', (
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

    await tester.tap(find.text('Tap to manage authorized apps'));
    await tester.pumpAndSettle();

    expect(find.byType(AppsScreen), findsOneWidget);
    expect(find.byIcon(Icons.arrow_back_outlined), findsOneWidget);
    expect(
      tester
          .widget<IndexedStack>(
            find.byType(IndexedStack, skipOffstage: false),
          )
          .index,
      0,
    );
    expect(calls, isNot(contains('openApps')));
  });

  testWidgets('bad input: non-string tabHome falls back to "Shizuku"', (
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

    expect(dockLabel('Apps'), findsNothing);
    expect(dockLabel('123'), findsNothing);
    expect(dockLabel('Shizuku'), findsOneWidget);
    expect(dockLabel('Settings'), findsOneWidget);
  });
}
