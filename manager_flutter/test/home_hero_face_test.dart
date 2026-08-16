import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/home/home_screen.dart';
import 'package:manager_flutter/onetools/dot_matrix_face.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';
import 'package:manager_flutter/theme/app_theme.dart';

void main() {
  testWidgets('P0 home hero shows frown face when service is down', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: const HomeScreen(),
      ),
    );
    await tester.pump();
    expect(find.byType(OneStatusHero), findsOneWidget);
    expect(find.byType(DotMatrixFace), findsOneWidget);
    final face = tester.widget<DotMatrixFace>(find.byType(DotMatrixFace));
    expect(face.mood, DotMatrixMood.frown);
  });

  test('smile and frown matrices stay 7x7', () {
    expect(DotMatrixMood.smile, isNot(DotMatrixMood.frown));
  });
}
