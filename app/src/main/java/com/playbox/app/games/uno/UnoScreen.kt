package com.playbox.app.games.uno

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val UnoHelp = listOf(
    "标准 4 人 UNO：你 + 3 台电脑，每人起手 7 张，先出完手牌者获胜。",
    "出牌需要颜色相同，或数字/功能相同；万能牌可以指定新颜色。",
    "跳过、反转、+2 和万能 +4 会改变出牌顺序或让对手抽牌。",
    "只剩 2 张牌时先点 UNO 宣告；打出后剩 1 张时若没宣告，会罚抽 2 张。",
    "没有可出的牌时抽一张；抽到可出的牌可以继续出，也可以结束回合。",
)

@Composable
fun UnoRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: UnoViewModel = viewModel(
        factory = UnoViewModel.factory(container.gameStatsRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    val statusText = when {
        !state.hasStarted -> "准备开始"
        state.isGameOver -> "对局结束"
        state.isAiThinking -> "电脑出牌中…"
        state.humanTurn -> "轮到你"
        else -> "电脑出牌中…"
    }

    GameScaffold(
        gameName = "UNO",
        score = state.score,
        bestScore = bestScore,
        canPause = false,
        isPaused = false,
        onBackRequest = {
            if (state.hasStarted && !state.showResult) {
                showExitConfirmation = true
            } else {
                onExit()
            }
        },
        onTogglePause = {},
        extraStats = listOf(
            GameStatusItem("牌堆", state.drawPileSize.toString()),
            GameStatusItem("当前色", colorLabel(state.currentColor)),
        ),
        onHelpRequest = { showHelp = true },
    ) {
        GameContentContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                OpponentRow(state)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.lastAction.ifBlank { "等待开始" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .clickable(enabled = state.humanTurn && !state.hasDrawnThisTurn && !state.isGameOver) {
                                    viewModel.drawCard()
                                }
                                .padding(4.dp),
                        ) {
                            CardBack(modifier = Modifier.size(width = 74.dp, height = 108.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = state.drawPileSize.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        Text("抽牌", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(30.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        state.discard?.let {
                            UnoCardView(
                                card = it,
                                enabled = false,
                                modifier = Modifier.size(width = 80.dp, height = 116.dp),
                                onClick = {},
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(cardColor(state.currentColor), CircleShape),
                            )
                            Text(
                                text = "${colorLabel(state.currentColor)} · ${if (state.direction > 0) "顺时针" else "逆时针"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = if (state.humanTurn) "轮到你出牌" else statusText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (state.humanTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "你的手牌 ${state.hand.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                ) {
                    itemsIndexed(state.hand) { index, card ->
                        UnoCardView(
                            card = card,
                            enabled = state.humanTurn &&
                                !state.isAiThinking &&
                                index in state.playableIndices,
                            onClick = { viewModel.tapCard(index) },
                            modifier = Modifier
                                .width(66.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::drawCard,
                        enabled = state.humanTurn && !state.hasDrawnThisTurn && !state.isGameOver,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("抽牌")
                    }
                    if (state.hasDrawnThisTurn && state.humanTurn) {
                        OutlinedButton(
                            onClick = viewModel::endTurnAfterDraw,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("结束回合")
                        }
                    }
                    val canCallUno = state.humanTurn && state.hand.size == 2 && !state.unoDeclared
                    Button(
                        onClick = viewModel::callUno,
                        enabled = canCallUno,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Text(if (state.unoDeclared) "UNO!" else "喊 UNO")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "点击可出的牌；没有可出的牌时点抽牌",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (!state.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "UNO",
                description = "你与 3 个电脑对战，先出完手牌者获胜。剩 2 张时记得喊 UNO。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            )
        }
    }

    if (showHelp) {
        GameHelpSheet("UNO", UnoHelp, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            onConfirm = onExit,
            onDismiss = { showExitConfirmation = false },
        )
    }
    if (state.pendingWildIndex != null) {
        ColorPickerDialog(
            onSelect = viewModel::chooseWildColor,
            onDismiss = viewModel::cancelWildChoice,
        )
    }
    if (state.showResult) {
        GameResultDialog(
            gameName = "UNO",
            score = state.score,
            bestScore = bestScore,
            isNewRecord = state.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
            details = listOf(
                if (state.winner == 0) "你出完了所有手牌" else "电脑${state.winner ?: 1} 获胜",
                "电脑剩余：${state.counts.drop(1).joinToString(" / ") { it.toString() }} 张",
            ),
        )
    }
}

@Composable
private fun OpponentRow(state: UnoUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (player in 1..3) {
            val current = state.currentPlayer == player && !state.isGameOver
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = if (current) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (current) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "电脑$player",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        text = "${state.counts.getOrElse(player) { 0 }} 张",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnoCardView(
    card: UnoCard,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseColor = cardColor(card.color)
    Surface(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (card.isWild) Color(0xFF202126) else baseColor,
        border = BorderStroke(
            width = 2.dp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else Color.Transparent,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (card.isWild) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf(UnoColor.RED, UnoColor.YELLOW, UnoColor.GREEN, UnoColor.BLUE).forEach {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(cardColor(it), CircleShape),
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.size(width = 48.dp, height = 68.dp),
                shape = CircleShape,
                color = Color(0xFFF6EEE2),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = card.label(),
                        fontSize = if (card.kind == UnoKind.NUMBER) 26.sp else 21.sp,
                        fontWeight = FontWeight.Black,
                        color = if (card.isWild) Color(0xFF17191E) else baseColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardBack(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF17191F),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "UNO",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF2C14E),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(UnoColor.RED, UnoColor.YELLOW, UnoColor.GREEN, UnoColor.BLUE).forEach {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(cardColor(it), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(
    onSelect: (UnoColor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择万能牌颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(UnoColor.RED, UnoColor.YELLOW, UnoColor.GREEN, UnoColor.BLUE).forEach { color ->
                    Button(
                        onClick = { onSelect(color) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cardColor(color),
                            contentColor = if (color == UnoColor.YELLOW) Color.Black else Color.White,
                        ),
                    ) {
                        Text(colorLabel(color))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun cardColor(color: UnoColor): Color = when (color) {
    UnoColor.RED -> Color(0xFFD7263D)
    UnoColor.YELLOW -> Color(0xFFF2C14E)
    UnoColor.GREEN -> Color(0xFF2A9D5C)
    UnoColor.BLUE -> Color(0xFF246EB9)
    UnoColor.WILD -> Color(0xFF202126)
}

private fun colorLabel(color: UnoColor): String = when (color) {
    UnoColor.RED -> "红"
    UnoColor.YELLOW -> "黄"
    UnoColor.GREEN -> "绿"
    UnoColor.BLUE -> "蓝"
    UnoColor.WILD -> "万能"
}
