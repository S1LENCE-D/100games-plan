package com.playbox.app.games.game2048

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BOARD_ANIMATION_DURATION_MILLIS = 140

private val HelpLines2048 = listOf(
    "向四个方向滑动棋盘，所有方块会一起移动。",
    "两个相同数字相撞时会合并，并得到对应分数。",
    "每次有效滑动后会在空白处生成一个新方块。",
    "棋盘填满且没有相邻同值方块时本局结束。",
)

@Composable
fun Game2048RouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: Game2048ViewModel = viewModel(
        factory = Game2048ViewModel.factory(container.gameStatsRepository, container.gameSaveRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    GameBackgroundPauseEffect(onBackgroundPause = viewModel::onLifecyclePause)

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
        gameName = "2048",
        score = uiState.score,
        bestScore = bestScore,
        canPause = uiState.hasStarted && !uiState.isGameOver,
        isPaused = uiState.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = listOf(
            GameStatusItem(label = "最大方块", value = uiState.largestTile.toString()),
        ),
        onHelpRequest = {
            if (uiState.hasStarted && !uiState.isGameOver && !uiState.isPaused) {
                viewModel.togglePause()
            }
            showHelp = true
        },
        onSaveRequest = if (uiState.hasStarted && !uiState.isGameOver) viewModel::saveGame else null,
    ) {
        GameContentContainer {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 520.dp || maxWidth > maxHeight * 1.2f
                if (isWide) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val boardSize = minOf(maxWidth, maxHeight, 560.dp)
                        Game2048Board(
                            state = uiState,
                            onMove = viewModel::move,
                            onAnimationFinished = viewModel::onMoveAnimationFinished,
                            modifier = Modifier.size(boardSize),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            val boardSize = minOf(maxWidth, maxHeight, 520.dp)
                            Game2048Board(
                                state = uiState,
                                onMove = viewModel::move,
                                onAnimationFinished = viewModel::onMoveAnimationFinished,
                                modifier = Modifier.size(boardSize),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "滑动方块，让相同数字合并",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }

        if (!uiState.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "2048",
                description = "向四个方向滑动棋盘，让相同数字合并。每次合并都会得分，目标是创造 2048 方块。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            )
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
            gameName = "2048",
            helpLines = HelpLines2048,
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
            gameName = "2048",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
        )
    }
}

@Composable
private fun Game2048Board(
    state: Game2048UiState,
    onMove: (MoveDirection) -> Unit,
    onAnimationFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    // 每次换动画时新建一个从 0 开始的 Animatable：第一帧不会短暂绘制到上一次动画的终点。
    val animationProgress = remember(state.animation?.id) { Animatable(0f) }
    // 预生成所有可能数字的文本布局，动画期间只改变位置，避免逐帧 measure。
    val tileVisuals = remember(textMeasurer, colorScheme, typography) {
        buildTileVisuals(colorScheme, typography, textMeasurer)
    }

    LaunchedEffect(state.animation?.id) {
        val animation = state.animation ?: return@LaunchedEffect
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = BOARD_ANIMATION_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
        onAnimationFinished(animation.id)
    }

    val canPlay = state.hasStarted && !state.isPaused && !state.isGameOver
    val gap = 8.dp
    val contentPadding = 12.dp
    val swipeThreshold = 24.dp
    val tileBorderWidth = 1.5.dp
    val cellBorderWidth = 1.dp

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .semantics {
                    contentDescription = if (state.hasStarted) {
                        "2048 棋盘，当前得分 ${state.score}"
                    } else {
                        "2048 棋盘预览"
                    }
                    customActions = if (canPlay) {
                        listOf(
                            CustomAccessibilityAction("向上移动") {
                                onMove(MoveDirection.UP)
                                true
                            },
                            CustomAccessibilityAction("向下移动") {
                                onMove(MoveDirection.DOWN)
                                true
                            },
                            CustomAccessibilityAction("向左移动") {
                                onMove(MoveDirection.LEFT)
                                true
                            },
                            CustomAccessibilityAction("向右移动") {
                                onMove(MoveDirection.RIGHT)
                                true
                            },
                        )
                    } else {
                        emptyList()
                    }
                }
                .pointerInput(canPlay) {
                    if (!canPlay) return@pointerInput
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
                                    if (dragOffset.x > 0) MoveDirection.RIGHT else MoveDirection.LEFT
                                } else {
                                    if (dragOffset.y > 0) MoveDirection.DOWN else MoveDirection.UP
                                }
                                onMove(direction)
                            }
                            dragOffset = Offset.Zero
                        },
                    )
                },
        ) {
            val gapPx = gap.toPx()
            val cellSize = (size.minDimension - gapPx * 3f) / 4f
            val origin = (size.minDimension - (cellSize * 4f + gapPx * 3f)) / 2f
            val corner = CornerRadius(cellSize * 0.18f)
            val emptyFill = colorScheme.surfaceContainer.copy(alpha = 0.55f)
            val emptyOutline = colorScheme.outlineVariant.copy(alpha = 0.65f)
            val tileSize = Size(cellSize, cellSize)
            val tileStroke = Stroke(width = tileBorderWidth.toPx())

            fun cellOffset(row: Float, col: Float): Offset = Offset(
                x = origin + col * (cellSize + gapPx),
                y = origin + row * (cellSize + gapPx),
            )

            fun drawTile(
                value: Int,
                row: Float,
                col: Float,
            ) {
                val visual = tileVisuals[value] ?: return
                val topLeft = cellOffset(row = row, col = col)
                drawRoundRect(
                    color = visual.colors.container,
                    topLeft = topLeft,
                    size = tileSize,
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = visual.colors.border,
                    topLeft = topLeft,
                    size = tileSize,
                    cornerRadius = corner,
                    style = tileStroke,
                )
                val layout = visual.textLayout
                // 文本落在整数像素上，滑动时避免字形在子像素位置抖动/闪烁。
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = (topLeft.x + (cellSize - layout.size.width) / 2f).roundToInt().toFloat(),
                        y = (topLeft.y + (cellSize - layout.size.height) / 2f).roundToInt().toFloat(),
                    ),
                )
            }

            for (row in 0 until 4) {
                for (col in 0 until 4) {
                    val topLeft = cellOffset(row = row.toFloat(), col = col.toFloat())
                    drawRoundRect(
                        color = emptyFill,
                        topLeft = topLeft,
                        size = tileSize,
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = emptyOutline,
                        topLeft = topLeft,
                        size = tileSize,
                        cornerRadius = corner,
                        style = Stroke(width = cellBorderWidth.toPx()),
                    )
                }
            }

            val animation = state.animation
            if (animation != null) {
                val progress = animationProgress.value
                animation.motions.forEach { motion ->
                    val row = motion.fromRow + (motion.toRow - motion.fromRow) * progress
                    val col = motion.fromCol + (motion.toCol - motion.fromCol) * progress
                    drawTile(value = motion.value, row = row, col = col)
                }
            } else {
                state.tiles.forEach { tile ->
                    drawTile(
                        value = tile.value,
                        row = tile.row.toFloat(),
                        col = tile.col.toFloat(),
                    )
                }
            }
        }
    }
}

