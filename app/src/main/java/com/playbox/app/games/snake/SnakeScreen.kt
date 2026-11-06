package com.playbox.app.games.snake

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.domain.model.AppSettings
import com.playbox.app.domain.model.SnakeButtonSize
import com.playbox.app.domain.model.SnakeControlMode
import com.playbox.app.domain.model.SnakeControlSide
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameBackgroundPauseEffect
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GameOptionSwitch
import com.playbox.app.ui.components.game.GamePauseOverlay
import com.playbox.app.ui.components.game.GameReadyOverlay
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameSegmentedOptions
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.launch

private val SnakeHelpLines = listOf(
    "在棋盘上滑动，或点击下方方向键来转向。",
    "吃到果实加 10 分并让身体变长；吃到越多，速度会逐步提升，但会停在安全速度上限。",
    "穿墙模式下撞到边界会从另一侧出现；普通模式撞到边界即结束。",
    "撞到自己的身体也会结束。每局结束后最高分自动保存。",
)

@Composable
fun SnakeRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: SnakeViewModel = viewModel(
        factory = SnakeViewModel.factory(container.gameStatsRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    val appSettings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
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
        gameName = "贪吃蛇",
        score = uiState.score,
        bestScore = bestScore,
        canPause = uiState.hasStarted && !uiState.isGameOver,
        isPaused = uiState.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = listOf(
            GameStatusItem(label = "长度", value = uiState.length.toString()),
            GameStatusItem(label = "模式", value = if (uiState.wrapAround) "穿墙" else "围墙"),
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
                val useWideLayout = maxWidth >= 520.dp || maxWidth > maxHeight * 1.25f
                if (useWideLayout) {
                    SnakeWideLayout(
                        uiState = uiState,
                        canControl = canControl,
                        controlMode = appSettings.snakeControlMode,
                        controlSide = appSettings.snakeControlSide,
                        buttonSize = appSettings.snakeButtonSize,
                        onTurn = viewModel::turn,
                    )
                } else {
                    SnakeCompactLayout(
                        uiState = uiState,
                        canControl = canControl,
                        controlMode = appSettings.snakeControlMode,
                        controlSide = appSettings.snakeControlSide,
                        buttonSize = appSettings.snakeButtonSize,
                        onTurn = viewModel::turn,
                    )
                }
            }
        }

        if (!uiState.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "贪吃蛇",
                description = "吃掉果实让身体变长。滑动或使用方向键转向，撞到自己即结束；可以开启穿墙模式，从棋盘另一侧出现。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            ) {
                Text(
                    text = "速度",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                GameSegmentedOptions(
                    options = SnakeDifficulty.entries,
                    selected = uiState.difficulty,
                    label = { difficulty -> difficulty.label },
                    onSelect = viewModel::selectDifficulty,
                )
                Spacer(modifier = Modifier.height(8.dp))
                GameOptionSwitch(
                    title = "穿墙模式",
                    description = if (uiState.wrapAround) {
                        "撞到边界后从另一侧出现"
                    } else {
                        "撞到边界即游戏结束"
                    },
                    checked = uiState.wrapAround,
                    onCheckedChange = viewModel::setWrapAround,
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "操作方式",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                GameSegmentedOptions(
                    options = SnakeControlMode.entries,
                    selected = appSettings.snakeControlMode,
                    label = { mode -> mode.label() },
                    onSelect = { mode ->
                        scope.launch { container.settingsRepository.setSnakeControlMode(mode) }
                    },
                )
                if (appSettings.snakeControlMode != SnakeControlMode.SWIPE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "按键位置",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GameSegmentedOptions(
                        options = SnakeControlSide.entries,
                        selected = appSettings.snakeControlSide,
                        label = { side -> side.label() },
                        onSelect = { side ->
                            scope.launch { container.settingsRepository.setSnakeControlSide(side) }
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "按键大小",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GameSegmentedOptions(
                        options = SnakeButtonSize.entries,
                        selected = appSettings.snakeButtonSize,
                        label = { size -> size.label() },
                        onSelect = { size ->
                            scope.launch { container.settingsRepository.setSnakeButtonSize(size) }
                        },
                    )
                }
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
            gameName = "贪吃蛇",
            helpLines = SnakeHelpLines,
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
            gameName = "贪吃蛇",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
        )
    }
}

@Composable
private fun SnakeCompactLayout(
    uiState: SnakeUiState,
    canControl: Boolean,
    controlMode: SnakeControlMode,
    controlSide: SnakeControlSide,
    buttonSize: SnakeButtonSize,
    onTurn: (Direction) -> Unit,
) {
    val showDpad = controlMode != SnakeControlMode.SWIPE
    val canSwipe = controlMode != SnakeControlMode.BUTTONS

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val cell = minOf(
                maxWidth / SNAKE_COLUMNS.toFloat(),
                maxHeight / SNAKE_ROWS.toFloat(),
                22.dp,
            )
            SnakeBoard(
                state = uiState,
                canControl = canControl,
                canSwipe = canSwipe,
                onTurn = onTurn,
                modifier = Modifier.size(
                    width = cell * SNAKE_COLUMNS.toFloat(),
                    height = cell * SNAKE_ROWS.toFloat(),
                ),
            )
        }
        if (showDpad) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = if (controlSide == SnakeControlSide.LEFT) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
                ) {
                    SnakeDPad(
                        buttonSize = buttonSize.buttonSizeDp(),
                        canControl = canControl,
                        onTurn = onTurn,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SnakeWideLayout(
    uiState: SnakeUiState,
    canControl: Boolean,
    controlMode: SnakeControlMode,
    controlSide: SnakeControlSide,
    buttonSize: SnakeButtonSize,
    onTurn: (Direction) -> Unit,
) {
    val showDpad = controlMode != SnakeControlMode.SWIPE
    val canSwipe = controlMode != SnakeControlMode.BUTTONS

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val veryShort = maxHeight < 320.dp
        val preferredControlSize = buttonSize.buttonSizeDp()
        val controlSize = when {
            veryShort -> minOf(preferredControlSize, 56.dp)
            maxHeight < 380.dp -> minOf(preferredControlSize, 60.dp)
            else -> preferredControlSize
        }
        val sideWidth = if (showDpad) maxOf(232.dp, controlSize * 3f + 48.dp) else 0.dp
        val gap = 20.dp
        val availableWidth = if (showDpad) {
            (maxWidth - sideWidth - gap).coerceAtLeast(120.dp)
        } else {
            maxWidth
        }
        val cell = minOf(
            availableWidth / SNAKE_COLUMNS.toFloat(),
            maxHeight / SNAKE_ROWS.toFloat(),
            22.dp,
        )
        val boardWidth = cell * SNAKE_COLUMNS.toFloat()
        val boardHeight = cell * SNAKE_ROWS.toFloat()

        if (!showDpad) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SnakeBoard(
                    state = uiState,
                    canControl = canControl,
                    canSwipe = canSwipe,
                    onTurn = onTurn,
                    modifier = Modifier.size(width = boardWidth, height = boardHeight),
                )
            }
        } else {
            val controlPanel: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier.width(sideWidth),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(if (veryShort) 8.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!veryShort) {
                            Text(
                                text = "方向控制",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (controlMode == SnakeControlMode.BUTTONS) {
                                    "使用方向键控制"
                                } else {
                                    "滑动棋盘也可以转向"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        SnakeDPad(
                            buttonSize = controlSize,
                            canControl = canControl,
                            onTurn = onTurn,
                        )
                        if (!veryShort) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "速度：${uiState.difficulty.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (controlSide == SnakeControlSide.LEFT) {
                    controlPanel()
                    Spacer(modifier = Modifier.width(gap))
                }
                SnakeBoard(
                    state = uiState,
                    canControl = canControl,
                    canSwipe = canSwipe,
                    onTurn = onTurn,
                    modifier = Modifier.size(width = boardWidth, height = boardHeight),
                )
                if (controlSide == SnakeControlSide.RIGHT) {
                    Spacer(modifier = Modifier.width(gap))
                    controlPanel()
                }
            }
        }
    }
}

private fun SnakeButtonSize.buttonSizeDp(): Dp = when (this) {
    SnakeButtonSize.SMALL -> 54.dp
    SnakeButtonSize.MEDIUM -> 64.dp
    SnakeButtonSize.LARGE -> 78.dp
}

private fun SnakeControlMode.label(): String = when (this) {
    SnakeControlMode.BOTH -> "滑动 + 按键"
    SnakeControlMode.SWIPE -> "仅滑动"
    SnakeControlMode.BUTTONS -> "仅按键"
}

private fun SnakeControlSide.label(): String = when (this) {
    SnakeControlSide.LEFT -> "左手"
    SnakeControlSide.RIGHT -> "右手"
}

private fun SnakeButtonSize.label(): String = when (this) {
    SnakeButtonSize.SMALL -> "小"
    SnakeButtonSize.MEDIUM -> "中"
    SnakeButtonSize.LARGE -> "大"
}

@Composable
private fun SnakeDPad(
    buttonSize: Dp,
    canControl: Boolean,
    onTurn: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SnakeDirectionButton(
            direction = Direction.UP,
            buttonSize = buttonSize,
            enabled = canControl,
            onClick = onTurn,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SnakeDirectionButton(
                direction = Direction.LEFT,
                buttonSize = buttonSize,
                enabled = canControl,
                onClick = onTurn,
            )
            SnakeDirectionButton(
                direction = Direction.DOWN,
                buttonSize = buttonSize,
                enabled = canControl,
                onClick = onTurn,
            )
            SnakeDirectionButton(
                direction = Direction.RIGHT,
                buttonSize = buttonSize,
                enabled = canControl,
                onClick = onTurn,
            )
        }
    }
}

@Composable
private fun SnakeDirectionButton(
    direction: Direction,
    buttonSize: Dp,
    enabled: Boolean,
    onClick: (Direction) -> Unit,
) {
    val label = when (direction) {
        Direction.UP -> "向上转向"
        Direction.DOWN -> "向下转向"
        Direction.LEFT -> "向左转向"
        Direction.RIGHT -> "向右转向"
    }

    FilledTonalIconButton(
        onClick = { onClick(direction) },
        enabled = enabled,
        modifier = Modifier
            .size(buttonSize)
            .semantics { contentDescription = label },
    ) {
        DirectionChevron(
            direction = direction,
            modifier = Modifier.size(buttonSize * 0.44f),
        )
    }
}

@Composable
private fun DirectionChevron(
    direction: Direction,
    modifier: Modifier = Modifier,
) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val margin = width * 0.24f
        val path = Path().apply {
            when (direction) {
                Direction.UP -> {
                    moveTo(margin, height * 0.68f)
                    lineTo(width / 2f, height * 0.32f)
                    lineTo(width - margin, height * 0.68f)
                }

                Direction.DOWN -> {
                    moveTo(margin, height * 0.32f)
                    lineTo(width / 2f, height * 0.68f)
                    lineTo(width - margin, height * 0.32f)
                }

                Direction.LEFT -> {
                    moveTo(width * 0.68f, margin)
                    lineTo(width * 0.32f, height / 2f)
                    lineTo(width * 0.68f, height - margin)
                }

                Direction.RIGHT -> {
                    moveTo(width * 0.32f, margin)
                    lineTo(width * 0.68f, height / 2f)
                    lineTo(width * 0.32f, height - margin)
                }
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = width * 0.13f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun SnakeBoard(
    state: SnakeUiState,
    canControl: Boolean,
    canSwipe: Boolean,
    onTurn: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val swipeThreshold = remember { 24.dp }

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
                    contentDescription = if (state.hasStarted) {
                        "贪吃蛇棋盘，得分 ${state.score}，长度 ${state.length}"
                    } else {
                        "贪吃蛇棋盘预览"
                    }
                    customActions = if (canControl) {
                        listOf(
                            CustomAccessibilityAction("向上转向") {
                                onTurn(Direction.UP)
                                true
                            },
                            CustomAccessibilityAction("向下转向") {
                                onTurn(Direction.DOWN)
                                true
                            },
                            CustomAccessibilityAction("向左转向") {
                                onTurn(Direction.LEFT)
                                true
                            },
                            CustomAccessibilityAction("向右转向") {
                                onTurn(Direction.RIGHT)
                                true
                            },
                        )
                    } else {
                        emptyList()
                    }
                }
                .pointerInput(canControl, canSwipe) {
                    if (!canControl || !canSwipe) return@pointerInput
                    val threshold = swipeThreshold.toPx()
                    var dragOffset = Offset.Zero
                    detectDragGestures(
                        onDragStart = { dragOffset = Offset.Zero },
                        onDragCancel = { dragOffset = Offset.Zero },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                        },
                        onDragEnd = {
                            if (dragOffset.getDistance() >= threshold) {
                                val direction = if (abs(dragOffset.x) > abs(dragOffset.y)) {
                                    if (dragOffset.x > 0) Direction.RIGHT else Direction.LEFT
                                } else {
                                    if (dragOffset.y > 0) Direction.DOWN else Direction.UP
                                }
                                onTurn(direction)
                            }
                            dragOffset = Offset.Zero
                        },
                    )
                },
        ) {
            val cell = min(
                size.width / SNAKE_COLUMNS.toFloat(),
                size.height / SNAKE_ROWS.toFloat(),
            )
            val originX = (size.width - cell * SNAKE_COLUMNS) / 2f
            val originY = (size.height - cell * SNAKE_ROWS) / 2f

            val gridColor = scheme.outlineVariant.copy(alpha = 0.35f)
            for (column in 1 until SNAKE_COLUMNS) {
                val x = originX + column * cell
                drawLine(
                    color = gridColor,
                    start = Offset(x, originY),
                    end = Offset(x, originY + cell * SNAKE_ROWS),
                    strokeWidth = 1f,
                )
            }
            for (row in 1 until SNAKE_ROWS) {
                val y = originY + row * cell
                drawLine(
                    color = gridColor,
                    start = Offset(originX, y),
                    end = Offset(originX + cell * SNAKE_COLUMNS, y),
                    strokeWidth = 1f,
                )
            }

            state.body.forEachIndexed { index, segment ->
                val isHead = index == 0
                val topLeft = Offset(
                    x = originX + segment.col * cell + cell * 0.06f,
                    y = originY + segment.row * cell + cell * 0.06f,
                )
                val blockSize = cell * 0.88f
                val bodyAlpha = (0.94f - index * 0.018f).coerceAtLeast(0.58f)
                val blockColor = if (isHead) {
                    scheme.primary
                } else {
                    scheme.primary.copy(alpha = bodyAlpha)
                }
                drawRoundRect(
                    color = blockColor,
                    topLeft = topLeft,
                    size = Size(blockSize, blockSize),
                    cornerRadius = CornerRadius(cell * 0.28f),
                )
                drawRoundRect(
                    color = scheme.outline.copy(alpha = 0.45f),
                    topLeft = topLeft,
                    size = Size(blockSize, blockSize),
                    cornerRadius = CornerRadius(cell * 0.28f),
                    style = Stroke(width = 1f),
                )

                if (isHead) {
                    drawSnakeEyes(
                        center = Offset(
                            x = originX + (segment.col + 0.5f) * cell,
                            y = originY + (segment.row + 0.5f) * cell,
                        ),
                        cell = cell,
                        direction = state.direction,
                        eyeColor = scheme.onPrimary,
                    )
                }
            }

            state.food?.let { food ->
                val center = Offset(
                    x = originX + (food.col + 0.5f) * cell,
                    y = originY + (food.row + 0.5f) * cell,
                )
                drawCircle(
                    color = scheme.error,
                    radius = cell * 0.3f,
                    center = center,
                )
                drawCircle(
                    color = scheme.onError.copy(alpha = 0.8f),
                    radius = cell * 0.07f,
                    center = center + Offset(-cell * 0.09f, -cell * 0.09f),
                )
                drawLine(
                    color = scheme.tertiary,
                    start = center + Offset(0f, -cell * 0.3f),
                    end = center + Offset(cell * 0.18f, -cell * 0.46f),
                    strokeWidth = cell * 0.08f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnakeEyes(
    center: Offset,
    cell: Float,
    direction: Direction,
    eyeColor: Color,
) {
    val forward = when (direction) {
        Direction.UP -> Offset(0f, -1f)
        Direction.DOWN -> Offset(0f, 1f)
        Direction.LEFT -> Offset(-1f, 0f)
        Direction.RIGHT -> Offset(1f, 0f)
    }
    val side = Offset(-forward.y, forward.x)
    val eyeCenter = center + forward * (cell * 0.18f)
    val eyeOffset = side * (cell * 0.16f)
    drawCircle(
        color = eyeColor,
        radius = cell * 0.075f,
        center = eyeCenter + eyeOffset,
    )
    drawCircle(
        color = eyeColor,
        radius = cell * 0.075f,
        center = eyeCenter - eyeOffset,
    )
}
