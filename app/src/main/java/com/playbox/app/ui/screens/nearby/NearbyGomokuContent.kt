package com.playbox.app.ui.screens.nearby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.playbox.app.games.gomoku.GOMOKU_BLACK
import com.playbox.app.games.gomoku.GOMOKU_BOARD_SIZE
import com.playbox.app.games.gomoku.GOMOKU_WHITE
import com.playbox.app.games.nearby.NearbyUiState
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun NearbyGomokuContent(
    state: NearbyUiState,
    onCell: (Int) -> Unit,
    onNext: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val game = state.gomoku
    val localMark = if (state.isHost) GOMOKU_BLACK else GOMOKU_WHITE
    val localTurn = !game.isGameOver && game.currentPlayer == localMark
    val markLabel = if (localMark == GOMOKU_BLACK) "黑棋先手" else "白棋后手"

    InfoCard(
        title = "联机五子棋",
        message = "你执${if (localMark == GOMOKU_BLACK) "黑" else "白"}·$markLabel，点击交叉点落子。",
    )
    Spacer(modifier = Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        GomokuOnlineBoard(
            state = state,
            enabled = localTurn,
            onCell = onCell,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = when {
            game.isDraw -> "平局"
            game.winner == localMark -> "你赢了"
            game.winner != 0 -> "对方赢了"
            localTurn -> "轮到你落子"
            else -> "等待对方落子"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (game.isGameOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (game.isGameOver) {
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("再来一局")
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text("断开连接")
    }
}

@Composable
private fun GomokuOnlineBoard(
    state: NearbyUiState,
    enabled: Boolean,
    onCell: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = state.gomoku
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    val blackStone = Color(0xFF0A0D12)
    val whiteStone = Color(0xFFF2EEE4)
    val accent = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier = modifier.pointerInput(enabled, game.board) {
            detectTapGestures { offset ->
                if (!enabled) return@detectTapGestures
                val margin = size.width * 0.06f
                val cell = (size.width - margin * 2f) / (GOMOKU_BOARD_SIZE - 1)
                val column = ((offset.x - margin) / cell).roundToInt()
                val row = ((offset.y - margin) / cell).roundToInt()
                if (row !in 0 until GOMOKU_BOARD_SIZE || column !in 0 until GOMOKU_BOARD_SIZE) return@detectTapGestures
                val center = Offset(margin + column * cell, margin + row * cell)
                if (hypot(offset.x - center.x, offset.y - center.y) <= cell * 0.7f) {
                    onCell(row * GOMOKU_BOARD_SIZE + column)
                }
            }
        },
    ) {
        val margin = size.width * 0.06f
        val cell = (size.width - margin * 2f) / (GOMOKU_BOARD_SIZE - 1)
        for (index in 0 until GOMOKU_BOARD_SIZE) {
            val position = margin + index * cell
            drawLine(lineColor, Offset(margin, position), Offset(size.width - margin, position), 1.2f)
            drawLine(lineColor, Offset(position, margin), Offset(position, size.width - margin), 1.2f)
        }
        listOf(3 to 3, 3 to 11, 7 to 7, 11 to 3, 11 to 11).forEach { (row, column) ->
            drawCircle(
                color = lineColor,
                radius = cell * 0.1f,
                center = Offset(margin + column * cell, margin + row * cell),
            )
        }
        for (row in 0 until GOMOKU_BOARD_SIZE) {
            for (column in 0 until GOMOKU_BOARD_SIZE) {
                val mark = game.board.getOrElse(row * GOMOKU_BOARD_SIZE + column) { 0 }
                if (mark == 0) continue
                val center = Offset(margin + column * cell, margin + row * cell)
                val radius = cell * 0.42f
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = radius,
                    center = Offset(center.x + 1.5f, center.y + 2f),
                )
                drawCircle(
                    color = if (mark == GOMOKU_BLACK) blackStone else whiteStone,
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = if (mark == GOMOKU_BLACK) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.2f),
                    radius = radius,
                    center = center,
                    style = Stroke(1f),
                )
            }
        }
        game.lastMove?.let { move ->
            drawCircle(
                color = accent,
                radius = cell * 0.1f,
                center = Offset(margin + move.column * cell, margin + move.row * cell),
            )
        }
        if (game.winningLine.size >= 2) {
            val first = game.winningLine.first()
            val last = game.winningLine.last()
            drawLine(
                color = accent,
                start = Offset(margin + first.column * cell, margin + first.row * cell),
                end = Offset(margin + last.column * cell, margin + last.row * cell),
                strokeWidth = cell * 0.1f,
            )
        }
    }
}
