package com.playbox.app.ui.screens.nearby

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.playbox.app.games.nearby.NearbyUiState
import com.playbox.app.games.nearby.NearbyXiangqiUiState
import com.playbox.app.games.xiangqi.XiangqiBoard
import com.playbox.app.games.xiangqi.XiangqiColor
import com.playbox.app.games.xiangqi.XiangqiMode
import com.playbox.app.games.xiangqi.XiangqiOutcome
import com.playbox.app.games.xiangqi.XiangqiPoint
import com.playbox.app.games.xiangqi.XiangqiUiState

@Composable
internal fun NearbyXiangqiContent(
    state: NearbyUiState,
    onPoint: (XiangqiPoint) -> Unit,
    onNext: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val game = state.xiangqi
    val localColor = if (state.isHost) XiangqiColor.RED else XiangqiColor.BLACK
    val localTurn = !game.isGameOver && game.sideToMove == localColor
    val boardState = XiangqiUiState(
        board = game.board,
        mode = XiangqiMode.LOCAL,
        humanColor = localColor,
        sideToMove = game.sideToMove,
        selected = game.selected,
        legalTargets = game.legalTargets,
        lastMove = game.lastMove,
        outcome = game.outcome,
        winner = game.winner,
        inCheck = game.inCheck,
        moveCount = game.moveCount,
    )
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val boardHeight = (LocalConfiguration.current.screenHeightDp.dp - 145.dp).coerceAtLeast(200.dp)

    if (landscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .height(boardHeight)
                    .aspectRatio(0.9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                XiangqiBoard(
                    boardState,
                    onPoint,
                    Modifier.fillMaxSize(),
                    flippedOverride = localColor == XiangqiColor.BLACK,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                XiangqiInfo(localColor)
                XiangqiStatus(game, localColor, localTurn, state.isHost, onNext)
                TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("退出游戏")
                }
            }
        }
        return
    }

    XiangqiInfo(localColor)
    Spacer(modifier = Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        XiangqiBoard(
            boardState,
            onPoint,
            Modifier.fillMaxSize(),
            flippedOverride = localColor == XiangqiColor.BLACK,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    XiangqiStatus(game, localColor, localTurn, state.isHost, onNext)
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text("退出游戏")
    }
}

@Composable
private fun XiangqiInfo(localColor: XiangqiColor) {
    InfoCard(
        title = "联机中国象棋",
        message = "你执${if (localColor == XiangqiColor.RED) "红" else "黑"}，红方先手，点击棋子再点击目标点。",
    )
}

@Composable
private fun XiangqiStatus(
    game: NearbyXiangqiUiState,
    localColor: XiangqiColor,
    localTurn: Boolean,
    isHost: Boolean,
    onNext: () -> Unit,
) {
    Text(
        text = when (game.outcome) {
            XiangqiOutcome.CHECKMATE -> if (game.winner == localColor) "将死，你赢了" else "将死，对方赢了"
            XiangqiOutcome.STALEMATE -> "困毙"
            XiangqiOutcome.DRAW_REPETITION -> "和棋（重复局面）"
            XiangqiOutcome.DRAW_MOVE_LIMIT -> "和棋（步数上限）"
            XiangqiOutcome.ONGOING -> if (localTurn) "轮到你走" else "等待对方落子"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (game.isGameOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (game.isGameOver) {
        Spacer(modifier = Modifier.height(8.dp))
        if (isHost) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("下一轮") }
        } else {
            Text(
                text = "等待房主开始下一轮",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
