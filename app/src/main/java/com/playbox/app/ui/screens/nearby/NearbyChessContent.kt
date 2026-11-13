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
import com.playbox.app.games.chess.ChessBoard
import com.playbox.app.games.chess.ChessColor
import com.playbox.app.games.chess.ChessMode
import com.playbox.app.games.chess.ChessUiState
import com.playbox.app.games.nearby.NearbyChessUiState
import com.playbox.app.games.nearby.NearbyUiState
import com.playbox.app.games.chess.ChessOutcome
import com.playbox.app.games.chess.ChessSquare

@Composable
internal fun NearbyChessContent(
    state: NearbyUiState,
    onSquare: (ChessSquare) -> Unit,
    onNext: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val game = state.chess
    val localColor = if (state.isHost) ChessColor.WHITE else ChessColor.BLACK
    val localTurn = !game.isGameOver && game.sideToMove == localColor
    val boardState = ChessUiState(
        board = game.board,
        mode = ChessMode.LOCAL,
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
    val boardSize = (LocalConfiguration.current.screenHeightDp.dp - 145.dp).coerceAtLeast(180.dp)

    if (landscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ChessSurface(boardState, onSquare, Modifier.size(boardSize))
            Column(modifier = Modifier.weight(1f)) {
                ChessInfo(localColor)
                ChessStatus(game, localColor, localTurn, state.isHost, onNext)
                TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("退出游戏")
                }
            }
        }
        return
    }

    ChessInfo(localColor)
    Spacer(modifier = Modifier.height(12.dp))
    ChessSurface(
        state = boardState,
        onSquare = onSquare,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    )
    Spacer(modifier = Modifier.height(10.dp))
    ChessStatus(game, localColor, localTurn, state.isHost, onNext)
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text("退出游戏")
    }
}

@Composable
private fun ChessInfo(localColor: ChessColor) {
    InfoCard(
        title = "联机国际象棋",
        message = "你执${if (localColor == ChessColor.WHITE) "白" else "黑"}，白棋先手，点击棋子再点击目标格。",
    )
}

@Composable
private fun ChessSurface(
    state: ChessUiState,
    onSquare: (ChessSquare) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ChessBoard(
            state = state,
            onSquareTap = onSquare,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ChessStatus(
    game: NearbyChessUiState,
    localColor: ChessColor,
    localTurn: Boolean,
    isHost: Boolean,
    onNext: () -> Unit,
) {
    Text(
        text = when (game.outcome) {
            ChessOutcome.CHECKMATE -> if (game.winner == localColor) "将死，你赢了" else "将死，对方赢了"
            ChessOutcome.STALEMATE -> "逼和"
            ChessOutcome.DRAW_FIFTY -> "和棋（50 回合）"
            ChessOutcome.DRAW_REPETITION -> "和棋（三次重复）"
            ChessOutcome.DRAW_MATERIAL -> "和棋（子力不足）"
            ChessOutcome.ONGOING -> when {
                localTurn -> "轮到你走"
                else -> "等待对方落子"
            }
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (game.isGameOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (game.isGameOver) {
        Spacer(modifier = Modifier.height(10.dp))
        if (isHost) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("下一轮")
            }
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
