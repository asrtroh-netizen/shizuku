import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/main.dart';
import 'package:manager_flutter/onetools/dot_matrix_face.dart';
import 'package:manager_flutter/onetools/one_status_hero.dart';

void main() {
  testWidgets('app boots Ultra hero with dot-matrix face', (tester) async {
    await tester.pumpWidget(const ShizukuFlutterApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.byType(OneStatusHero), findsOneWidget);
    expect(find.byType(DotMatrixFace), findsOneWidget);
    expect(find.text('Quick actions'), findsOneWidget);
  });
}
