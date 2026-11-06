package com.playbox.app.games.tetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameBackgroundPauseEffect
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GamePauseOverlay
import com.playbox.app.ui.components.game.GameReadyOverlay
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameSegmentedOptions
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.min

private val TetrisHelpLines = listOf(
    "目标：移动和旋转下落的方块，填满一整行即可消除。",
    "控制：← 向左移动，↻ 顺时针旋转，→ 向右移动，软降逐格下落，落底直接落到底部。",
    "每消除 10 行提升一级，方块下落速度会加快。一次消除多行可以获得更多分数。",
    "尽量保持堆叠平整；新方块在出生区域无法放置时游戏结束。",
)

@Composable
fun TetrisRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: TetrisViewModel = viewModel(
        factory = TetrisViewModel.factory(container.gameStatsRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    GameBackgroundPauseEffect(onBackgroundPause = viewModel::onLifecyclePause)

    val canControl = uiState.hasStarted &&
        !uiState.isPaused &&
        !uiState.isGameOver &&
        !uiState.showResult

    val requestBack: () -> Unit = {
        if (uiState.hasStarted && !uiState.isGameOver) {
            if (!uiState.isPaused) {
                viewModel.togglePause()
            }
            showExitConfirmation = true
        } else {
            onExit()
        }
    }

    GameScaffold(
        gameName = "俄罗斯方块",
        score = uiState.score,
        bestScore = bestScore,
        canPause = uiState.hasStarted && !uiState.isGameOver,
        isPaused = uiState.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = listOf(
            GameStatusItem(label = "等级", value = uiState.level.toString()),
            GameStatusItem(label = "消行", value = uiState.clearedLines.toString()),
        ),
        onHelpRequest = {
            if (uiState.hasStarted && !uiState.isGameOver && !uiState.isPaused) {
                viewModel.togglePause()
            }
            showHelp = true
        },
    ) {
        GameContentContainer {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useWideLayout = maxWidth >= 520.dp || maxWidth > maxHeight * 1.3f
                if (useWideLayout) {
                    TetrisWideLayout(
                        uiState = uiState,
                        canControl = canControl,
                        onMoveLeft = viewModel::moveLeft,
                        onMoveRight = viewModel::moveRight,
                        onRotate = viewModel::rotate,
                        onSoftDrop = viewModel::softDrop,
                        onHardDrop = viewModel::hardDrop,
                    )
                } else {
                    TetrisCompactLayout(
                        uiState = uiState,
                        canControl = canControl,
                        onMoveLeft = viewModel::moveLeft,
                        onMoveRight = viewModel::moveRight,
                        onRotate = viewModel::rotate,
                        onSoftDrop = viewModel::softDrop,
                        onHardDrop = viewModel::hardDrop,
                    )
                }
            }
        }

        if (!uiState.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "俄罗斯方块",
                description = "移动并旋转七种方块，填满整行即可消除。开始后可通过顶部的信息按钮随时查看完整教程。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            ) {
                Text(
                    text = "初始速度",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                GameSegmentedOptions(
                    options = TetrisDifficulty.entries,
                    selected = uiState.difficulty,
                    label = { difficulty -> difficulty.label },
                    onSelect = viewModel::selectDifficulty,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "操作：← 左移　↻ 旋转　→ 右移　↓ 软降　落底",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "每消除 10 行提升一级，下落速度随之加快。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 暂停层与退出确认/教程互斥，避免两个 Dialog 叠加遮挡。
        GamePauseOverlay(
            visible = uiState.hasStarted && uiState.isPaused && !uiState.isGameOver && !showHelp && !showExitConfirmation,
            onResume = viewModel::togglePause,
            onRestart = viewModel::restart,
            onExitRequest = { showExitConfirmation = true },
            onHelpRequest = { showHelp = true },
        )
    }

    if (showHelp) {
        GameHelpSheet(
            gameName = "俄罗斯方块",
            helpLines = TetrisHelpLines,
            onDismiss = { showHelp = false },
        )
    }

    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            onConfirm = onExit,
            onDismiss = { showExitConfirmation = false },
        )
    }

    if (uiState.showResult) {
        GameResultDialog(
            gameName = "俄罗斯方块",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
        )
    }
}

@Composable
private fun TetrisCompactLayout(
    uiState: TetrisUiState,
    canControl: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRotate: () -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val sideWidth = 80.dp
            val gap = 12.dp
            val cell = minOf(
                (maxWidth - sideWidth - gap).coerceAtLeast(1.dp) / TETRIS_COLUMNS.toFloat(),
                maxHeight / TETRIS_ROWS.toFloat(),
                22.dp,
            )
            val boardWidth = cell * TETRIS_COLUMNS.toFloat()
            val boardHeight = cell * TETRIS_ROWS.toFloat()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TetrisBoard(
                    board = uiState.board,
                    activeCells = uiState.activeCells,
                    activeType = uiState.activeType,
                    modifier = Modifier.size(width = boardWidth, height = boardHeight),
                )
                Spacer(modifier = Modifier.width(gap))
                Column(
                    modifier = Modifier
                        .width(sideWidth)
                        .height(boardHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "下一个",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NextPiecePreview(type = uiState.nextType, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${uiState.level} 级",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 8.dp
            val buttonSize = ((maxWidth - gap * 4) / 5f).coerceIn(48.dp, 64.dp)
            CompactTetrisControls(
                buttonSize = buttonSize,
                gap = gap,
                canControl = canControl,
                onMoveLeft = onMoveLeft,
                onMoveRight = onMoveRight,
                onRotate = onRotate,
                onSoftDrop = onSoftDrop,
                onHardDrop = onHardDrop,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TetrisWideLayout(
    uiState: TetrisUiState,
    canControl: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRotate: () -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gap = 24.dp
        val sideWidth = (maxWidth * 0.34f).coerceIn(232.dp, 340.dp)
        val veryShort = maxHeight < 300.dp
        val compactSide = maxHeight < 360.dp
        val controlSize = when {
            veryShort -> 48.dp
            compactSide -> 56.dp
            else -> 64.dp
        }
        val previewSize = when {
            veryShort -> 40.dp
            compactSide -> 56.dp
            else -> 84.dp
        }
        val sidePadding = when {
            veryShort -> 8.dp
            compactSide -> 12.dp
            else -> 16.dp
        }
        val cell = minOf(
            maxHeight / TETRIS_ROWS.toFloat(),
            (maxWidth - sideWidth - gap).coerceAtLeast(120.dp) / TETRIS_COLUMNS.toFloat(),
            24.dp,
        )
        val boardWidth = cell * TETRIS_COLUMNS.toFloat()
        val boardHeight = cell * TETRIS_ROWS.toFloat()

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TetrisBoard(
                board = uiState.board,
                activeCells = uiState.activeCells,
                activeType = uiState.activeType,
                modifier = Modifier.size(width = boardWidth, height = boardHeight),
            )
            Spacer(modifier = Modifier.width(gap))
            Surface(
                modifier = Modifier.width(sideWidth),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(sidePadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!compactSide) {
                        Text(
                            text = "下一个方块",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    NextPiecePreview(
                        type = uiState.nextType,
                        modifier = Modifier.size(previewSize),
                    )
                    if (!compactSide) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "等级 ${uiState.level} · 消行 ${uiState.clearedLines}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(if (compactSide) 8.dp else 16.dp))
                    TetrisIconControls(
                        controlSize = controlSize,
                        canControl = canControl,
                        onMoveLeft = onMoveLeft,
                        onRotate = onRotate,
                        onMoveRight = onMoveRight,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onSoftDrop,
                            enabled = canControl,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = controlSize),
                        ) {
                            Text(text = "软降")
                        }
                        FilledTonalButton(
                            onClick = onHardDrop,
                            enabled = canControl,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = controlSize),
                        ) {
                            Text(text = "落底")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTetrisControls(
    buttonSize: Dp,
    gap: Dp,
    canControl: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRotate: () -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TetrisControlButton(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "向左移动",
                    modifier = Modifier.size(buttonSize * 0.5f),
                )
            },
            size = buttonSize,
            enabled = canControl,
            onClick = onMoveLeft,
        )
        TetrisControlButton(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "顺时针旋转",
                    modifier = Modifier.size(buttonSize * 0.5f),
                )
            },
            size = buttonSize,
            enabled = canControl,
            onClick = onRotate,
        )
        TetrisControlButton(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "向右移动",
                    modifier = Modifier.size(buttonSize * 0.5f),
                )
            },
            size = buttonSize,
            enabled = canControl,
            onClick = onMoveRight,
        )
        TetrisControlButton(
            icon = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "软降一格",
                    modifier = Modifier.size(buttonSize * 0.5f),
                )
            },
            size = buttonSize,
            enabled = canControl,
            onClick = onSoftDrop,
        )
        FilledTonalButton(
            onClick = onHardDrop,
            enabled = canControl,
            modifier = Modifier.size(buttonSize),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = "落底",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun TetrisIconControls(
    controlSize: Dp,
    canControl: Boolean,
    onMoveLeft: () -> Unit,
    onRotate: () -> Unit,
    onMoveRight: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TetrisControlButton(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "向左移动",
                    modifier = Modifier.size(controlSize * 0.5f),
                )
            },
            size = controlSize,
            enabled = canControl,
            onClick = onMoveLeft,
        )
        TetrisControlButton(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "顺时针旋转",
                    modifier = Modifier.size(controlSize * 0.5f),
                )
            },
            size = controlSize,
            enabled = canControl,
            onClick = onRotate,
        )
        TetrisControlButton(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "向右移动",
                    modifier = Modifier.size(controlSize * 0.5f),
                )
            },
            size = controlSize,
            enabled = canControl,
            onClick = onMoveRight,
        )
    }
}

