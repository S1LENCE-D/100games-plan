package com.playbox.app.ui.components.game

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 进入有存档的游戏时先询问恢复或新开，避免直接覆盖玩家上一轮进度。
 */
@Composable
fun GameSaveChoiceDialog(
    gameName: String,
    onResume: () -> Unit,
    onNewGame: () -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onExit,
        title = { Text("发现上次存档") },
        text = {
            Text("《$gameName》有一局未完成的进度。恢复存档，或者开启新游戏并丢弃上次存档。")
        },
        confirmButton = {
            Button(onClick = onResume) {
                Text("恢复存档")
            }
        },
        dismissButton = {
            TextButton(onClick = onNewGame) {
                Text("新游戏（丢弃存档）")
            }
        },
    )
}
