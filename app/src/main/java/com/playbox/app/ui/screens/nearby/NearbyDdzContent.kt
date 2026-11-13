package com.playbox.app.ui.screens.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.playbox.app.games.doudizhu.DdzPhase
import com.playbox.app.games.nearby.NearbyUiState

@Composable
internal fun NearbyDdzContent(
    state: NearbyUiState,
    onBid: (Int) -> Unit,
    onPlay: (List<Int>) -> Unit,
    onPass: () -> Unit,
    onNext: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val game = state.ddz
    if (game == null) {
        InfoCard(title = "联机斗地主", message = "正在等待房主发牌…", showProgress = true)
        TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("退出游戏") }
        return
    }
    val me = state.myIndex.coerceIn(0, 2)
    val hand = game.hands.getOrElse(me) { emptyList() }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    val myTurn = if (game.phase == DdzPhase.BIDDING) game.currentBidder == me else game.currentPlayer == me
    InfoCard(
        title = "联机斗地主",
        message = when (game.phase) {
            DdzPhase.BIDDING -> "叫地主：当前 ${game.highestBid} 分"
            DdzPhase.PLAYING -> "你是${if (game.landlord == me) "地主" else "农民"} · 底分 ${game.multiplier}"
            DdzPhase.FINISHED -> "本局结束"
        },
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = game.hands.withIndex().joinToString("  ") { (i, h) -> "${playerName(i, me)}:${h.size}" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = when {
            game.phase == DdzPhase.FINISHED -> if (game.landlordWon == true) "地主胜利" else "农民胜利"
            myTurn -> "轮到你"
            else -> "等待其他玩家"
        },
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    game.lastPlay?.let {
        Text(
            text = "上家：${it.cards.joinToString(" ") { c -> c.rankLabel() + c.suitLabel() }}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
    if (game.phase == DdzPhase.BIDDING) {
        if (myTurn) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(0, 1, 2, 3).forEach { points ->
                    Button(onClick = { onBid(points) }, modifier = Modifier.weight(1f)) {
                        Text(if (points == 0) "不叫" else "$points 分")
                    }
                }
            }
        }
    } else if (game.phase == DdzPhase.PLAYING) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            hand.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { card ->
                        Text(
                            text = card.rankLabel() + card.suitLabel(),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (card.id in selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable(enabled = myTurn) {
                                    selected = if (card.id in selected) selected - card.id else selected + card.id
                                }
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (myTurn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onPlay(selected.toList()); selected = emptySet() },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("出牌") }
                Button(onClick = onPass, modifier = Modifier.weight(1f)) { Text("不要") }
            }
        }
    }
    if (game.isGameOver) {
        if (state.isHost) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("下一轮") }
        } else {
            Text(
                "等待房主开始下一轮",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("退出游戏") }
}

private fun playerName(index: Int, me: Int): String = when {
    index == me -> "你"
    index == 0 -> "房主"
    else -> "玩家${index + 1}"
}
