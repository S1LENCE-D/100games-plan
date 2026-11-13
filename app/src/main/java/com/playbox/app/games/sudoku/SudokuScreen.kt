package com.playbox.app.games.sudoku

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.playbox.app.ui.components.game.GameSaveChoiceDialog
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameSegmentedOptions
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.min

private val SudokuHelpLines = listOf(
    "点击方格选中，再点击下方数字填入。",
    "每行、每列、每个 3×3 宫都必须包含 1–9，且不能重复。",
    "打开“笔记”后点击数字，可标记候选数；再次点击可取消。",
    "填入错误数字会保留并计入错误次数；全部填对即可完成。",
)

@Composable
fun SudokuRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: SudokuViewModel = viewModel(
        factory = SudokuViewModel.factory(container.gameStatsRepository, container.gameSaveRepository),
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
        gameName = "数独",
        score = uiState.score,
        bestScore = bestScore,
        canPause = uiState.hasStarted && !uiState.isGameOver,
        isPaused = uiState.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = listOf(
            GameStatusItem(label = "时间", value = "${uiState.elapsedSeconds}s"),
            GameStatusItem(label = "错误", value = uiState.mistakes.toString()),
        ),
        onSaveRequest = if (uiState.hasStarted && !uiState.isGameOver) viewModel::saveGame else null,
        onHelpRequest = {
            if (uiState.hasStarted && !uiState.isGameOver && !uiState.isPaused) {
                viewModel.togglePause()
            }
            showHelp = true
        },
    ) {
        GameContentContainer {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val boardSize = minOf(maxWidth, maxHeight * 0.58f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${uiState.difficulty.label} · 已给 ${uiState.cells.count { it.given }} 格",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    GameBoardFrame(
                        modifier = Modifier.size(boardSize),
                        contentPadding = 0.dp,
                        clipContent = true,
                    ) {
                        SudokuBoard(
                            state = uiState,
                            onSelect = viewModel::selectCell,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SudokuNumberPad(
                        enabled = uiState.hasStarted && !uiState.isPaused && !uiState.isGameOver,
                        notesMode = uiState.notesMode,
                        onNumber = viewModel::enterNumber,
                        onErase = viewModel::eraseCell,
                        onToggleNotes = viewModel::toggleNotes,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "点击选格 · 再用数字键填写",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!uiState.hasStarted && !uiState.showSaveChoice) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "数独",
                description = "每行、每列、每个 3×3 宫填入 1–9 且不重复。可选简单、中等、困难三档。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            ) {
                GameSegmentedOptions(
                    options = SudokuDifficulty.entries,
                    selected = uiState.difficulty,
                    label = { difficulty -> difficulty.label },
                    onSelect = viewModel::selectDifficulty,
                )
            }
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
        GameHelpSheet("数独", SudokuHelpLines, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            autoSave = true,
            onConfirm = {
                showExitConfirmation = false
                viewModel.saveAndExit(onExit)
            },
            onDismiss = { showExitConfirmation = false },
        )
    }
    if (uiState.showSaveChoice) {
        GameSaveChoiceDialog(
            gameName = "数独",
            onResume = viewModel::resumeSavedGame,
            onNewGame = viewModel::startNewGameFromSave,
            onExit = onExit,
        )
    }
    if (uiState.showResult) {
        GameResultDialog(
            gameName = "数独",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
        )
    }
}

@Composable
private fun SudokuBoard(
    state: SudokuUiState,
    onSelect: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier = modifier.pointerInput(state.hasStarted, state.isGameOver) {
            detectTapGestures { offset ->
                val cell = size.width / 9f
                val column = (offset.x / cell).toInt()
                val row = (offset.y / cell).toInt()
                if (row in 0..8 && column in 0..8) onSelect(row, column)
            }
        },
    ) {
        val cell = size.width / 9f
        for (row in 0..8) {
            for (column in 0..8) {
                val index = row * 9 + column
                val value = state.cells.getOrNull(index)?.value ?: 0
                val given = state.cells.getOrNull(index)?.given == true
                val notes = state.cells.getOrNull(index)?.notes.orEmpty()
                val selected = state.selectedRow == row && state.selectedColumn == column
                val wrong = value != 0 && !given &&
                    value != state.difficulty.solution[index].digitToInt()
                val background = when {
                    selected -> scheme.primaryContainer.copy(alpha = 0.55f)
                    given -> scheme.surfaceContainerHigh
                    else -> scheme.surfaceContainerLow
                }
                drawRect(
                    color = background,
                    topLeft = Offset(column * cell, row * cell),
                    size = Size(cell, cell),
                )
                if (value != 0) {
                    val layout = textMeasurer.measure(
                        text = value.toString(),
                        style = TextStyle(
                            color = when {
                                wrong -> scheme.error
                                given -> scheme.onSurface
                                else -> scheme.primary
                            },
                            fontSize = (cell * 0.5f / 2.75f).sp,
                            fontWeight = if (given) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            column * cell + (cell - layout.size.width) / 2f,
                            row * cell + (cell - layout.size.height) / 2f,
                        ),
                    )
                } else if (notes.isNotEmpty()) {
                    notes.forEach { note ->
                        val noteColumn = (note - 1) % 3
                        val noteRow = (note - 1) / 3
                        val layout = textMeasurer.measure(
                            text = note.toString(),
                            style = TextStyle(
                                color = scheme.onSurfaceVariant,
                                fontSize = (cell * 0.19f / 2.75f).sp,
                            ),
                        )
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                column * cell + noteColumn * cell / 3f + 1f,
                                row * cell + noteRow * cell / 3f,
                            ),
                        )
                    }
                }
            }
        }
        for (index in 0..9) {
            val lineWidth = if (index % 3 == 0) 2.5f else 1f
            drawLine(
                color = if (index % 3 == 0) scheme.outline else scheme.outlineVariant,
                start = Offset(index * cell, 0f),
                end = Offset(index * cell, size.height),
                strokeWidth = lineWidth,
            )
            drawLine(
                color = if (index % 3 == 0) scheme.outline else scheme.outlineVariant,
                start = Offset(0f, index * cell),
                end = Offset(size.width, index * cell),
                strokeWidth = lineWidth,
            )
        }
    }
}

@Composable
private fun SudokuNumberPad(
    enabled: Boolean,
    notesMode: Boolean,
    onNumber: (Int) -> Unit,
    onErase: () -> Unit,
    onToggleNotes: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (1..5).forEach { number ->
                FilledTonalButton(
                    onClick = { onNumber(number) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(text = number.toString())
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (6..9).forEach { number ->
                FilledTonalButton(
                    onClick = { onNumber(number) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(text = number.toString())
                }
            }
            OutlinedButton(
                onClick = onErase,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(text = "擦")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            FilterChip(
                selected = notesMode,
                onClick = onToggleNotes,
                enabled = enabled,
                label = { Text(text = "笔记") },
            )
        }
    }
}
