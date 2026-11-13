package com.playbox.app.ui.screens.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.playbox.app.games.nearby.NearbyUiState
import com.playbox.app.games.uno.CardBack
import com.playbox.app.games.uno.ColorPickerDialog
import com.playbox.app.games.uno.UnoCardView
import com.playbox.app.games.uno.UnoColor
import com.playbox.app.ui.components.game.GameButtonContent

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
    val myTurn = game.currentPlayer == me && game.winner == null

    InfoCard(
        title = "联机 UNO",
        message = "你手牌 ${hand.size} 张 · 其他玩家 " +
            game.hands.withIndex().filter { it.index != me }.joinToString(" / ") { "${it.value.size}" } + " 张",
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            game.top?.let {
                UnoCardView(card = it, enabled = false, modifier = Modifier.size(64.dp, 92.dp), onClick = {})
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(colorOf(game.currentColor), CircleShape),
            )
            Text(colorName(game.currentColor), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            CardBack(modifier = Modifier.size(52.dp, 76.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(game.drawPileSize.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    Text(
        text = when {
            game.winner == me -> "你赢了"
            game.winner != null -> "其他玩家获胜"
            myTurn -> "轮到你出牌"
            else -> "等待其他玩家"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text("你的手牌 ${hand.size}", style = MaterialTheme.typography.labelMedium)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        itemsIndexed(hand) { index, card ->
            UnoCardView(
                card = card,
                enabled = myTurn && index in game.playableIndices,
                onClick = {
                    if (card.isWild) wildIndex = index else onPlay(index, null)
                },
                modifier = Modifier
                    .width(66.dp)
                    .fillMaxHeight(),
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onDraw,
            enabled = myTurn,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) { GameButtonContent(Icons.Filled.Add, "抽牌") }
        if (game.winner != null && state.isHost) {
            Button(onClick = onNext, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                GameButtonContent(Icons.Filled.Check, "下一轮")
            }
        }
    }
    if (game.winner != null && !state.isHost) {
        Text(
            "等待房主开始下一轮",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("退出游戏") }

    wildIndex?.let { index ->
        ColorPickerDialog(
            onSelect = { color -> wildIndex = null; onPlay(index, color) },
            onDismiss = { wildIndex = null },
        )
    }
}

private fun colorOf(color: UnoColor): Color = when (color) {
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
