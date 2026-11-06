package com.playbox.app.games.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.playbox.app.ui.components.game.GameSegmentedOptions
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.min

private val MemoryHelpLines = listOf(
    "点击卡片翻开，一次翻两张。",
    "两张图案相同即可配对并保持翻开，不同则短暂停留后盖回。",
    "配对全部卡片即可完成；步数越少、用时越短，得分越高。",
)

@Composable
fun MemoryRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: MemoryViewModel = viewModel(
        factory = MemoryViewModel.factory(container.gameStatsRepository),
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
        gameName = "记忆翻牌",
        score = uiState.score,
        bestScore = bestScore,
        canPause = uiState.hasStarted && !uiState.isGameOver,
        isPaused = uiState.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = listOf(
            GameStatusItem(label = "时间", value = "${uiState.elapsedSeconds}s"),
            GameStatusItem(label = "步数", value = uiState.moves.toString()),
            GameStatusItem(label = "配对", value = "${uiState.matchedPairs}/${uiState.difficulty.pairs}"),
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
                val rows = uiState.difficulty.rows
                val columns = uiState.difficulty.columns
                val cell = minOf(
                    maxWidth / columns.toFloat(),
                    maxHeight * 0.76f / rows.toFloat(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "找出全部 ${uiState.difficulty.pairs} 对图案",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    GameBoardFrame(
                        modifier = Modifier.size(
                            cell * columns.toFloat(),
                            cell * rows.toFloat(),
                        ),
                    ) {
                        MemoryBoard(
                            cards = uiState.cards,
                            rows = rows,
                            columns = columns,
                            onFlip = viewModel::flip,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (!uiState.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "记忆翻牌",
                description = "每次翻开两张卡片，找出全部相同图案。可选 4×4、4×5、5×6 三种牌面。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            ) {
                GameSegmentedOptions(
                    options = MemoryDifficulty.entries,
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
        GameHelpSheet("记忆翻牌", MemoryHelpLines, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            onConfirm = onExit,
            onDismiss = { showExitConfirmation = false },
        )
    }
    if (uiState.showResult) {
        GameResultDialog(
            gameName = "记忆翻牌",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
        )
    }
}

@Composable
private fun MemoryBoard(
    cards: List<MemoryCard>,
    rows: Int,
    columns: Int,
    onFlip: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(columns) { column ->
                    val index = row * columns + column
                    val card = cards.getOrNull(index)
                    if (card != null) {
                        MemoryCardView(
                            card = card,
                            onClick = { onFlip(index) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryCardView(
    card: MemoryCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val visible = card.faceUp || card.matched
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        label = "memory-card-scale",
    )
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .clickable(
                enabled = !card.matched,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = shape,
        color = when {
            card.matched -> scheme.primaryContainer
            card.faceUp -> scheme.secondaryContainer
            else -> scheme.surfaceContainerHigh
        },
        contentColor = when {
            card.matched -> scheme.onPrimaryContainer
            card.faceUp -> scheme.onSecondaryContainer
            else -> scheme.onSurfaceVariant
        },
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (visible) {
                Text(
                    text = card.symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                MemoryCardBack()
            }
            if (card.matched) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MemoryCardBack() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
    Canvas(modifier = Modifier.size(30.dp)) {
        val radius = size.minDimension * 0.075f
        listOf(0.32f to 0.32f, 0.68f to 0.32f, 0.32f to 0.68f, 0.68f to 0.68f).forEach { (x, y) ->
            drawCircle(
                color = color,
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y),
            )
        }
    }
}
