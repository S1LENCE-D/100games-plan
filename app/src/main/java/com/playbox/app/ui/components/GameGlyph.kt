package com.playbox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.playbox.app.R
import kotlin.math.roundToInt
import com.playbox.app.domain.model.GameId

/**
 * 游戏大厅统一图标：所有图标使用同一个 primaryContainer 容器、
 * 同一笔触和 24×24 的绘制网格，保证视觉风格与光学居中一致。
 */
@Composable
fun GameGlyph(
    gameId: GameId,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val container = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val accent = MaterialTheme.colorScheme.tertiary
    val secondaryAccent = MaterialTheme.colorScheme.primary
    val dinoSprite = if (gameId == GameId.DINO) {
        ImageBitmap.imageResource(R.drawable.dino_sprite_sheet)
    } else {
        null
    }
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.28f),
        color = container,
        contentColor = ink,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.18f),
        ) {
            drawUnifiedGlyph(
                gameId = gameId,
                ink = ink,
                accent = accent,
                secondaryAccent = secondaryAccent,
                container = container,
                dinoSprite = dinoSprite,
            )
        }
    }
}

private fun DrawScope.drawUnifiedGlyph(
    gameId: GameId,
    ink: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color,
    secondaryAccent: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
    dinoSprite: ImageBitmap?,
) {
    val unit = size.minDimension / 24f
    val stroke = unit * 1.8f
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(
            color = ink,
            start = p(x1, y1),
            end = p(x2, y2),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
    fun block(x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
        drawRoundRect(
            color = ink.copy(alpha = alpha),
            topLeft = p(x, y),
            size = Size(w * unit, h * unit),
            cornerRadius = CornerRadius(unit * 1.2f, unit * 1.2f),
        )
    }

    when (gameId) {
        GameId.GAME_2048 -> {
            block(4f, 4f, 7f, 7f, 1f)
            block(13f, 4f, 7f, 7f, 0.74f)
            block(4f, 13f, 7f, 7f, 0.52f)
            block(13f, 13f, 7f, 7f, 0.34f)
        }

        GameId.TETRIS -> {
            block(5f, 4f, 5f, 5f)
            block(10f, 4f, 5f, 5f)
            block(15f, 4f, 5f, 5f)
            block(10f, 9f, 5f, 5f)
            block(10f, 14f, 5f, 4f, 0.45f)
        }

        GameId.SNAKE -> {
            val path = Path().apply {
                moveTo(18f * unit, 7f * unit)
                cubicTo(18f * unit, 3f * unit, 6f * unit, 3f * unit, 6f * unit, 7f * unit)
                cubicTo(6f * unit, 11f * unit, 18f * unit, 11f * unit, 18f * unit, 15f * unit)
                cubicTo(18f * unit, 19f * unit, 6f * unit, 19f * unit, 6f * unit, 15f * unit)
            }
            drawPath(
                path = path,
                color = ink,
                style = Stroke(
                    width = stroke * 1.35f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawCircle(color = ink, radius = unit * 2.2f, center = p(6f, 15f))
        }

        GameId.BREAKOUT -> {
            block(4f, 5f, 5f, 3f, 0.85f)
            block(10f, 5f, 5f, 3f, 0.62f)
            block(16f, 5f, 4f, 3f, 0.85f)
            drawCircle(color = ink, radius = unit * 2.1f, center = p(12f, 13f))
            drawLine(
                color = ink,
                start = p(6f, 19f),
                end = p(18f, 19f),
                strokeWidth = stroke * 1.4f,
                cap = StrokeCap.Round,
            )
        }

        GameId.MINESWEEPER -> {
            line(8f, 5f, 8f, 20f)
            val flag = Path().apply {
                moveTo(8f * unit, 6f * unit)
                lineTo(18f * unit, 9f * unit)
                lineTo(8f * unit, 12f * unit)
                close()
            }
            drawPath(path = flag, color = ink)
            line(5f, 20f, 11f, 20f)
        }

        GameId.MEMORY -> {
            drawRoundRect(
                color = ink.copy(alpha = 0.42f),
                topLeft = p(4f, 7f),
                size = Size(9f * unit, 12f * unit),
                cornerRadius = CornerRadius(unit * 1.5f, unit * 1.5f),
            )
            drawRoundRect(
                color = ink,
                topLeft = p(10f, 4f),
                size = Size(10f * unit, 13f * unit),
                cornerRadius = CornerRadius(unit * 1.5f, unit * 1.5f),
            )
            line(13f, 8f, 17f, 8f)
            line(13f, 12f, 17f, 12f)
        }

        GameId.SUDOKU -> {
            val grid = listOf(4f, 9.33f, 14.66f, 20f)
            grid.forEach { coordinate ->
                line(coordinate, 4f, coordinate, 20f)
                line(4f, coordinate, 20f, coordinate)
            }
            block(9.33f, 9.33f, 5.33f, 5.33f, 0.34f)
        }

        GameId.TIC_TAC_TOE -> {
            drawLine(
                color = ink,
                start = p(5f, 5f),
                end = p(19f, 19f),
                strokeWidth = stroke * 1.5f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ink,
                start = p(19f, 5f),
                end = p(5f, 19f),
                strokeWidth = stroke * 1.5f,
                cap = StrokeCap.Round,
            )
        }

        GameId.FIFTEEN_PUZZLE -> {
            val coordinates = listOf(4f, 9.66f, 15.33f)
            coordinates.forEachIndexed { row, y ->
                coordinates.forEachIndexed { column, x ->
                    if (row == 2 && column == 2) return@forEachIndexed
                    drawRoundRect(
                        color = ink.copy(alpha = if (row == 0) 0.9f else 0.55f),
                        topLeft = p(x, y),
                        size = Size(4.66f * unit, 4.66f * unit),
                        cornerRadius = CornerRadius(unit, unit),
                    )
                }
            }
        }

        GameId.BALL_BATTLE -> {
            drawCircle(color = accent, radius = 6.2f * unit, center = p(8.7f, 12.8f))
            drawCircle(color = secondaryAccent, radius = 3.6f * unit, center = p(17.3f, 8.8f))
        }

        GameId.GOMOKU -> {
            line(4.5f, 4.5f, 19.5f, 4.5f)
            line(4.5f, 12f, 19.5f, 12f)
            line(4.5f, 19.5f, 19.5f, 19.5f)
            line(4.5f, 4.5f, 4.5f, 19.5f)
            line(12f, 4.5f, 12f, 19.5f)
            line(19.5f, 4.5f, 19.5f, 19.5f)
            drawCircle(color = accent, radius = 3.4f * unit, center = p(12f, 12f))
            drawCircle(color = secondaryAccent, radius = 2.4f * unit, center = p(4.5f, 4.5f))
            drawCircle(color = ink, radius = 2.4f * unit, center = p(19.5f, 19.5f))
        }

        GameId.CHESS -> {
            val crown = Path().apply {
                moveTo(4.5f * unit, 18.5f * unit)
                lineTo(4.5f * unit, 9.5f * unit)
                lineTo(8.2f * unit, 12.8f * unit)
                lineTo(11.5f * unit, 6.5f * unit)
                lineTo(14.8f * unit, 12.8f * unit)
                lineTo(18.5f * unit, 9.5f * unit)
                lineTo(18.5f * unit, 18.5f * unit)
                close()
            }
            drawPath(crown, accent)
            line(4.5f, 20.5f, 18.5f, 20.5f)
            drawCircle(color = secondaryAccent, radius = 1.2f * unit, center = p(11.5f, 5f))
            line(11.5f, 3.8f, 11.5f, 6.2f)
            line(10.3f, 5f, 12.7f, 5f)
        }

        GameId.XIANGQI -> {
            line(4f, 5f, 20f, 5f)
            line(4f, 12f, 20f, 12f)
            line(4f, 19f, 20f, 19f)
            line(4f, 5f, 4f, 19f)
            line(12f, 5f, 12f, 19f)
            line(20f, 5f, 20f, 19f)
            drawCircle(color = accent, radius = 4.2f * unit, center = p(8f, 8.5f))
            drawCircle(color = ink, radius = 2.2f * unit, center = p(8f, 8.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * unit))
            drawCircle(color = secondaryAccent, radius = 4.2f * unit, center = p(16f, 15.5f))
        }

        GameId.UNO -> {
            drawRoundRect(
                color = ink.copy(alpha = 0.12f),
                topLeft = p(5f, 3.5f),
                size = Size(14f * unit, 17f * unit),
                cornerRadius = CornerRadius(3f * unit),
            )
            drawRoundRect(
                color = ink,
                topLeft = p(5f, 3.5f),
                size = Size(14f * unit, 17f * unit),
                cornerRadius = CornerRadius(3f * unit),
                style = Stroke(width = 1.3f * unit),
            )
            drawCircle(color = accent, radius = 2.1f * unit, center = p(9.2f, 8.2f))
            drawCircle(color = secondaryAccent, radius = 2.1f * unit, center = p(14.8f, 8.2f))
            drawCircle(color = accent.copy(alpha = 0.55f), radius = 2.1f * unit, center = p(9.2f, 15.8f))
            drawCircle(color = secondaryAccent.copy(alpha = 0.55f), radius = 2.1f * unit, center = p(14.8f, 15.8f))
        }

        GameId.DOUDIZHU -> {
            drawRoundRect(
                color = accent.copy(alpha = 0.55f),
                topLeft = p(4f, 7f),
                size = Size(9f * unit, 13f * unit),
                cornerRadius = CornerRadius(2f * unit),
            )
            drawRoundRect(
                color = secondaryAccent.copy(alpha = 0.8f),
                topLeft = p(10f, 4f),
                size = Size(9f * unit, 13f * unit),
                cornerRadius = CornerRadius(2f * unit),
            )
            drawRoundRect(
                color = ink,
                topLeft = p(10f, 4f),
                size = Size(9f * unit, 13f * unit),
                cornerRadius = CornerRadius(2f * unit),
                style = Stroke(width = 1.2f * unit),
            )
            drawCircle(color = ink, radius = 1.4f * unit, center = p(14.5f, 10.5f))
        }

        GameId.LIQUID_SORT -> {
            val glass = Path().apply {
                moveTo(8f * unit, 3f * unit)
                lineTo(16f * unit, 3f * unit)
                lineTo(16f * unit, 18.5f * unit)
                lineTo(15f * unit, 21f * unit)
                lineTo(9f * unit, 21f * unit)
                lineTo(8f * unit, 18.5f * unit)
                close()
            }
            drawPath(glass, ink.copy(alpha = 0.10f))
            drawPath(glass, ink, style = Stroke(width = 1.4f * unit))
            line(8f, 5.2f, 16f, 5.2f)
            val layerColors = listOf(
                accent,
                secondaryAccent,
                accent.copy(alpha = 0.45f),
            )
            layerColors.forEachIndexed { index, color ->
                drawRoundRect(
                    color = color,
                    topLeft = p(9.2f, 17.4f - index * 3.9f),
                    size = Size(5.6f * unit, 3.8f * unit),
                    cornerRadius = CornerRadius(0.7f * unit),
                )
            }
            line(10f, 7.2f, 10f, 16.2f)
        }

        GameId.EATCAT -> {
            val leftEar = Path().apply {
                moveTo(5.5f * unit, 9f * unit)
                lineTo(7.5f * unit, 3f * unit)
                lineTo(11f * unit, 7f * unit)
                close()
            }
            val rightEar = Path().apply {
                moveTo(13f * unit, 7f * unit)
                lineTo(16.5f * unit, 3f * unit)
                lineTo(18.5f * unit, 9f * unit)
                close()
            }
            drawPath(leftEar, color = ink)
            drawPath(rightEar, color = ink)
            drawCircle(
                color = ink,
                radius = 7f * unit,
                center = p(12f, 13.5f),
            )
            drawCircle(
                color = container,
                radius = 1f * unit,
                center = p(9.2f, 12.5f),
            )
            drawCircle(
                color = container,
                radius = 1f * unit,
                center = p(14.8f, 12.5f),
            )
            block(11.2f, 15f, 1.6f, 1.4f, 0.7f)
            line(3f, 14f, 6.5f, 15f)
            line(3f, 17f, 6.5f, 16f)
            line(21f, 14f, 17.5f, 15f)
            line(21f, 17f, 17.5f, 16f)
        }

        GameId.DINO -> {
            if (dinoSprite != null) {
                val dstWidth = size.width * 0.66f
                val dstHeight = dstWidth * 47f / 44f
                drawImage(
                    image = dinoSprite,
                    srcOffset = IntOffset(765, 2),
                    srcSize = IntSize(44, 47),
                    dstOffset = IntOffset(
                        ((size.width - dstWidth) / 2f).roundToInt(),
                        ((size.height - dstHeight) / 2f).roundToInt(),
                    ),
                    dstSize = IntSize(dstWidth.roundToInt(), dstHeight.roundToInt()),
                    colorFilter = ColorFilter.tint(ink, BlendMode.SrcIn),
                    filterQuality = FilterQuality.None,
                )
            }
        }

        GameId.REACTION -> {
            val bolt = Path().apply {
                moveTo(13f * unit, 3f * unit)
                lineTo(6.5f * unit, 13f * unit)
                lineTo(11.2f * unit, 13f * unit)
                lineTo(9.5f * unit, 21f * unit)
                lineTo(17.5f * unit, 10.5f * unit)
                lineTo(12.6f * unit, 10.5f * unit)
                close()
            }
            drawPath(path = bolt, color = ink)
        }
    }
}
