package com.playbox.app.games.fifteenpuzzle

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.abs
import kotlin.math.min

private val FifteenHelpLines = listOf(
    "点击空位同行或同列的数字滑块，中间滑块会一起滑动。",
    "也可以沿滑块移动方向滑动棋盘。",
    "每次开局都会随机打乱，并保证拼图有解。",
    "把 1–15 按顺序排好即完成；步数越少、用时越短，得分越高。",
)

@Composable
fun FifteenPuzzleRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: FifteenPuzzleViewModel = viewModel(
        factory = FifteenPuzzleViewModel.factory(container.gameStatsRepository),
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
        gameName = "数字华容道",
        score = uiState.score,
        bestScore = bestScore,
        canPause = uiState.hasStarted && !uiState.isGameOver,
        isPaused = uiState.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = listOf(
            GameStatusItem(label = "步数", value = uiState.moves.toString()),
            GameStatusItem(label = "时间", value = "${uiState.elapsedSeconds}s"),
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
                val boardSize = minOf(maxWidth * 0.92f, maxHeight * 0.62f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "点击同行/列 · 或滑动棋盘",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    GameBoardFrame(
                        modifier = Modifier.size(boardSize),
                        contentPadding = 0.dp,
                        clipContent = true,
                    ) {
                        FifteenPuzzleBoard(
                            cells = uiState.cells,
                            enabled = uiState.hasStarted && !uiState.isPaused && !uiState.isGameOver,
                            onMove = viewModel::move,
                            onSwipe = viewModel::swipe,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (!uiState.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "数字华容道",
                description = "点击同行或同列的滑块，或滑动棋盘，把 1–15 按顺序排好。",
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
        GameHelpSheet("数字华容道", FifteenHelpLines, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(onConfirm = onExit, onDismiss = { showExitConfirmation = false })
    }
    if (uiState.showResult) {
        GameResultDialog(
            gameName = "数字华容道",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
        )
    }
}

@Composable
private fun FifteenPuzzleBoard(
    cells: List<Int>,
    enabled: Boolean,
    onMove: (Int) -> Unit,
    onSwipe: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = modifier) {
        val cell = maxWidth / 4
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    val boardWidth = size.width.toFloat()
                    val boardHeight = size.height.toFloat()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        var total = Offset.Zero
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - change.previousPosition
                            total += delta
                            if (!dragging && total.getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging) change.consume()
                        }
                        if (!enabled) return@awaitEachGesture
                        if (dragging) {
                            val threshold = min(boardWidth, boardHeight) * 0.08f
                            val dx = total.x
                            val dy = total.y
                            when {
                                abs(dx) > abs(dy) && abs(dx) > threshold ->
                                    onSwipe(if (dx > 0f) 1 else -1, 0)
                                abs(dy) > threshold ->
                                    onSwipe(0, if (dy > 0f) 1 else -1)
                            }
                        } else {
                            val column = (start.x / (boardWidth / 4f)).toInt().coerceIn(0, 3)
                            val row = (start.y / (boardHeight / 4f)).toInt().coerceIn(0, 3)
                            onMove(row * 4 + column)
                        }
                    }
                },
        ) {
            cells.forEachIndexed { index, value ->
                if (value == 0) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = cell * (index % 4).toFloat(),
                                y = cell * (index / 4).toFloat(),
                            )
                            .size(cell)
                            .padding(cell * 0.03f)
                            .clip(RoundedCornerShape(cell * 0.12f))
                            .background(scheme.surfaceContainerLowest.copy(alpha = 0.55f)),
                    )
                } else {
                    key(value) {
                        val x by animateFloatAsState(
                            targetValue = (index % 4).toFloat(),
                            animationSpec = tween(140, easing = FastOutSlowInEasing),
                            label = "fifteen-x-$value",
                        )
                        val y by animateFloatAsState(
                            targetValue = (index / 4).toFloat(),
                            animationSpec = tween(140, easing = FastOutSlowInEasing),
                            label = "fifteen-y-$value",
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = cell * x, y = cell * y)
                                .size(cell)
                                .padding(cell * 0.03f)
                                .clip(RoundedCornerShape(cell * 0.12f))
                                .background(scheme.surfaceContainerHigh)
                                .border(
                                    width = 1.dp,
                                    color = scheme.outlineVariant,
                                    shape = RoundedCornerShape(cell * 0.12f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                                fontSize = (cell.value * 0.21f).sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
