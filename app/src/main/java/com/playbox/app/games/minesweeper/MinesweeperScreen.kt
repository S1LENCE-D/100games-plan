package com.playbox.app.games.minesweeper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.playbox.app.ui.components.game.GameSegmentedOptions
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.roundToInt

private val MinesweeperHelpLines = listOf(
    "点击方格翻开；数字表示周围八格中的地雷数量。",
    "长按方格插旗标记地雷。",
    "第一次翻开的方格及其周围八格保证安全。",
    "翻开所有非雷方格即可获胜；时间越短、难度越高，得分越高。",
)

@Composable
fun MinesweeperRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: MinesweeperViewModel = viewModel(
        factory = MinesweeperViewModel.factory(container.gameStatsRepository),
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
        gameName = "扫雷",
        score = uiState.score,
        bestScore = bestScore,
        canPause = uiState.hasStarted && !uiState.isGameOver,
        isPaused = uiState.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = listOf(
            GameStatusItem(label = "时间", value = "${uiState.elapsedSeconds}s"),
            GameStatusItem(label = "剩余雷", value = uiState.minesRemaining.toString()),
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
                val cell = minOf(
                    maxWidth / uiState.difficulty.columns.toFloat(),
                    maxHeight * 0.80f / uiState.difficulty.rows.toFloat(),
                )
                val boardWidth = cell * uiState.difficulty.columns.toFloat()
                val boardHeight = cell * uiState.difficulty.rows.toFloat()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${uiState.difficulty.label} · ${uiState.difficulty.rows}×${uiState.difficulty.columns}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    GameBoardFrame(
                        modifier = Modifier.size(boardWidth, boardHeight),
                        contentPadding = 0.dp,
                        clipContent = true,
                    ) {
                        MinesweeperBoard(
                            state = uiState,
                            onReveal = viewModel::reveal,
                            onFlag = viewModel::toggleFlag,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "点击翻开 · 长按插旗",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (!uiState.hasStarted) {
        GameReadyOverlay(
            showTutorial = tutorialSeen == false,
            title = "扫雷",
            description = "点击方格翻开，长按插旗；第一次翻格及周围八格保证安全。",
            onStart = {
                viewModel.markTutorialSeen()
                viewModel.startGame()
            },
            onDismissRequest = onExit,
        ) {
            GameSegmentedOptions(
                options = MinesweeperDifficulty.entries,
                selected = uiState.difficulty,
                label = { difficulty -> difficulty.label },
                onSelect = viewModel::selectDifficulty,
            )
        }
    }

    if (showHelp) {
        GameHelpSheet("扫雷", MinesweeperHelpLines, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            onConfirm = onExit,
            onDismiss = { showExitConfirmation = false },
        )
    }
    if (uiState.showResult) {
        GameResultDialog(
            gameName = "扫雷",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
        )
    }
}

@Composable
private fun MinesweeperBoard(
    state: MinesweeperUiState,
    onReveal: (Int, Int) -> Unit,
    onFlag: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val numberColors = listOf(
        scheme.primary,
        scheme.tertiary,
        scheme.secondary,
        scheme.error,
        scheme.onSurfaceVariant,
        scheme.primary,
        scheme.tertiary,
        scheme.secondary,
    )

    Canvas(
        modifier = modifier.pointerInput(state.difficulty, state.isPaused, state.isGameOver) {
            detectTapGestures(
                onTap = { offset ->
                    val column = (offset.x / (size.width / state.difficulty.columns.toFloat())).toInt()
                    val row = (offset.y / (size.height / state.difficulty.rows.toFloat())).toInt()
                    if (row in 0 until state.difficulty.rows && column in 0 until state.difficulty.columns) {
                        onReveal(row, column)
                    }
                },
                onLongPress = { offset ->
                    val column = (offset.x / (size.width / state.difficulty.columns.toFloat())).toInt()
                    val row = (offset.y / (size.height / state.difficulty.rows.toFloat())).toInt()
                    if (row in 0 until state.difficulty.rows && column in 0 until state.difficulty.columns) {
                        onFlag(row, column)
                    }
                },
            )
        },
    ) {
        val cellWidth = size.width / state.difficulty.columns
        val cellHeight = size.height / state.difficulty.rows
        for (cell in state.cells) {
            val left = cell.column * cellWidth
            val top = cell.row * cellHeight
            if (!cell.revealed) {
                drawRect(
                    color = scheme.surfaceContainerHighest,
                    topLeft = Offset(left, top),
                    size = Size(cellWidth, cellHeight),
                )
                if (cell.flagged) {
                    val flagColor = scheme.error
                    val cx = left + cellWidth * 0.54f
                    val cy = top + cellHeight * 0.52f
                    drawLine(
                        color = scheme.onSurface,
                        start = Offset(cx, top + cellHeight * 0.22f),
                        end = Offset(cx, top + cellHeight * 0.78f),
                        strokeWidth = cellWidth * 0.08f,
                    )
                    val path = Path().apply {
                        moveTo(cx, top + cellHeight * 0.24f)
                        lineTo(left + cellWidth * 0.3f, top + cellHeight * 0.4f)
                        lineTo(cx, top + cellHeight * 0.54f)
                        close()
                    }
                    drawPath(path, flagColor)
                }
                drawRect(
                    color = scheme.outlineVariant,
                    topLeft = Offset(left, top),
                    size = Size(cellWidth, cellHeight),
                    style = Stroke(width = 1f),
                )
            } else {
                drawRect(
                    color = if (cell.isMine) scheme.errorContainer else scheme.surfaceContainerLow,
                    topLeft = Offset(left, top),
                    size = Size(cellWidth, cellHeight),
                )
                when {
                    cell.isMine -> {
                        drawCircle(
                            color = scheme.onErrorContainer,
                            radius = cellWidth * 0.2f,
                            center = Offset(left + cellWidth / 2f, top + cellHeight * 0.58f),
                        )
                        drawLine(
                            color = scheme.onErrorContainer,
                            start = Offset(left + cellWidth * 0.5f, top + cellHeight * 0.22f),
                            end = Offset(left + cellWidth * 0.5f, top + cellHeight * 0.42f),
                            strokeWidth = cellWidth * 0.07f,
                        )
                    }
                    cell.adjacentMines > 0 -> {
                        val text = cell.adjacentMines.toString()
                        val layout = textMeasurer.measure(
                            text = text,
                            style = TextStyle(
                                color = numberColors[(cell.adjacentMines - 1).coerceIn(0, 7)],
                                fontSize = (cellWidth * 0.55f / 2.75f).sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                left + (cellWidth - layout.size.width) / 2f,
                                top + (cellHeight - layout.size.height) / 2f,
                            ),
                        )
                    }
                }
            }
        }
    }
}
