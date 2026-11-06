package com.playbox.app.ui.screens.nearby

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.playbox.app.games.nearby.NearbyUiState

@Composable
internal fun BombContent(
    state: NearbyUiState,
    onGuess: (Int) -> Unit,
    onRematch: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val bomb = state.bomb
    var input by rememberSaveable { mutableStateOf("") }
    val myTurn = !bomb.isOver && bomb.turn == state.myIndex
    val parsed = input.toIntOrNull()
    val valid = myTurn && !bomb.waiting && parsed != null && parsed in bomb.low..bomb.high
    val loserIsMe = bomb.loser == state.myIndex

    InfoCard(
        title = "多人数字炸弹",
        message = "${state.playerCount} 人轮流猜 1–100。踩中隐藏炸弹的玩家出局。",
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "范围 ${bomb.low} – ${bomb.high}",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = when {
            bomb.isOver -> "游戏结束"
            myTurn -> "轮到你了"
            else -> "等待玩家 ${bomb.turn + 1} 猜测"
        },
        style = MaterialTheme.typography.titleMedium,
        color = if (myTurn && !bomb.isOver) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    bomb.lastGuess?.let { last ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "上一位玩家猜了 $last",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = input,
        onValueChange = { value ->
            if (value.length <= 3 && value.all(Char::isDigit)) input = value
        },
        label = { Text(text = "输入 1–100") },
        singleLine = true,
        enabled = !bomb.isOver && !bomb.waiting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = {
            val value = input.toIntOrNull() ?: return@Button
            onGuess(value)
            input = ""
        },
        enabled = valid,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(text = if (bomb.waiting) "等待结果…" else "确认猜测")
    }
    if (bomb.isOver) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (loserIsMe) {
                "砰！你踩中了炸弹"
            } else {
                "砰！玩家 ${(bomb.loser ?: 0) + 1} 踩中炸弹，你赢了"
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (loserIsMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (state.isHost) {
            OutlinedButton(onClick = onRematch, modifier = Modifier.fillMaxWidth()) {
                Text(text = "再来一局")
            }
        } else {
            Text(
                text = "等待房主开始下一局",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text(text = "断开连接")
    }
}
