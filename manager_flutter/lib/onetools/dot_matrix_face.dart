import 'package:flutter/material.dart';

/// 点阵脸：笑 = 就绪，哭 = 激活中 / 未激活。形状本身编码状态，不只靠颜色。
enum DotMatrixMood { smile, frown }

class DotMatrixFace extends StatelessWidget {
  const DotMatrixFace({
    super.key,
    required this.mood,
    required this.color,
    this.size = 36,
    this.semanticLabel,
  });

  final DotMatrixMood mood;
  final Color color;
  final double size;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: semanticLabel,
      image: true,
      child: CustomPaint(
        size: Size.square(size),
        painter: _DotMatrixFacePainter(mood: mood, color: color),
      ),
    );
  }
}

class _DotMatrixFacePainter extends CustomPainter {
  const _DotMatrixFacePainter({required this.mood, required this.color});

  final DotMatrixMood mood;
  final Color color;

  /// 7×7：眼睛固定，嘴形区分笑/哭。
  static const _smile = <int>[
    0, 0, 0, 0, 0, 0, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 0, 0, 0, 0, 0, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 0, 1, 1, 1, 0, 0,
    0, 0, 0, 0, 0, 0, 0,
  ];

  static const _frown = <int>[
    0, 0, 0, 0, 0, 0, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 0, 0, 0, 0, 0, 0,
    0, 0, 1, 1, 1, 0, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 0, 0, 0, 0, 0, 0,
  ];

  @override
  void paint(Canvas canvas, Size size) {
    const cells = 7;
    final gap = size.shortestSide * 0.08;
    final cell = (size.shortestSide - gap * (cells - 1)) / cells;
    final radius = cell * 0.42;
    final lit = Paint()..color = color;
    final dim = Paint()..color = color.withValues(alpha: 0.14);
    final pattern = mood == DotMatrixMood.smile ? _smile : _frown;

    for (var y = 0; y < cells; y++) {
      for (var x = 0; x < cells; x++) {
        final on = pattern[y * cells + x] == 1;
        final cx = x * (cell + gap) + cell / 2;
        final cy = y * (cell + gap) + cell / 2;
        canvas.drawCircle(Offset(cx, cy), radius, on ? lit : dim);
      }
    }
  }

  @override
  bool shouldRepaint(covariant _DotMatrixFacePainter old) =>
      old.mood != mood || old.color != color;
}
