package com.playbox.app.games.tictactoe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.min

private val TicTacToeHelpLines = listOf(
    "你执 X 先手，AI 执 O。",
    "先将三枚棋子连成一线者获胜；棋盘填满即为平局。",
    "可选择随机、简单或完美 AI；完美 AI 使用极小化极大算法。",
)

@Composable
fun TicTacToeRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: TicTacToeViewModel = viewModel(
        factory = TicTacToeViewModel.factory(container.gameStatsRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    val requestBack: () -> Unit = { showExitConfirmation = true }

    GameScaffold(
        gameName = "井字棋",
        score = uiState.score,
        bestScore = bestScore,
        canPause = false,
        isPaused = false,
        onTogglePause = {},
        onBackRequest = requestBack,
        extraStats = listOf(
            GameStatusItem(label = "AI", value = uiState.aiLevel.label),
            GameStatusItem(label = "状态", value = statusLabel(uiState)),
        ),
        onHelpRequest = { showHelp = true },
    ) {
        GameContentContainer {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val boardSize = minOf(maxWidth * 0.9f, maxHeight * 0.62f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            uiState.isDraw -> "平局"
                            uiState.winner == TicTacToeEngine.PLAYER -> "你赢了"
                            uiState.winner == TicTacToeEngine.AI -> "AI 获胜"
                            uiState.isAiThinking -> "AI 正在思考…"
                            else -> "你的回合 · X"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GameBoardFrame(
                        modifier = Modifier.size(boardSize),
                        contentPadding = 0.dp,
                        clipContent = true,
                    ) {
                        TicTacToeBoard(
                            board = uiState.board,
                            enabled = uiState.hasStarted && !uiState.isGameOver && !uiState.isAiThinking,
                            onPlay = viewModel::play,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (!uiState.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "井字棋",
                description = "你执 X 先手，先将三子连成一线获胜。选择 AI 难度后开始。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            ) {
                GameSegmentedOptions(
                    options = TicTacToeAi.entries,
                    selected = uiState.aiLevel,
                    label = { level -> level.label },
                    onSelect = viewModel::selectAiLevel,
                )
            }
        }
    }

    if (showHelp) {
        GameHelpSheet("井字棋", TicTacToeHelpLines, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(onConfirm = onExit, onDismiss = { showExitConfirmation = false })
    }
    if (uiState.showResult) {
        GameResultDialog(
            gameName = "井字棋",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
            scoreLabel = "本局得分",
        )
    }
}

private fun statusLabel(state: TicTacToeUiState): String = when {
    state.isDraw -> "平局"
    state.winner == TicTacToeEngine.PLAYER -> "胜利"
    state.winner == TicTacToeEngine.AI -> "失败"
    state.isAiThinking -> "等待"
    else -> "进行中"
}

@Composable
private fun TicTacToeBoard(
    board: List<Int>,
    enabled: Boolean,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Canvas(
        modifier = modifier.pointerInput(enabled) {
            detectTapGestures { offset ->
                if (!enabled) return@detectTapGestures
                val cell = size.width / 3f
                val column = (offset.x / cell).toInt().coerceIn(0, 2)
                val row = (offset.y / cell).toInt().coerceIn(0, 2)
                onPlay(row * 3 + column)
            }
        },
    ) {
        val cell = size.width / 3f
        for (index in 1..2) {
            drawLine(
                color = scheme.outlineVariant,
                start = Offset(index * cell, 0f),
                end = Offset(index * cell, size.height),
                strokeWidth = 2f,
            )
            drawLine(
                color = scheme.outlineVariant,
                start = Offset(0f, index * cell),
                end = Offset(size.width, index * cell),
                strokeWidth = 2f,
            )
        }
        val stroke = cell * 0.08f
        board.forEachIndexed { index, mark ->
            if (mark == 0) return@forEachIndexed
            val row = index / 3
            val column = index % 3
            val left = column * cell
            val top = row * cell
            val padding = cell * 0.23f
            if (mark == TicTacToeEngine.PLAYER) {
                drawLine(
                    color = scheme.primary,
                    start = Offset(left + padding, top + padding),
                    end = Offset(left + cell - padding, top + cell - padding),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = scheme.primary,
                    start = Offset(left + cell - padding, top + padding),
                    end = Offset(left + padding, top + cell - padding),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            } else {
                drawCircle(
                    color = scheme.tertiary,
                    radius = cell * 0.27f,
                    center = Offset(left + cell / 2f, top + cell / 2f),
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}
