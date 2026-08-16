package moe.shizuku.manager.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * OneIMS Ultra 点阵脸的 Compose 镜像。
 *
 * 矩阵、间隙、半径、暗点透明度必须与
 * `oneims_flutter/lib/onetools/dot_matrix_face.dart` 逐字节同构：
 * 笑 = 就绪，哭 = 未就绪 / 启动中；形状编码状态，不只靠颜色。
 */
enum class DotMatrixMood { Smile, Frown }

@Composable
fun DotMatrixFace(
    mood: DotMatrixMood,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    semanticLabel: String? = null,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (semanticLabel.isNullOrEmpty()) Modifier
                else Modifier.semantics { contentDescription = semanticLabel },
            ),
    ) {
        val cells = 7
        val side = this.size.minDimension
        val gap = side * 0.08f
        val cell = (side - gap * (cells - 1)) / cells
        val radius = cell * 0.42f
        val dim = color.copy(alpha = 0.14f)
        val pattern = if (mood == DotMatrixMood.Smile) SMILE else FROWN
        for (y in 0 until cells) {
            for (x in 0 until cells) {
                val on = pattern[y * cells + x] == 1
                val cx = x * (cell + gap) + cell / 2f
                val cy = y * (cell + gap) + cell / 2f
                drawCircle(
                    color = if (on) color else dim,
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

/** 点阵脸坐在一层更实的小玻璃砖上，对齐 Ultra `_FaceWell`。 */
@Composable
fun DotMatrixFaceWell(
    smile: Boolean,
    color: Color,
    semanticLabel: String,
    modifier: Modifier = Modifier,
) {
    val radius = 14.dp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color.copy(alpha = 0.08f))
            .border(0.8.dp, color.copy(alpha = 0.22f), RoundedCornerShape(radius))
            .padding(8.dp),
    ) {
        DotMatrixFace(
            mood = if (smile) DotMatrixMood.Smile else DotMatrixMood.Frown,
            color = color,
            semanticLabel = semanticLabel,
        )
    }
}

private val SMILE = intArrayOf(
    0, 0, 0, 0, 0, 0, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 0, 0, 0, 0, 0, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 0, 1, 1, 1, 0, 0,
    0, 0, 0, 0, 0, 0, 0,
)

private val FROWN = intArrayOf(
    0, 0, 0, 0, 0, 0, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 0, 0, 0, 0, 0, 0,
    0, 0, 1, 1, 1, 0, 0,
    0, 1, 0, 0, 0, 1, 0,
    0, 0, 0, 0, 0, 0, 0,
)
