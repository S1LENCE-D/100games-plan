package com.playbox.app.games.liquidsort

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameButtonContent
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GameReadyOverlay
import com.playbox.app.ui.components.game.GameSaveChoiceDialog
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.min

private val LiquidHelp = listOf(
    "每个试管最多容纳 4 层液体。",
    "点击一个有液体的试管选中它，再点击目标试管倒入。",
    "只有目标试管为空，或目标顶部颜色与来源顶部颜色相同，才能倒入。",
    "一次会把来源顶部连续的同色液体尽量倒入目标；最终每个试管要么为空，要么只有一种颜色。",
)

private val LiquidPalette = listOf(
    Color(0xFFE45B78),
    Color(0xFFF2C14E),
    Color(0xFF43B581),
    Color(0xFF4B8BE5),
    Color(0xFFB26AD4),
    Color(0xFFE8834A),
    Color(0xFF49B6C9),
    Color(0xFFD56A9A),
    Color(0xFF8A9A5B),
    Color(0xFF7A78D6),
)

@Composable
fun LiquidSortRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: LiquidSortViewModel = viewModel(
        factory = LiquidSortViewModel.factory(container.gameStatsRepository, container.gameSaveRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    GameScaffold(
        gameName = "彩液分瓶",
        score = state.score,
        bestScore = bestScore,
        canPause = false,
        isPaused = false,
        onBackRequest = {
            if (state.hasStarted && !state.showResult) showExitConfirmation = true else onExit()
        },
        onTogglePause = {},
        extraStats = listOf(
            GameStatusItem("关卡", state.level.toString()),
            GameStatusItem("步数", state.moves.toString()),
        ),
        onHelpRequest = { showHelp = true },
        onSaveRequest = if (state.hasStarted && !state.isSolved) viewModel::saveGame else null,
    ) {
        GameContentContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.selectedTube?.let { "已选择第 ${it + 1} 管，点击目标试管倒入" }
                        ?: "选择一个试管，再点击目标试管",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                if (state.message.isNotBlank()) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val columns = min(6, maxOf(2, (maxWidth.value / 68f).toInt()))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        state.tubes.chunked(columns).forEachIndexed { rowIndex, row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                row.forEachIndexed { indexInRow, tube ->
                                    val tubeIndex = rowIndex * columns + indexInRow
                                    TubeView(
                                        liquid = tube,
                                        selected = state.selectedTube == tubeIndex,
                                        validTarget = tubeIndex in state.validTargets,
                                        onClick = { viewModel.selectTube(tubeIndex) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::undo,
                        enabled = state.canUndo && !state.isSolved,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        GameButtonContent(Icons.Filled.KeyboardArrowLeft, "撤回")
                    }
                    FilledTonalButton(
                        onClick = viewModel::restart,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        GameButtonContent(Icons.Filled.Refresh, "重新开始")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "每种颜色最终收进同一支试管",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.hasStarted && !state.showSaveChoice) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "彩液分瓶",
                description = "把顶部同色液体倒入空管或同色顶部试管，直到每支试管只剩一种颜色。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            )
        }
    }

    if (showHelp) {
        GameHelpSheet("彩液分瓶", LiquidHelp, onDismiss = { showHelp = false })
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
    if (state.showSaveChoice) {
        GameSaveChoiceDialog(
            gameName = "彩液分瓶",
            onResume = viewModel::resumeSavedGame,
            onNewGame = viewModel::startNewGameFromSave,
            onExit = onExit,
        )
    }
    if (state.showResult) {
        GameResultDialog(
            gameName = "彩液分瓶",
            score = state.score,
            bestScore = bestScore,
            isNewRecord = state.isNewRecord,
            onReplay = viewModel::nextLevel,
            onExit = onExit,
            replayLabel = "下一关",
            details = listOf(
                "第 ${state.level} 关完成",
                "用了 ${state.moves} 步",
            ),
        )
    }
}

@Composable
private fun TubeView(
    liquid: List<Int>,
    selected: Boolean,
    validTarget: Boolean,
    onClick: () -> Unit,
) {
    val outline = when {
        selected -> MaterialTheme.colorScheme.tertiary
        validTarget -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
    val targetGlow = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
    val shadow = Color.Black.copy(alpha = 0.32f)
    Canvas(
        modifier = Modifier
            .width(56.dp)
            .height(150.dp)
            .offset(y = if (selected) (-8).dp else 0.dp)
            .clickable(onClick = onClick),
    ) {
        val left = 5f
        val top = 6f
        val right = size.width - 5f
        val bottom = size.height - 4f
        val inner = Rect(left + 4f, top + 6f, right - 4f, bottom - 4f)
        if (validTarget || selected) {
            drawRoundRect(
                color = targetGlow,
                topLeft = Offset(left - 4f, top - 4f),
                size = Size(right - left + 8f, bottom - top + 6f),
                cornerRadius = CornerRadius(18f, 18f),
            )
        }
        drawRoundRect(
            color = shadow,
            topLeft = Offset(left + 3f, top + 5f),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(16f, 16f),
        )
        val glass = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, right, bottom),
                    topLeft = CornerRadius(14f, 14f),
                    topRight = CornerRadius(14f, 14f),
                    bottomLeft = CornerRadius(24f, 24f),
                    bottomRight = CornerRadius(24f, 24f),
                ),
            )
        }
        drawPath(
            glass,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f),
                    glassTint,
                    Color.White.copy(alpha = 0.04f),
                ),
            ),
        )
        val liquidClip = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = inner,
                    topLeft = CornerRadius(10f, 10f),
                    topRight = CornerRadius(10f, 10f),
                    bottomLeft = CornerRadius(18f, 18f),
                    bottomRight = CornerRadius(18f, 18f),
                ),
            )
        }
        val layerHeight = inner.height / LIQUID_TUBE_CAPACITY
        clipPath(liquidClip) {
            liquid.forEachIndexed { index, colorIndex ->
                val base = LiquidPalette[colorIndex % LiquidPalette.size]
                val layerTop = inner.bottom - (index + 1) * layerHeight
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(base.copy(alpha = 0.82f), base),
                        startY = layerTop,
                        endY = layerTop + layerHeight,
                    ),
                    topLeft = Offset(inner.left, layerTop),
                    size = Size(inner.width, layerHeight + 1f),
                )
                if (index == liquid.lastIndex) {
                    drawOval(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(inner.left, layerTop - 1f),
                        size = Size(inner.width, layerHeight * 0.16f),
                    )
                }
                // 微小气泡，增加玻璃与液体的层次
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = 1.6f,
                    center = Offset(inner.left + inner.width * 0.3f, layerTop + layerHeight * 0.55f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = 1f,
                    center = Offset(inner.left + inner.width * 0.68f, layerTop + layerHeight * 0.3f),
                )
            }
        }
        drawPath(
            glass,
            color = outline,
            style = Stroke(width = if (selected) 3f else 2f),
        )
        // 管口与内壁
        drawRoundRect(
            color = outline,
            topLeft = Offset(left, top),
            size = Size(right - left, 8f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = if (selected) 3f else 2f),
        )
        drawOval(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(left + 5f, top + 2f),
            size = Size(right - left - 10f, 4f),
        )
        // 玻璃高光
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(left + 9f, top + 20f),
            end = Offset(left + 9f, bottom - 30f),
            strokeWidth = 2.4f,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(right - 11f, top + 24f),
            end = Offset(right - 11f, bottom - 38f),
            strokeWidth = 1.2f,
        )
    }
}