private data class TileVisual(
    val colors: TileColors,
    val textLayout: TextLayoutResult,
)

private val TILE_TEXT_VALUES: List<Int> = buildList {
    var value = 2
    repeat(24) {
        add(value)
        value = value shl 1
    }
}

private fun buildTileVisuals(
    scheme: androidx.compose.material3.ColorScheme,
    typography: androidx.compose.material3.Typography,
    textMeasurer: TextMeasurer,
): Map<Int, TileVisual> = TILE_TEXT_VALUES.associateWith { value ->
    val colors = tilePalette(value = value, scheme = scheme)
    val layout = textMeasurer.measure(
        text = value.toString(),
        style = tileTextStyle(value = value, typography = typography)
            .copy(color = colors.content),
        maxLines = 1,
    )
    TileVisual(colors = colors, textLayout = layout)
}

private data class TileColors(
    val container: Color,
    val content: Color,
    val border: Color,
)

private fun tilePalette(
    value: Int,
    scheme: androidx.compose.material3.ColorScheme,
): TileColors {
    val (container, content) = when {
        value <= 2 -> scheme.surfaceContainerHighest to scheme.onSurface
        value == 4 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        value == 8 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        value == 16 -> scheme.primaryContainer to scheme.onPrimaryContainer
        value == 32 -> scheme.primary to scheme.onPrimary
        value == 64 -> scheme.secondary to scheme.onSecondary
        value == 128 -> scheme.tertiary to scheme.onTertiary
        value == 256 -> scheme.errorContainer to scheme.onErrorContainer
        value == 512 -> scheme.inverseSurface to scheme.inverseOnSurface
        value == 1024 -> scheme.primary to scheme.onPrimary
        value == 2048 -> scheme.tertiary to scheme.onTertiary
        else -> scheme.inverseSurface to scheme.inverseOnSurface
    }
    val border = if (value <= 4) {
        scheme.outline.copy(alpha = 0.82f)
    } else {
        content.copy(alpha = 0.36f)
    }
    return TileColors(
        container = container,
        content = content,
        border = border,
    )
}

private fun tileTextStyle(
    value: Int,
    typography: androidx.compose.material3.Typography,
): TextStyle = when {
    value < 100 -> typography.headlineMedium
    value < 1000 -> typography.titleLarge
    value < 10000 -> typography.titleMedium
    else -> typography.labelLarge
}
