package com.playbox.app.ui.screens.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.playbox.app.games.nearby.NearbyUiState

@Composable
internal fun TttContent(
    state: NearbyUiState,
    onCell: (Int) -> Unit,
    onNext: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val ttt = state.ttt
    val localMark = if (state.isHost) 1 else 2
    val filled = ttt.board.count { it != 0 }
    val localTurn = !ttt.isDraw && ttt.winner == 0 && ((filled % 2 == 0) == (localMark == 1))
    val markLabel = if (localMark == 1) "X" else "O"
    InfoCard(
        title = "双人井字棋",
        message = "你执 $markLabel，轮流落子，先连成三子者获胜。",
    )
    Spacer(modifier = Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { column ->
                    val index = row * 3 + column
                    val mark = ttt.board.getOrElse(index) { 0 }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (mark == 0) {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            )
                            .clickable(
                                enabled = localTurn && mark == 0,
                                onClick = { onCell(index) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (mark != 0) {
                            Text(
                                text = if (mark == 1) "X" else "O",
                                style = MaterialTheme.typography.displaySmall,
                                color = if (mark == 1) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                            )
                        }
                    }
                }
            }
            if (row < 2) Spacer(modifier = Modifier.height(6.dp))
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = when {
            ttt.isDraw -> "平局"
            ttt.winner == localMark -> "你赢了"
            ttt.winner != 0 -> "对方赢了"
            localTurn -> "轮到你了"
            else -> "等待对方落子"
        },
        style = MaterialTheme.typography.titleMedium,
        color = if (ttt.winner != 0 || ttt.isDraw) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (ttt.isDraw || ttt.winner != 0) {
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(text = "再来一局")
        }
    }
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text(text = "断开连接")
    }
}
