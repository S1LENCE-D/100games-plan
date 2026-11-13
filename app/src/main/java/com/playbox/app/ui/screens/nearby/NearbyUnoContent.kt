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
import androidx.compose.material3.AlertDialog
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
import com.playbox.app.games.nearby.NearbyUiState
import com.playbox.app.games.uno.UnoCard
import com.playbox.app.games.uno.UnoColor

@Composable
internal fun NearbyUnoContent(
    state: NearbyUiState,
    onPlay: (Int, UnoColor?) -> Unit,
    onDraw: () -> Unit,
    onNext: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val game = state.uno
    if (game == null) {
        InfoCard(title = "联机 UNO", message = "正在等待房主发牌…", showProgress = true)
        TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("退出游戏") }
        return
    }
    val me = state.myIndex.coerceIn(0, game.hands.lastIndex.coerceAtLeast(0))
    val hand = game.hands.getOrElse(me) { emptyList() }
    var wildIndex by remember { mutableStateOf<Int?>(null) }
    InfoCard(
        title = "联机 UNO",
        message = "你手牌 ${hand.size} 张 · 其他玩家 " +
            game.hands.withIndex().filter { it.index != me }.joinToString(" ") { "${it.value.size}" } + " 张",
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InfoCard(title = "牌堆顶", message = game.top?.let { "${colorName(it.color)} ${it.label()}" } ?: "—")
        InfoCard(title = "当前颜色", message = colorName(game.currentColor))
        InfoCard(title = "牌堆", message = game.drawPileSize.toString())
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = when {
            game.winner == me -> "你赢了"
            game.winner != null -> "对方赢了"
            game.currentPlayer == me -> "轮到你出牌"
            else -> "等待对方出牌"
        },
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        hand.forEachIndexed { index, card ->
            val playable = game.currentPlayer == me && index in game.playableIndices
            Text(
                text = "${colorName(card.color)} ${card.label()}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (card.color == UnoColor.WILD) MaterialTheme.colorScheme.surfaceContainerHigh
                        else cardColor(card.color).copy(alpha = if (playable) 0.85f else 0.35f),
                    )
                    .clickable(enabled = playable) {
                        if (card.isWild) wildIndex = index else onPlay(index, null)
                    }
                    .padding(14.dp),
                color = Color.White,
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (game.currentPlayer == me && game.winner == null) {
        Button(onClick = onDraw, modifier = Modifier.fillMaxWidth()) { Text("抽一张牌") }
    }
    if (game.winner != null) {
        if (state.isHost) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("下一轮") }
        } else {
            TextButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("等待房主开始下一轮")
            }
        }
    }
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("退出游戏") }

    wildIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { wildIndex = null },
            title = { Text("选择颜色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(UnoColor.RED, UnoColor.YELLOW, UnoColor.GREEN, UnoColor.BLUE).forEach { color ->
                        TextButton(
                            onClick = { wildIndex = null; onPlay(index, color) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(colorName(color)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { wildIndex = null }) { Text("取消") } },
        )
    }
}

private fun cardColor(color: UnoColor): Color = when (color) {
    UnoColor.RED -> Color(0xFFD7263D)
    UnoColor.YELLOW -> Color(0xFFF2C14E)
    UnoColor.GREEN -> Color(0xFF2A9D5C)
    UnoColor.BLUE -> Color(0xFF246EB9)
    UnoColor.WILD -> Color(0xFF555B66)
}

private fun colorName(color: UnoColor): String = when (color) {
    UnoColor.RED -> "红"
    UnoColor.YELLOW -> "黄"
    UnoColor.GREEN -> "绿"
    UnoColor.BLUE -> "蓝"
    UnoColor.WILD -> "万能"
}
