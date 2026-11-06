package com.playbox.app.games.dino

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.R
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
import kotlin.math.roundToInt

private val DinoHelpLines = listOf(
    "点击屏幕或“跳跃”按钮越过仙人掌。",
    "奔跑距离越远，速度越快，得分越高。",
    "撞到仙人掌即结束；跳起后可以越过较矮的仙人掌。",
)

@Composable
fun DinoRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: DinoViewModel = viewModel(
        factory = DinoViewModel.factory(container.gameStatsRepository),
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
        gameName = "谷歌小恐龙",
        score = uiState.score,
        bestScore = bestScore,
        canPause = uiState.hasStarted && !uiState.isGameOver,
        isPaused = uiState.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = listOf(
            GameStatusItem(label = "速度", value = uiState.speed.toInt().toString()),
        ),
        onHelpRequest = {
            if (uiState.hasStarted && !uiState.isGameOver && !uiState.isPaused) {
                viewModel.togglePause()
            }
            showHelp = true
        },
    ) {
        GameContentContainer {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(uiState.hasStarted, uiState.isPaused, uiState.isGameOver, uiState.showResult) {
                        detectTapGestures {
                            if (uiState.hasStarted && !uiState.isPaused && !uiState.isGameOver && !uiState.showResult) {
                                viewModel.jump()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val boardHeight = minOf(maxHeight * 0.58f, maxWidth * 0.74f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "点击任意位置跳跃 · 躲开仙人掌",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    GameBoardFrame(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(boardHeight),
                        contentPadding = 0.dp,
                        clipContent = true,
                    ) {
                        DinoBoard(
                            state = uiState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "点击任意位置跳跃",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!uiState.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "谷歌小恐龙",
                description = "点击屏幕任意位置跳跃，越过仙人掌。跑得越远，得分越高。",
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
        GameHelpSheet("谷歌小恐龙", DinoHelpLines, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(onConfirm = onExit, onDismiss = { showExitConfirmation = false })
    }
    if (uiState.showResult) {
        GameResultDialog(
            gameName = "谷歌小恐龙",
            score = uiState.score,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
        )
    }
}

@Composable
private fun DinoBoard(
    state: DinoUiState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val sprite = ImageBitmap.imageResource(R.drawable.dino_sprite_sheet)
    Canvas(modifier = modifier) {
        val scale = size.width / DinoEngine.WORLD_WIDTH
        val ground = size.height * 0.82f
        drawRect(color = scheme.surfaceContainerLow, size = size)

        // 使用开源 sprite 中的云朵；颜色跟随主题，不直接复制原图配色。
        val cloudTint = ColorFilter.tint(
            color = scheme.onSurfaceVariant.copy(alpha = 0.16f),
            blendMode = BlendMode.SrcIn,
        )
        listOf(
            0.12f to 0.16f,
            0.63f to 0.25f,
        ).forEach { (xFrac, yFrac) ->
            drawImage(
                image = sprite,
                srcOffset = IntOffset(86, 2),
                srcSize = IntSize(46, 14),
                dstOffset = IntOffset((size.width * xFrac).roundToInt(), (size.height * yFrac).roundToInt()),
                dstSize = IntSize((46 * scale * 1.2f).roundToInt(), (14 * scale * 1.2f).roundToInt()),
                colorFilter = cloudTint,
                filterQuality = FilterQuality.None,
            )
        }

        drawLine(
            color = scheme.outline,
            start = Offset(0f, ground),
            end = Offset(size.width, ground),
            strokeWidth = 2f,
        )
        // 地面纹理保持轻量，和 sprite 像素风格一致。
        repeat(14) { index ->
            val x = (index * size.width / 14f + size.width * 0.03f) % size.width
            drawLine(
                color = scheme.outlineVariant,
                start = Offset(x, ground + 6f),
                end = Offset(x + size.width * 0.025f, ground + 6f),
                strokeWidth = 1f,
            )
        }

        state.obstacles.forEach { obstacle ->
            val large = obstacle.width > 20f
            val sourceX = if (large) 332 else 228
            val sourceWidth = if (large) 25 else 17
            val sourceHeight = if (large) 50 else 35
            drawImage(
                image = sprite,
                srcOffset = IntOffset(sourceX, 2),
                srcSize = IntSize(sourceWidth, sourceHeight),
                dstOffset = IntOffset(
                    (obstacle.x * scale).roundToInt(),
                    (ground - obstacle.height * scale).roundToInt(),
                ),
                dstSize = IntSize(
                    (obstacle.width * scale).roundToInt(),
                    (obstacle.height * scale).roundToInt(),
                ),
                colorFilter = ColorFilter.tint(scheme.tertiary, BlendMode.SrcIn),
                filterQuality = FilterQuality.None,
            )
        }

        val dinoFrame = when {
            state.isGameOver -> 220
            state.isJumping -> 0
            else -> if ((state.score / 4) % 2 == 0) 88 else 132
        }
        drawImage(
            image = sprite,
            srcOffset = IntOffset(677 + dinoFrame, 2),
            srcSize = IntSize(44, 47),
            dstOffset = IntOffset(
                (DinoEngine.DINO_X * scale).roundToInt(),
                (ground - state.dinoY * scale - DinoEngine.DINO_HEIGHT * scale).roundToInt(),
            ),
            dstSize = IntSize(
                (DinoEngine.DINO_WIDTH * scale).roundToInt(),
                (DinoEngine.DINO_HEIGHT * scale).roundToInt(),
            ),
            colorFilter = ColorFilter.tint(scheme.primary, BlendMode.SrcIn),
            filterQuality = FilterQuality.None,
        )
    }
}
