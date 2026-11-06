package com.playbox.app.games.breakout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameBackgroundPauseEffect
import com.playbox.app.ui.components.game.GameBoardFrame
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GamePauseOverlay
import com.playbox.app.ui.components.game.GameReadyOverlay
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.min

private val BreakoutHelpLines = listOf(
    "在棋盘下方拖动挡板，让小球反弹击碎砖块。",
    "砖块越靠上分值越高；清空全部砖块即可通关。",
    "漏接小球会失去一条生命，三条生命耗尽时本局结束。",
)

@Composable
fun BreakoutRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: BreakoutViewModel = viewModel(
        factory = BreakoutViewModel.factory(container.gameStatsRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    GameBackgroundPauseEffect(onBackgroundPause = viewModel::onLifecyclePause)

    val requestBack: () -> Unit = {
        if (uiState.hasStarted && !uiState.isGameOver) {
            if (!uiState.isPaused) viewModel.togglePause()
            showExitConfirmation = true
        } else {
            onExit()
        }
    }

    GameScaffold(
        gameName = "打砖块",
        score = uiState.score,
        bestScore = bestScore,
        canPause = uiState.hasStarted && !uiState.isGameOver,
        isPaused = uiState.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = listOf(
            GameStatusItem(label = "生命", value = uiState.lives.toString()),
            GameStatusItem(label = "砖块", value = uiState.bricks.count { it.alive }.toString()),
        ),
        onHelpRequest = {
            if (uiState.hasStarted && !uiState.isGameOver && !uiState.isPaused) {
                viewModel.togglePause()
            }
            showHelp = true
        },
    ) {
        GameContentContainer {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val boardWidth = minOf(
                    maxWidth,
                    maxHeight * BREAKOUT_WORLD_WIDTH / BREAKOUT_WORLD_HEIGHT * 0.9f,
                )
                val boardHeight = boardWidth * BREAKOUT_WORLD_HEIGHT / BREAKOUT_WORLD_WIDTH
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text(
                        text = "拖动挡板 · 接住小球",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GameBoardFrame(modifier = Modifier.size(boardWidth, boardHeight)) {
                        BreakoutBoard(
                            state = uiState,
                            onPaddleMove = viewModel::movePaddle,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (!uiState.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "打砖块",
                description = "拖动挡板反弹小球，清空全部砖块。三条生命，漏接小球会失去一条。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            )
        }

        GamePauseOverlay(
            visible = uiState.hasStarted && uiState.isPaused && !uiState.isGameOver && !showHelp && !showExitConfirmation,
            onResume = viewModel::togglePause,
            onRestart = viewModel::restart,
            onExitRequest = { showExitConfirmation = true },
            onHelpRequest = { showHelp = true },
        )
    }

    if (showHelp) {
        GameHelpSheet("打砖块", BreakoutHelpLines, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            onConfirm = onExit,
            onDismiss = { showExitConfirmation = false },
        )
    }
    if (uiState.showResult) {
        GameResultDialog(
            gameName = "打砖块",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
        )
    }
}

@Composable
private fun BreakoutBoard(
    state: BreakoutUiState,
    onPaddleMove: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    onPaddleMove(offset.x / size.width)
                },
                onDrag = { change, _ ->
                    change.consume()
                    onPaddleMove(change.position.x / size.width)
                },
            )
        },
    ) {
        val sx = size.width / BREAKOUT_WORLD_WIDTH
        val sy = size.height / BREAKOUT_WORLD_HEIGHT
        fun x(value: Float) = value * sx
        fun y(value: Float) = value * sy

        val brickColors = listOf(
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.primaryContainer,
            scheme.secondaryContainer,
            scheme.tertiaryContainer,
        )
        val brickWidth = (BREAKOUT_WORLD_WIDTH - 16f - 4f * (BREAKOUT_BRICK_COLUMNS - 1)) /
            BREAKOUT_BRICK_COLUMNS
        state.bricks.filter { it.alive }.forEach { brick ->
            val left = 8f + brick.column * (brickWidth + 4f)
            val top = 64f + brick.row * (18f + 4f)
            drawRoundRect(
                color = brickColors[brick.row % brickColors.size],
                topLeft = Offset(x(left), y(top)),
                size = Size(x(brickWidth), y(18f)),
                cornerRadius = CornerRadius(4f * sx, 4f * sy),
            )
        }

        val paddleLeft = state.paddleCenter - BREAKOUT_PADDLE_WIDTH / 2f
        drawRoundRect(
            color = scheme.primary,
            topLeft = Offset(x(paddleLeft), y(BREAKOUT_PADDLE_Y)),
            size = Size(x(BREAKOUT_PADDLE_WIDTH), y(BREAKOUT_PADDLE_HEIGHT)),
            cornerRadius = CornerRadius(5f * sx, 5f * sy),
        )
        val ballRadius = y(BREAKOUT_BALL_RADIUS)
        drawCircle(
            color = scheme.onSurface.copy(alpha = 0.10f),
            radius = ballRadius * 1.6f,
            center = Offset(x(state.ballX), y(state.ballY)),
        )
        drawCircle(
            color = scheme.onSurface,
            radius = ballRadius,
            center = Offset(x(state.ballX), y(state.ballY)),
        )
    }
}
