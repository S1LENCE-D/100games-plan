package com.playbox.app.games.gomoku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameBoardFrame
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GameReadyOverlay
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameSegmentedOptions
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private val GomokuHelp = listOf(
    "15×15 棋盘，黑棋先手；横、竖、斜任意方向先连成五子或更多者获胜。",
    "点击交叉点落子；棋子出现在最近的交叉点。",
    "人机模式可以选择执黑先手或执白后手，AI 会自动应手。",
    "悔棋在人机模式会同时撤回 AI 的上一手，双人同屏只撤回一手。",
)

@Composable
fun GomokuRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: GomokuViewModel = viewModel(
        factory = GomokuViewModel.factory(container.gameStatsRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    val statusText = when {
        !state.hasStarted -> "准备开始"
        state.isGameOver -> "对局结束"
        state.mode == GomokuMode.AI && state.isAiThinking -> "AI 思考中…"
        state.mode == GomokuMode.AI && state.currentPlayer == state.humanColor -> "轮到你落子"
        state.mode == GomokuMode.AI -> "AI 回合"
        state.currentPlayer == GOMOKU_BLACK -> "黑棋回合"
        else -> "白棋回合"
    }

    GameScaffold(
        gameName = "五子棋",
        score = state.score,
        bestScore = bestScore,
        canPause = false,
        isPaused = false,
        onBackRequest = {
            if (state.hasStarted && !state.showResult) {
                showExitConfirmation = true
            } else {
                onExit()
            }
        },
        onTogglePause = {},
        extraStats = listOf(
            GameStatusItem("状态", statusText),
            GameStatusItem("手数", state.moveCount.toString()),
        ),
        onHelpRequest = { showHelp = true },
    ) {
        GameContentContainer {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val boardSize = minOf(
                    maxWidth,
                    760.dp,
                    (maxHeight - 140.dp).coerceAtLeast(280.dp),
                )
                Column(
                    modifier = Modifier.width(boardSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = when {
                            state.mode == GomokuMode.AI -> "人机对战 · 你执${if (state.humanColor == GOMOKU_BLACK) "黑" else "白"}"
                            else -> "双人同屏 · 黑棋先手"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    GameBoardFrame(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(boardSize),
                        contentPadding = 0.dp,
                        clipContent = true,
                    ) {
                        GomokuBoard(
                            state = state,
                            onCellTap = viewModel::tapCell,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::undo,
                            enabled = state.moveCount > 0 &&
                                !state.isAiThinking &&
                                !state.isGameOver &&
                                !state.showResult,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("悔棋")
                        }
                        FilledTonalButton(
                            onClick = viewModel::restart,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("重新开局")
                        }
                    }
                }
            }
        }

        if (!state.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "五子棋",
                description = "15×15 棋盘，黑棋先手。挑战 AI，或与身边的人双人同屏。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            ) {
                GameSegmentedOptions(
                    options = GomokuMode.entries,
                    selected = state.mode,
                    label = {
                        when (it) {
                            GomokuMode.AI -> "人机对战"
                            GomokuMode.LOCAL -> "双人同屏"
                        }
                    },
                    onSelect = viewModel::selectMode,
                )
                if (state.mode == GomokuMode.AI) {
                    GameSegmentedOptions(
                        options = listOf(GOMOKU_BLACK, GOMOKU_WHITE),
                        selected = state.humanColor,
                        label = { if (it == GOMOKU_BLACK) "执黑先手" else "执白后手" },
                        onSelect = viewModel::selectHumanColor,
                    )
                }
            }
        }
    }

    if (showHelp) {
        GameHelpSheet("五子棋", GomokuHelp, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            onConfirm = onExit,
            onDismiss = { showExitConfirmation = false },
        )
    }
    if (state.showResult) {
        val title = when {
            state.isDraw -> "平局"
            state.mode == GomokuMode.AI && state.winner == state.humanColor -> "你赢了"
            state.mode == GomokuMode.AI -> "AI 获胜"
            state.winner == GOMOKU_BLACK -> "黑棋获胜"
            else -> "白棋获胜"
        }
        GameResultDialog(
            gameName = "五子棋",
            score = state.score,
            bestScore = bestScore,
            isNewRecord = state.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
            details = listOf(
                title,
                "共 ${state.moveCount} 手",
                if (state.mode == GomokuMode.AI) "人机对战 · 你执${if (state.humanColor == GOMOKU_BLACK) "黑" else "白"}" else "双人同屏",
            ),
        )
    }
}

@Composable
private fun GomokuBoard(
    state: GomokuUiState,
    onCellTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val lineColor = scheme.outline.copy(alpha = 0.62f)
    val blackStone = Color(0xFF0A0D12)
    val whiteStone = Color(0xFFF2EEE4)
    val boardColor = scheme.surfaceContainerLow
    val winningColor = scheme.tertiary
    Canvas(
        modifier = modifier.pointerInput(state.isGameOver, state.isAiThinking, state.mode) {
            detectTapGestures { offset ->
                val margin = size.width * 0.055f
                val cell = (size.width - margin * 2f) / (GOMOKU_BOARD_SIZE - 1)
                val column = ((offset.x - margin) / cell).roundToInt()
                val row = ((offset.y - margin) / cell).roundToInt()
                if (row !in 0 until GOMOKU_BOARD_SIZE || column !in 0 until GOMOKU_BOARD_SIZE) {
                    return@detectTapGestures
                }
                val center = Offset(margin + column * cell, margin + row * cell)
                if (hypot(offset.x - center.x, offset.y - center.y) <= cell * 0.58f) {
                    onCellTap(row, column)
                }
            }
        },
    ) {
        drawRect(boardColor)
        val margin = size.width * 0.055f
        val cell = (size.width - margin * 2f) / (GOMOKU_BOARD_SIZE - 1)
        for (index in 0 until GOMOKU_BOARD_SIZE) {
            val position = margin + index * cell
            drawLine(
                color = lineColor,
                start = Offset(margin, position),
                end = Offset(size.width - margin, position),
                strokeWidth = 1.5f,
            )
            drawLine(
                color = lineColor,
                start = Offset(position, margin),
                end = Offset(position, size.height - margin),
                strokeWidth = 1.5f,
            )
        }
        listOf(3 to 3, 3 to 11, 7 to 7, 11 to 3, 11 to 11).forEach { (row, column) ->
            drawCircle(
                color = lineColor,
                radius = cell * 0.11f,
                center = Offset(margin + column * cell, margin + row * cell),
            )
        }

        for (row in 0 until GOMOKU_BOARD_SIZE) {
            for (column in 0 until GOMOKU_BOARD_SIZE) {
                val player = state.board[row * GOMOKU_BOARD_SIZE + column]
                if (player == 0) continue
                val center = Offset(margin + column * cell, margin + row * cell)
                val stoneRadius = cell * 0.44f
                drawCircle(
                    color = Color.Black.copy(alpha = 0.28f),
                    radius = stoneRadius,
                    center = Offset(center.x + cell * 0.04f, center.y + cell * 0.06f),
                )
                drawCircle(
                    color = if (player == GOMOKU_BLACK) blackStone else whiteStone,
                    radius = stoneRadius,
                    center = center,
                )
                drawCircle(
                    color = if (player == GOMOKU_BLACK) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.2f),
                    radius = stoneRadius,
                    center = center,
                    style = Stroke(width = 1f),
                )
            }
        }

        state.lastMove?.let { last ->
            drawCircle(
                color = if (last.player == GOMOKU_BLACK) Color.White else Color.Black,
                radius = cell * 0.1f,
                center = Offset(margin + last.column * cell, margin + last.row * cell),
            )
        }

        if (state.winningLine.size >= 2) {
            val first = state.winningLine.first()
            val last = state.winningLine.last()
            drawLine(
                color = winningColor,
                start = Offset(margin + first.column * cell, margin + first.row * cell),
                end = Offset(margin + last.column * cell, margin + last.row * cell),
                strokeWidth = cell * 0.1f,
            )
        }
    }
}
