package com.playbox.app.ui.screens.nearby

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.playbox.app.games.ballbattle.BALL_TOTAL_SKILL_POINTS
import com.playbox.app.games.ballbattle.BallAttribute
import com.playbox.app.games.ballbattle.BallBattleSnapshot
import com.playbox.app.games.ballbattle.BallStats
import com.playbox.app.games.nearby.NearbyUiState
import com.playbox.app.ui.components.SoftCard
import com.playbox.app.ui.components.game.GameBoardFrame
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun NearbyBallContent(
    state: NearbyUiState,
    onAdd: (BallAttribute) -> Unit,
    onRemove: (BallAttribute) -> Unit,
    onRandom: () -> Unit,
    onReady: () -> Unit,
    onLeave: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val ball = state.ball
    val snapshot = ball.snapshot
    if (snapshot == null) {
        BallLobbyBuild(
            state = state,
            onAdd = onAdd,
            onRemove = onRemove,
            onRandom = onRandom,
            onReady = onReady,
            onLeave = onLeave,
            onDisconnect = onDisconnect,
        )
    } else {
        BallLobbyBattle(
            state = state,
            snapshot = snapshot,
            onLeave = onLeave,
            onDisconnect = onDisconnect,
        )
    }
}

@Composable
private fun BallLobbyBuild(
    state: NearbyUiState,
    onAdd: (BallAttribute) -> Unit,
    onRemove: (BallAttribute) -> Unit,
    onRandom: () -> Unit,
    onReady: () -> Unit,
    onLeave: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val ball = state.ball
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
            InfoCard(
                title = "小球对对碰 · 联机",
                message = if (state.isHost) {
                    "各自分配 12 点；双方准备后由房主统一开局。"
                } else {
                    "各自分配 12 点；你准备好后等待房主开局。"
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "技能点 ${ball.build.spent}/$BALL_TOTAL_SKILL_POINTS",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "剩余 ${ball.build.remaining}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (ball.build.remaining == 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            ball.build.spent.toFloat() / BALL_TOTAL_SKILL_POINTS
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            BallAttribute.entries.forEach { attribute ->
                NearbyBallSkillRow(
                    attribute = attribute,
                    value = ball.build.pointsOf(attribute),
                    stats = ball.build.stats,
                    canAdd = ball.build.remaining > 0 && !ball.localReady,
                    canRemove = ball.build.pointsOf(attribute) > 0 && !ball.localReady,
                    onAdd = { onAdd(attribute) },
                    onRemove = { onRemove(attribute) },
                )
                Spacer(modifier = Modifier.height(7.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRandom,
                    enabled = !ball.localReady,
                    modifier = Modifier.weight(1f),
                ) { Text("随机分配") }
                OutlinedButton(
                    onClick = onReady,
                    enabled = !ball.localReady && ball.build.remaining == 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (ball.localReady) "已准备" else "准备开战")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = buildString {
                    append(if (ball.localReady) "你已准备" else "你尚未准备")
                    append(" · ")
                    append(if (ball.remoteReady) "好友已准备" else "等待好友")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onLeave) { Text(text = "返回房间") }
            TextButton(onClick = onDisconnect) { Text(text = "断开连接") }
        }
    }
}

@Composable
private fun NearbyBallSkillRow(
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${attribute.label}  $value",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = nearbyBallEffect(attribute, stats),
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

@Composable
private fun BallLobbyBattle(
    state: NearbyUiState,
    snapshot: BallBattleSnapshot,
    onLeave: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val ball = state.ball
    val hostIsMe = state.isHost
    val leftLabel = if (hostIsMe) "你" else "好友"
    val rightLabel = if (hostIsMe) "好友" else "你"
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val arenaSize = minOf(
            maxWidth,
            760.dp,
            (maxHeight - 120.dp).coerceAtLeast(180.dp),
        )
        Column(
            modifier = Modifier.width(arenaSize),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NearbyBallHealth(
                    label = leftLabel,
                    hp = snapshot.player.hp,
                    maxHp = snapshot.player.maxHp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                NearbyBallHealth(
                    label = rightLabel,
                    hp = snapshot.opponent.hp,
                    maxHp = snapshot.opponent.maxHp,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            GameBoardFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(arenaSize),
                contentPadding = 0.dp,
                clipContent = true,
            ) {
                NearbyBallArena(snapshot = snapshot, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (!ball.isFinished) {
                    when {
                        ball.localFinished -> "你的动画已结束，等待好友动画完成…"
                        ball.remoteFinished -> "好友动画已结束，等待你的动画完成…"
                        else -> "自动碰撞中 · 金色数字为暴击"
                    }
                } else {
                    when (ball.winner) {
                        1 -> if (hostIsMe) "你赢了" else "好友赢了"
                        2 -> if (hostIsMe) "好友赢了" else "你赢了"
                        else -> "平局"
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (ball.isFinished) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (ball.isFinished) {
                Button(onClick = onLeave) { Text(text = "返回房间") }
            } else {
                TextButton(onClick = onLeave) { Text(text = "退出本局") }
            }
            TextButton(onClick = onDisconnect) { Text(text = "断开连接") }
        }
    }
}

@Composable
private fun NearbyBallHealth(
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
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                "$hp/$maxHp",
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
private fun NearbyBallArena(
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
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        drawRect(surface)
        val scale = min(size.width / snapshot.arenaWidth, size.height / snapshot.arenaHeight)
        val xOffset = (size.width - snapshot.arenaWidth * scale) / 2f
        val yOffset = (size.height - snapshot.arenaHeight * scale) / 2f
        var x = 0f
        while (x <= snapshot.arenaWidth) {
            drawLine(
                color = grid.copy(alpha = 0.18f),
                start = Offset(xOffset + x * scale, yOffset),
                end = Offset(xOffset + x * scale, yOffset + snapshot.arenaHeight * scale),
                strokeWidth = 1f,
            )
            x += 100f
        }
        var y = 0f
        while (y <= snapshot.arenaHeight) {
            drawLine(
                color = grid.copy(alpha = 0.18f),
                start = Offset(xOffset, yOffset + y * scale),
                end = Offset(xOffset + snapshot.arenaWidth * scale, yOffset + y * scale),
                strokeWidth = 1f,
            )
            y += 100f
        }

        fun drawBall(body: com.playbox.app.games.ballbattle.BallBody, color: Color) {
            val center = Offset(xOffset + body.x * scale, yOffset + body.y * scale)
            val radius = body.radius * scale
            drawCircle(color = color.copy(alpha = 0.14f), radius = radius + 7f, center = center)
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
            val progress = ((snapshot.elapsedMs - event.atMs).coerceIn(0L, 1_000L) / 1_000f)
            val label = if (event.isCritical) "暴击 -${event.amount}" else "-${event.amount}"
            val style = TextStyle(
                color = if (event.isCritical) {
                    Color(0xFFFFC857)
                } else if (event.targetPlayer) {
                    damageToPlayerColor
                } else {
                    damageToOpponentColor
                },
                fontSize = if (event.isCritical) 20.sp else 15.sp,
                fontWeight = FontWeight.Bold,
            )
            val layout = textMeasurer.measure(label, style)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    xOffset + event.x * scale - layout.size.width / 2f,
                    yOffset + event.y * scale - layout.size.height / 2f - progress * 32f,
                ),
                alpha = 1f - progress,
            )
        }
    }
}

private fun nearbyBallEffect(attribute: BallAttribute, stats: BallStats): String = when (attribute) {
    BallAttribute.POWER -> "攻击 ${stats.attack}"
    BallAttribute.VITALITY -> "生命 ${stats.maxHp} · 减伤 ${"%.0f%%".format(stats.damageReduction * 100)}"
    BallAttribute.AGILITY -> "速度 ${stats.moveSpeed.roundToInt()}"
    BallAttribute.SIZE -> "半径 ${stats.radius.roundToInt()} · 质量 ${"%.1f".format(stats.mass)}"
    BallAttribute.LUCK -> "暴击 ${"%.0f%%".format(stats.critChance * 100)}"
}
