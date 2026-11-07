package com.playbox.app.games.ballbattle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.SoftCard
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameBackgroundPauseEffect
import com.playbox.app.ui.components.game.GameBoardFrame
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GamePauseOverlay
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.min
import kotlin.math.roundToInt

private val BallBattleHelp = listOf(
    "每局开始前有 12 个技能点，可自由加到力量、生命、速度、体型和暴击上。",
    "力量提高攻击，生命提高血量与减伤，速度提高移动速度，体型提高半径和质量，暴击提高暴击率。",
    "球的渲染尺寸变大时，基础移速会按半径等比提升；高速碰撞会细分为多个物理步，避免穿模。",
    "技能点全部分配后开始自动对战，两个球在场地中反弹碰撞，碰撞会同时造成伤害。",
    "生命归零即落败；45 秒仍未分胜负时，按剩余生命比例判定。",
)

@Composable
fun BallBattleRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: BallBattleViewModel = viewModel(
        factory = BallBattleViewModel.factory(container.gameStatsRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    GameBackgroundPauseEffect(onBackgroundPause = viewModel::onLifecyclePause)

    val requestBack: () -> Unit = {
        if (state.phase == BallBattlePhase.BATTLE && !state.showResult) {
            if (!state.isPaused) viewModel.togglePause()
            showExitConfirmation = true
        } else {
            onExit()
        }
    }

    GameScaffold(
        gameName = "小球对对碰",
        score = state.score,
        bestScore = bestScore,
        canPause = state.phase == BallBattlePhase.BATTLE && !state.showResult,
        isPaused = state.isPaused,
        onBackRequest = requestBack,
        onTogglePause = viewModel::togglePause,
        extraStats = if (state.phase == BallBattlePhase.BUILD) {
            listOf(
                GameStatusItem("总点数", BALL_TOTAL_SKILL_POINTS.toString()),
                GameStatusItem("剩余点数", state.remainingPoints.toString()),
            )
        } else {
            listOf(
                GameStatusItem("我方生命", state.snapshot?.player?.hp?.toString() ?: "0"),
                GameStatusItem("对手生命", state.snapshot?.opponent?.hp?.toString() ?: "0"),
            )
        },
        onHelpRequest = {
            if (state.phase == BallBattlePhase.BATTLE && !state.isPaused) {
                viewModel.togglePause()
            }
            showHelp = true
        },
    ) {
        GameContentContainer {
            when (state.phase) {
                BallBattlePhase.BUILD -> BallBuildContent(
                    state = state,
                    onAdd = viewModel::addPoint,
                    onRemove = viewModel::removePoint,
                    onRandom = viewModel::randomizeBuild,
                    onReset = viewModel::resetBuild,
                    onStart = viewModel::startBattle,
                )

                BallBattlePhase.BATTLE,
                BallBattlePhase.RESULT,
                -> BallBattleContent(state = state)
            }
        }

        GamePauseOverlay(
            visible = state.phase == BallBattlePhase.BATTLE &&
                state.isPaused && !state.showResult && !showHelp && !showExitConfirmation,
            onResume = viewModel::togglePause,
            onRestart = viewModel::rematch,
            onExitRequest = { showExitConfirmation = true },
            onHelpRequest = { showHelp = true },
        )
    }

    if (showHelp) {
        GameHelpSheet("小球对对碰", BallBattleHelp, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            onConfirm = onExit,
            onDismiss = { showExitConfirmation = false },
        )
    }
    if (state.showResult) {
        val winnerText = when (state.winner) {
            1 -> "你赢了"
            2 -> "AI 球获胜"
            else -> "平局"
        }
        val snapshot = state.snapshot
        GameResultDialog(
            gameName = "小球对对碰",
            score = state.score,
            bestScore = bestScore,
            isNewRecord = state.isNewRecord,
            onReplay = viewModel::rematch,
            onExit = onExit,
            extraActionLabel = "调整加点",
            onExtraAction = viewModel::openBuild,
            details = listOf(
                winnerText,
                "我方剩余生命 ${snapshot?.player?.hp ?: 0}/${snapshot?.player?.maxHp ?: 0}",
                "造成伤害 ${snapshot?.playerDamageDealt ?: 0}",
                "AI 加点：${buildSummary(state.opponentBuild)}",
            ),
        )
    }
}

@Composable
private fun BallBuildContent(
    state: BallBattleUiState,
    onAdd: (BallAttribute) -> Unit,
    onRemove: (BallAttribute) -> Unit,
    onRandom: () -> Unit,
    onReset: () -> Unit,
    onStart: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val contentWidth = minOf(maxWidth, 680.dp)
        Column(
            modifier = Modifier
                .width(contentWidth)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "自定义你的球",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "自由分配 12 个技能点，然后用它撞赢随机 AI 球。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "技能点 ${state.build.spent}/$BALL_TOTAL_SKILL_POINTS",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "剩余 ${state.remainingPoints}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.remainingPoints == 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            state.build.spent.toFloat() / BALL_TOTAL_SKILL_POINTS
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "攻击 ${state.playerStats.attack} · 生命 ${state.playerStats.maxHp} · 速度 ${state.playerStats.moveSpeed.roundToInt()} · 暴击 ${"%.0f%%".format(state.playerStats.critChance * 100)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            BallAttribute.entries.forEach { attribute ->
                SkillPointRow(
                    attribute = attribute,
                    value = state.build.pointsOf(attribute),
                    stats = state.playerStats,
                    canAdd = state.remainingPoints > 0,
                    canRemove = state.build.pointsOf(attribute) > 0,
                    onAdd = { onAdd(attribute) },
                    onRemove = { onRemove(attribute) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onRandom, modifier = Modifier.weight(1f)) {
                    Text(text = "随机分配")
                }
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text(text = "重置")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onStart,
                enabled = state.remainingPoints == 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(text = if (state.remainingPoints == 0) "开始对战" else "请分配完 ${state.remainingPoints} 点")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "单机对手随机分配 12 点；联机模式可在附近联机中与好友各自加点对战。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SkillPointRow(
    attribute: BallAttribute,
    value: Int,
    stats: BallStats,
    canAdd: Boolean,
    canRemove: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${attribute.label}  $value",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = attribute.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statEffect(attribute, stats),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRemove, enabled = canRemove) {
                Text(
                    text = "−",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (canRemove) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
                    },
                )
            }
            IconButton(onClick = onAdd, enabled = canAdd) {
                Icon(Icons.Filled.Add, contentDescription = "增加${attribute.label}")
            }
        }
    }
}