@Composable
private fun TetrisControlButton(
    icon: @Composable () -> Unit,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        content = icon,
    )
}

@Composable
private fun TetrisBoard(
    board: List<Int>,
    activeCells: List<TetrominoCell>,
    activeType: TetrominoType?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .semantics {
                    contentDescription = "俄罗斯方块棋盘"
                },
        ) {
            val cellWidth = size.width / TETRIS_COLUMNS
            val cellHeight = size.height / TETRIS_ROWS
            val gap = min(cellWidth, cellHeight) * 0.08f
            val emptyFill = scheme.surfaceContainer.copy(alpha = 0.55f)
            val emptyBorder = scheme.outlineVariant.copy(alpha = 0.45f)

            for (row in 0 until TETRIS_ROWS) {
                for (column in 0 until TETRIS_COLUMNS) {
                    val topLeft = Offset(column * cellWidth, row * cellHeight)
                    drawRoundRect(
                        color = emptyFill,
                        topLeft = topLeft,
                        size = Size(cellWidth, cellHeight),
                        cornerRadius = CornerRadius(cellWidth * 0.12f),
                    )
                    drawRoundRect(
                        color = emptyBorder,
                        topLeft = topLeft,
                        size = Size(cellWidth, cellHeight),
                        cornerRadius = CornerRadius(cellWidth * 0.12f),
                        style = Stroke(width = 1f),
                    )
                }
            }

            fun drawBlock(
                row: Int,
                column: Int,
                type: TetrominoType,
            ) {
                if (row !in 0 until TETRIS_ROWS || column !in 0 until TETRIS_COLUMNS) return
                val topLeft = Offset(
                    x = column * cellWidth + gap / 2f,
                    y = row * cellHeight + gap / 2f,
                )
                val blockSize = Size(cellWidth - gap, cellHeight - gap)
                drawRoundRect(
                    color = tetrominoColor(type = type, scheme = scheme),
                    topLeft = topLeft,
                    size = blockSize,
                    cornerRadius = CornerRadius(cellWidth * 0.18f),
                )
                drawRoundRect(
                    color = scheme.outline.copy(alpha = 0.62f),
                    topLeft = topLeft,
                    size = blockSize,
                    cornerRadius = CornerRadius(cellWidth * 0.18f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            if (board.size >= TETRIS_ROWS * TETRIS_COLUMNS) {
                for (row in 0 until TETRIS_ROWS) {
                    for (column in 0 until TETRIS_COLUMNS) {
                        val value = board[row * TETRIS_COLUMNS + column]
                        if (value in 1..7) {
                            drawBlock(
                                row = row,
                                column = column,
                                type = TetrominoType.entries[value - 1],
                            )
                        }
                    }
                }
            }

            if (activeType != null) {
                activeCells.forEach { cell ->
                    drawBlock(
                        row = cell.row,
                        column = cell.col,
                        type = activeType,
                    )
                }
            }
        }
    }
}

@Composable
private fun NextPiecePreview(
    type: TetrominoType?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.semantics {
            contentDescription = "下一个方块：${type?.name ?: "无"}"
        },
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        if (type == null) {
            Box(modifier = Modifier.fillMaxSize())
        } else {
            val cells = TetrisEngine.previewCells(type = type)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                val minRow = cells.minOf { it.row }
                val maxRow = cells.maxOf { it.row }
                val minCol = cells.minOf { it.col }
                val maxCol = cells.maxOf { it.col }
                val columns = maxCol - minCol + 1
                val rows = maxRow - minRow + 1
                val cell = min(size.width / (columns + 0.6f), size.height / (rows + 0.6f))
                val offsetX = (size.width - cell * columns) / 2f
                val offsetY = (size.height - cell * rows) / 2f

                cells.forEach { pieceCell ->
                    val topLeft = Offset(
                        x = offsetX + (pieceCell.col - minCol) * cell + cell * 0.06f,
                        y = offsetY + (pieceCell.row - minRow) * cell + cell * 0.06f,
                    )
                    val blockSize = Size(cell * 0.88f, cell * 0.88f)
                    drawRoundRect(
                        color = tetrominoColor(type = type, scheme = scheme),
                        topLeft = topLeft,
                        size = blockSize,
                        cornerRadius = CornerRadius(cell * 0.18f),
                    )
                    drawRoundRect(
                        color = scheme.outline.copy(alpha = 0.62f),
                        topLeft = topLeft,
                        size = blockSize,
                        cornerRadius = CornerRadius(cell * 0.18f),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }
    }
}

private fun tetrominoColor(
    type: TetrominoType,
    scheme: androidx.compose.material3.ColorScheme,
): Color = when (type) {
    TetrominoType.I -> scheme.primary
    TetrominoType.J -> scheme.tertiary
    TetrominoType.L -> scheme.secondary
    TetrominoType.O -> scheme.primaryContainer
    TetrominoType.S -> scheme.secondaryContainer
    TetrominoType.T -> scheme.tertiaryContainer
    TetrominoType.Z -> scheme.errorContainer
}
