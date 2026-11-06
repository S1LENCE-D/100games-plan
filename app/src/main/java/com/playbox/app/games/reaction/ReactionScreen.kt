package com.playbox.app.games.reaction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GameReadyOverlay
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameStatusItem

private val ReactionHelpLines = listOf(
    "开始前跟随音频节拍点击，每小节 4 拍，第 4 拍为重音；共 4 轮采样。",
    "每轮先显示等待状态，随后突然变色；变色瞬间点击全屏任意位置。",
    "在变色前点击会被判定为过早，本轮重新开始。",
    "共 5 轮，记录补偿后的每轮毫秒数，并统计平均与最快成绩。",
)

@Composable
fun ReactionRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: ReactionViewModel = viewModel(
        factory = ReactionViewModel.factory(
            container.gameStatsRepository,
            container.settingsRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    GameScaffold(
        gameName = "反应力挑战",
        score = uiState.averageMillis,
        bestScore = bestScore,
        canPause = false,
        isPaused = false,
        onTogglePause = {},
        scoreLabel = "平均",
        bestScoreLabel = "最快",
        onBackRequest = { showExitConfirmation = true },
        extraStats = listOf(
            GameStatusItem(
                label = "轮次",
                value = "${uiState.round.coerceAtMost(uiState.totalRounds)}/${uiState.totalRounds}",
            ),
            GameStatusItem(
                label = "上次",
                value = uiState.lastMillis?.let { "${it}ms" } ?: "--",
            ),
            GameStatusItem(
                label = "补偿",
                value = "${if (uiState.compensationMs >= 0) "+" else ""}${uiState.compensationMs}ms",
            ),
        ),
        onHelpRequest = { showHelp = true },
    ) {
        GameContentContainer {
            if (uiState.phase == ReactionPhase.CALIBRATION) {
                ReactionCalibrationSurface(
                    state = uiState,
                    onTap = viewModel::calibrationTap,
                    onStart = viewModel::beginChallenge,
                    onSkip = viewModel::skipCalibration,
                    onRecalibrate = viewModel::recalibrate,
                    onClear = viewModel::clearSavedCalibration,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ReactionSurface(
                    state = uiState,
                    onTap = viewModel::tap,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (!uiState.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "反应力挑战",
                description = "首次跟四四拍完成 4 轮校准并保存到本机；之后直接复用，也可在开始前重新校准。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            )
        }
    }

    if (showHelp) {
        GameHelpSheet("反应力挑战", ReactionHelpLines, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(onConfirm = onExit, onDismiss = { showExitConfirmation = false })
    }
    if (uiState.showResult) {
        GameResultDialog(
            gameName = "反应力挑战",
            score = uiState.averageMillis,
            bestScore = bestScore,
            isNewRecord = uiState.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
            scoreLabel = "平均反应",
            scoreSuffix = "ms",
        )
    }
}

@Composable
private fun ReactionCalibrationSurface(
    state: ReactionUiState,
    onTap: () -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onRecalibrate: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val pulseScale by animateFloatAsState(
        targetValue = if (state.calibrationPulse) 1.12f else 0.86f,
        animationSpec = tween(90),
        label = "reaction-calibration-pulse",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onTap)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "延迟校准",
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "听到重音时点击屏幕",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        if (state.calibrationAccent) scheme.tertiary else scheme.primary,
                    ),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = when {
                    state.calibrationReused -> "已复用本机校准"
                    state.calibrationSaved -> "校准完成 · 已保存到本机"
                    state.calibrationComplete -> "校准完成 · 已采样 ${state.calibrationSamples} 次"
                    else -> "第 ${state.calibrationRound}/4 轮 · 已采样 ${state.calibrationSamples}/4"
                },
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "延迟补偿 ${signedMs(state.compensationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.primary,
            )
            if (state.calibrationReused) {
                Text(
                    text = "首次校准已保存到本机，可直接开始",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else if (state.calibrationSaved) {
                Text(
                    text = "下次开始将自动复用本机校准",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (state.calibrationComplete) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(text = "开始挑战")
                }
                TextButton(onClick = onRecalibrate) {
                    Text(text = "重新校准")
                }
                if (state.calibrationReused) {
                    TextButton(onClick = onClear) {
                        Text(text = "清除本机校准")
                    }
                }
            } else {
                Text(
                    text = "4/4 拍 · 第 4 拍重音 · 共 4 轮",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onSkip) {
                    Text(text = "跳过校准")
                }
            }
        }
    }
}

@Composable
private fun ReactionSurface(
    state: ReactionUiState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val background: Color
    val title: String
    val subtitle: String
    when (state.phase) {
        ReactionPhase.WAITING -> {
            background = scheme.surfaceContainerHigh
            title = "等待变色…"
            subtitle = "变色瞬间点击屏幕任意位置"
        }
        ReactionPhase.GO -> {
            background = scheme.primary
            title = "点击！"
            subtitle = "全屏点击 · 越快越好"
        }
        ReactionPhase.TOO_SOON -> {
            background = scheme.errorContainer
            title = "太快了"
            subtitle = "还没变色，本轮即将重新开始"
        }
        ReactionPhase.ROUND_RESULT -> {
            background = scheme.secondaryContainer
            title = state.lastMillis?.let { "${it}ms" } ?: "--"
            subtitle = "准备下一轮"
        }
        ReactionPhase.FINISHED -> {
            background = scheme.surfaceContainerLow
            title = "完成"
            subtitle = "平均 ${state.averageMillis}ms"
        }
        ReactionPhase.CALIBRATION -> {
            background = scheme.surfaceContainerHigh
            title = ""
            subtitle = ""
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable(onClick = onTap)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = if (state.phase == ReactionPhase.GO) {
                    scheme.onPrimary
                } else {
                    scheme.onSurface
                },
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.phase == ReactionPhase.GO) {
                    scheme.onPrimary.copy(alpha = 0.78f)
                } else {
                    scheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "延迟补偿 ${signedMs(state.compensationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.phase == ReactionPhase.GO) {
                    scheme.onPrimary.copy(alpha = 0.68f)
                } else {
                    scheme.onSurfaceVariant
                },
            )
            if (state.results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = state.results.joinToString("  ·  ") { "${it}ms" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.phase == ReactionPhase.GO) {
                        scheme.onPrimary.copy(alpha = 0.7f)
                    } else {
                        scheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun signedMs(value: Int): String = "${if (value >= 0) "+" else ""}${value}ms"
