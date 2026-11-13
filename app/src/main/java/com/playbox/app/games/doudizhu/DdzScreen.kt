package com.playbox.app.games.doudizhu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameButtonContent
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GameReadyOverlay
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameStatusItem

private val DdzHelp = listOf(
    "标准 54 张牌，3 人局；你与 2 个电脑对战。",
    "叫分决定地主：地主获得 3 张底牌并先出牌，两名农民合作对抗地主。",
    "牌型包括单张、对子、三带、顺子、连对、飞机、四带二、炸弹和王炸。",
    "炸弹和王炸会让倍数翻倍；地主或农民先出完手牌即获胜。",
    "点击手牌选牌，再点出牌；不要可过牌，提示会帮你选择一手能出的牌。",
)

@Composable
fun DdzRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: DdzViewModel = viewModel(
        factory = DdzViewModel.factory(container.gameStatsRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    val status = when {
        !state.hasStarted -> "准备开始"
        state.phase == DdzPhase.BIDDING -> if (state.currentBidder == 0) "轮到你叫分" else "电脑${state.currentBidder} 叫分中"
        state.isAiThinking -> "电脑${state.currentPlayer} 思考中"
        state.currentPlayer == 0 -> "轮到你出牌"
        else -> "电脑${state.currentPlayer} 回合"
    }

    GameScaffold(
        gameName = "斗地主",
        score = state.score,
        bestScore = bestScore,
        canPause = false,
        isPaused = false,
        onBackRequest = {
            if (state.hasStarted && !state.showResult) showExitConfirmation = true else onExit()
        },
        onTogglePause = {},
        extraStats = listOf(
            GameStatusItem("身份", state.humanRole),
            GameStatusItem("倍数", "${state.multiplier}×"),
        ),
        onHelpRequest = { showHelp = true },
    ) {
        GameContentContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Opponents(state)
                Spacer(modifier = Modifier.height(6.dp))
                BottomCards(state)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "上一步",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = state.lastAction.ifBlank { "等待叫地主" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.phase == DdzPhase.BIDDING) {
                        BiddingCenter(state)
                    } else {
                        TableCenter(state)
                    }
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (state.currentPlayer == 0 && state.phase == DdzPhase.PLAYING) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "你的手牌 ${state.hand.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(102.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                ) {
                    items(state.hand, key = { it.id }) { card ->
                        DdzCardView(
                            card = card,
                            selected = card.id in state.selectedIds,
                            enabled = state.humanTurn && !state.isAiThinking,
                            onClick = { viewModel.toggleCard(card.id) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (state.phase == DdzPhase.BIDDING) {
                    BidControls(
                        state = state,
                        onBid = viewModel::bidHuman,
                    )
                } else {
                    PlayControls(
                        state = state,
                        onPlay = viewModel::playSelected,
                        onPass = viewModel::pass,
                        onHint = viewModel::hint,
                    )
                }
                if (state.message.isNotBlank()) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (!state.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "斗地主",
                description = "你与 2 个电脑对战。叫分抢地主，地主拿 3 张底牌先出牌，先出完手牌的一方获胜。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            )
        }
    }

    if (showHelp) {
        GameHelpSheet("斗地主", DdzHelp, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(onConfirm = onExit, onDismiss = { showExitConfirmation = false })
    }
    if (state.showResult) {
        val humanWon = if (state.landlord == 0) state.landlordWon == true else state.landlordWon == false
        GameResultDialog(
            gameName = "斗地主",
            score = state.score,
            bestScore = bestScore,
            isNewRecord = state.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
            details = listOf(
                if (humanWon) "你赢了" else "你输了",
                if (state.landlordWon == true) "地主获胜" else "农民获胜",
                "倍数 ${state.multiplier}× · 底分 ${state.highestBid.coerceAtLeast(1)}",
                when {
                    state.spring -> "春天"
                    state.antiSpring -> "反春天"
                    else -> "你${if (state.landlord == 0) "是地主" else "是农民"}"
                },
            ),
        )
    }
}

@Composable
private fun Opponents(state: DdzUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (player in 1..2) {
            val current = state.currentPlayer == player && state.phase == DdzPhase.PLAYING
            val bidding = state.currentBidder == player && state.phase == DdzPhase.BIDDING
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (current || bidding) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (current || bidding) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (current || bidding) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "电脑$player",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (state.landlord == player) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("地主", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                    Text(
                        text = "${state.counts.getOrElse(player) { 0 }} 张",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    if (state.landlord == 0) {
        Text(
            text = "你是地主",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun BottomCards(state: DdzUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "底牌",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 6.dp),
        )
        state.bottomCards.forEach { card ->
            if (state.bottomRevealed) {
                DdzCardView(card = card, selected = false, enabled = false, small = true, onClick = {})
            } else {
                CardBack(small = true)
            }
            Spacer(modifier = Modifier.width(3.dp))
        }
    }
}

@Composable
private fun BiddingCenter(state: DdzUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (state.highestBid > 0) "当前最高 ${state.highestBid} 分" else "尚无人叫分",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (state.currentBidder == 0) "请选择叫分" else "电脑${state.currentBidder} 正在考虑",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TableCenter(state: DdzUiState) {
    val play = state.lastPlay
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (play == null) {
            Text(
                text = if (state.landlord == state.currentPlayer) "新一轮，${if (state.currentPlayer == 0) "你先出" else "电脑${state.currentPlayer} 先出"}" else "新一轮",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "${if (play.player == 0) "你" else "电脑${play.player}"} · ${play.combo.label()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(play.cards, key = { it.id }) { card ->
                    DdzCardView(card = card, selected = false, enabled = false, small = true, onClick = {})
                }
            }
        }
    }
}

@Composable
private fun BidControls(state: DdzUiState, onBid: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(
            onClick = { onBid(0) },
            enabled = state.currentBidder == 0 && !state.isAiThinking,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) { GameButtonContent(Icons.Filled.Clear, "不叫") }
        for (points in 1..3) {
            Button(
                onClick = { onBid(points) },
                enabled = state.currentBidder == 0 && !state.isAiThinking && points > state.highestBid,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) { GameButtonContent(Icons.Filled.Check, "$points 分") }
        }
    }
}

@Composable
private fun PlayControls(
    state: DdzUiState,
    onPlay: () -> Unit,
    onPass: () -> Unit,
    onHint: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onHint,
            enabled = state.humanTurn && !state.isAiThinking,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) { GameButtonContent(Icons.Filled.Info, "提示") }
        OutlinedButton(
            onClick = onPass,
            enabled = state.canPass && !state.isAiThinking,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) { GameButtonContent(Icons.Filled.Clear, "不要") }
        Button(
            onClick = onPlay,
            enabled = state.humanTurn && !state.isAiThinking && state.selectedCombo != null,
            modifier = Modifier
                .weight(1.2f)
                .heightIn(min = 48.dp),
        ) { GameButtonContent(Icons.Filled.PlayArrow, "出牌") }
    }
}

@Composable
internal fun DdzCardView(
    card: DdzCard,
    selected: Boolean,
    enabled: Boolean,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val width = if (small) 38.dp else 52.dp
    val height = if (small) 54.dp else 74.dp
    val red = card.suit == DdzSuit.HEART || card.suit == DdzSuit.DIAMOND || card.rank == 17
    val textColor = if (red) Color(0xFFC62828) else Color(0xFF17191E)
    Surface(
        modifier = Modifier
            .width(width)
            .height(height)
            .offset(y = if (selected) (-10).dp else 0.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF6F0E5),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.tertiary else Color(0xFF6D6254),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                text = card.rankLabel(),
                color = textColor,
                fontSize = if (small) 12.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 3.dp, top = 2.dp),
            )
            if (!card.isJoker) {
                Text(
                    text = card.suitLabel(),
                    color = textColor,
                    fontSize = if (small) 11.sp else 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 2.dp),
                )
            } else {
                Text(
                    text = if (card.rank == 17) "大" else "小",
                    color = textColor,
                    fontSize = if (small) 12.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
internal fun CardBack(small: Boolean = false) {
    val width = if (small) 38.dp else 52.dp
    val height = if (small) 54.dp else 74.dp
    Surface(
        modifier = Modifier
            .width(width)
            .height(height),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF252833),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "斗",
                color = Color(0xFFE1B96A),
                fontWeight = FontWeight.Bold,
                fontSize = if (small) 12.sp else 16.sp,
            )
        }
    }
}
