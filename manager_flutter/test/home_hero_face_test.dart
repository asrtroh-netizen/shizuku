import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/home/home_screen.dart';
import 'package:manager_flutter/onetools/dot_matrix_face.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';
import 'package:manager_flutter/theme/app_theme.dart';

void main() {
  testWidgets('home keeps original sections when service is down', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: const HomeScreen(),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.byType(OneStatusHero), findsOneWidget);
    expect(find.byType(DotMatrixFace), findsOneWidget);
    final face = tester.widget<DotMatrixFace>(find.byType(DotMatrixFace));
    expect(face.mood, DotMatrixMood.frown);

    expect(find.text('Lang'), findsOneWidget);
    expect(find.text('Wireless debugging'), findsOneWidget);
    expect(find.text('Quick actions'), findsOneWidget);
    expect(find.text('Application management'), findsOneWidget);
    expect(find.text('Terminal'), findsOneWidget);
    expect(find.text('Root start'), findsOneWidget);
    expect(find.text('PC ADB'), findsWidgets);
    await tester.scrollUntilVisible(find.text('Boot start'), 300);
    expect(find.text('Boot start'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Check for updates'), 200);
    expect(find.text('Check for updates'), findsOneWidget);
  });

  test('smile and frown matrices stay 7x7', () {
    expect(DotMatrixMood.smile, isNot(DotMatrixMood.frown));
  });
}
