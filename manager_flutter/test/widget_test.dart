import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/main.dart';
import 'package:manager_flutter/nav/glass_dock.dart';
import 'package:manager_flutter/onetools/dot_matrix_face.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';

void main() {
  test('initialTabFromRoute maps home, settings, and legacy /tab/3', () {
    expect(initialTabFromRoute('/'), 0);
    expect(initialTabFromRoute('/tab/0'), 0);
    expect(initialTabFromRoute('/tab/1'), 1);
    expect(initialTabFromRoute('/tab/2'), 0);
    expect(initialTabFromRoute('/tab/3'), 1);
    expect(initialTabFromRoute('/nope'), 0);
  });

  testWidgets('app boots Ultra hero with two-item dock', (tester) async {
    await tester.pumpWidget(const ShizukuFlutterApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.byType(OneStatusHero), findsOneWidget);
    expect(find.byType(DotMatrixFace), findsOneWidget);
    expect(find.text('Quick actions'), findsOneWidget);
    expect(find.byType(NavigationDestination), findsNWidgets(2));
    expect(
      find.descendant(
        of: find.byType(GlassDock),
        matching: find.byIcon(Icons.apps_outlined),
      ),
      findsNothing,
    );
    expect(
      find.descendant(
        of: find.byType(GlassDock),
        matching: find.byIcon(Icons.terminal_outlined),
      ),
      findsNothing,
    );
  });

  testWidgets('MaterialApp follows snapshot.dark instead of system brightness',
      (tester) async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      const MethodChannel('shizuku/home'),
      (call) async {
        if (call.method == 'getState') {
          return jsonEncode(<String, Object?>{
            'ok': true,
            'dark': true,
            'copy': <String, Object?>{},
          });
        }
        return jsonEncode(const <String, Object?>{'ok': true});
      },
    );
    addTearDown(
      () => messenger.setMockMethodCallHandler(
        const MethodChannel('shizuku/home'),
        null,
      ),
    );

    await tester.pumpWidget(const ShizukuFlutterApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
    expect(app.themeMode, ThemeMode.dark);
  });
}