private fun statEffect(attribute: BallAttribute, stats: BallStats): String = when (attribute) {
    BallAttribute.POWER -> "攻击 ${stats.attack}"
    BallAttribute.VITALITY -> "生命 ${stats.maxHp} · 减伤 ${"%.0f%%".format(stats.damageReduction * 100)}"
    BallAttribute.AGILITY -> "速度 ${stats.moveSpeed.roundToInt()}"
    BallAttribute.SIZE -> "半径 ${stats.radius.roundToInt()} · 质量 ${"%.1f".format(stats.mass)}"
    BallAttribute.LUCK -> "暴击 ${"%.0f%%".format(stats.critChance * 100)}"
}

@Composable
private fun BallBattleContent(state: BallBattleUiState) {
    val snapshot = state.snapshot ?: return
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val contentWidth = minOf(maxWidth, 760.dp)
        val arenaHeight = contentWidth * 0.60f
        Column(
            modifier = Modifier.width(contentWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HealthBarRow(
                playerHp = snapshot.player.hp,
                playerMaxHp = snapshot.player.maxHp,
                opponentHp = snapshot.opponent.hp,
                opponentMaxHp = snapshot.opponent.maxHp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            GameBoardFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(arenaHeight),
                contentPadding = 0.dp,
                clipContent = true,
            ) {
                BallArena(snapshot = snapshot, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (state.winner == null) {
                    "自动碰撞中 · 双方碰撞同时造成伤害"
                } else {
                    when (state.winner) {
                        1 -> "你赢了"
                        2 -> "AI 球获胜"
                        else -> "平局"
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.winner == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AI 加点：${buildSummary(state.opponentBuild)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "金色数字为暴击伤害，双方碰撞都会显示伤害数字",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HealthBarRow(
    playerHp: Int,
    playerMaxHp: Int,
    opponentHp: Int,
    opponentMaxHp: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HealthBar(
            label = "你",
            hp = playerHp,
            maxHp = playerMaxHp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        HealthBar(
            label = "AI 球",
            hp = opponentHp,
            maxHp = opponentMaxHp,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HealthBar(
    label: String,
    hp: Int,
    maxHp: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$hp/$maxHp",
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (maxHp == 0) 0f else hp.toFloat() / maxHp },
            color = color,
            trackColor = color.copy(alpha = 0.16f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}

@Composable
private fun BallArena(
    snapshot: BallBattleSnapshot,
    modifier: Modifier = Modifier,
) {
    val playerColor = MaterialTheme.colorScheme.primary
    val opponentColor = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val grid = MaterialTheme.colorScheme.outlineVariant
    val flashColor = MaterialTheme.colorScheme.onSurface
    val damageToPlayerColor = MaterialTheme.colorScheme.error
    val damageToOpponentColor = MaterialTheme.colorScheme.primary
    val criticalColor = Color(0xFFFFC857)
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        drawRect(color = surface)
        val scaleX = size.width / snapshot.arenaWidth
        val scaleY = size.height / snapshot.arenaHeight
        val scale = min(scaleX, scaleY)
        val xOffset = (size.width - snapshot.arenaWidth * scale) / 2f
        val yOffset = (size.height - snapshot.arenaHeight * scale) / 2f

        // 轻量网格，帮助看出球的速度和碰撞位置。
        var gx = 0f
        while (gx <= snapshot.arenaWidth) {
            drawLine(
                color = grid.copy(alpha = 0.18f),
                start = Offset(xOffset + gx * scale, yOffset),
                end = Offset(xOffset + gx * scale, yOffset + snapshot.arenaHeight * scale),
                strokeWidth = 1f,
            )
            gx += 100f
        }
        var gy = 0f
        while (gy <= snapshot.arenaHeight) {
            drawLine(
                color = grid.copy(alpha = 0.18f),
                start = Offset(xOffset, yOffset + gy * scale),
                end = Offset(xOffset + snapshot.arenaWidth * scale, yOffset + gy * scale),
                strokeWidth = 1f,
            )
            gy += 100f
        }

        fun drawBall(body: BallBody, color: Color) {
            val center = Offset(xOffset + body.x * scale, yOffset + body.y * scale)
            val radius = body.radius * scale
            drawCircle(
                color = color.copy(alpha = 0.14f),
                radius = radius + 7f,
                center = center,
            )
            drawCircle(color = color, radius = radius, center = center)
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = radius * 0.24f,
                center = Offset(center.x - radius * 0.28f, center.y - radius * 0.30f),
            )
            drawCircle(
                color = color.copy(alpha = 0.45f),
                radius = radius,
                center = center,
                style = Stroke(width = 2f),
            )
        }

        drawBall(snapshot.player, playerColor)
        drawBall(snapshot.opponent, opponentColor)

        if (snapshot.collisionPulse) {
            val center = Offset(
                xOffset + (snapshot.player.x + snapshot.opponent.x) / 2f * scale,
                yOffset + (snapshot.player.y + snapshot.opponent.y) / 2f * scale,
            )
            drawCircle(
                color = flashColor.copy(alpha = 0.45f),
                radius = (snapshot.player.radius + 18f) * scale,
                center = center,
                style = Stroke(width = 3f),
            )
        }

        snapshot.damageEvents.forEach { event ->
            val ageMs = (snapshot.elapsedMs - event.atMs).coerceIn(0L, 1_000L)
            val progress = ageMs / 1_000f
            val alpha = 1f - progress
            val label = if (event.isCritical) "暴击 -${event.amount}" else "-${event.amount}"
            val color = when {
                event.isCritical -> criticalColor
                event.targetPlayer -> damageToPlayerColor
                else -> damageToOpponentColor
            }
            val style = TextStyle(
                color = color,
                fontSize = if (event.isCritical) 23.sp else 17.sp,
                fontWeight = FontWeight.Bold,
            )
            val layout = textMeasurer.measure(label, style)
            val x = xOffset + event.x * scale - layout.size.width / 2f
            val y = yOffset + event.y * scale - layout.size.height / 2f - progress * 34f
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x, y),
                alpha = alpha,
            )
        }
    }
}

private fun buildSummary(build: BallBuild): String = listOf(
    "力${build.power}",
    "生${build.vitality}",
    "速${build.agility}",
    "体${build.size}",
    "暴${build.luck}",
).joinToString(" ")
