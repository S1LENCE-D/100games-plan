package com.playbox.app.games

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.playbox.app.AppContainer
import com.playbox.app.domain.model.GameCatalog
import com.playbox.app.domain.model.GameId
import com.playbox.app.games.breakout.BreakoutRouteScreen
import com.playbox.app.games.ballbattle.BallBattleRouteScreen
import com.playbox.app.games.dino.DinoRouteScreen
import com.playbox.app.games.eatcat.EatcatRouteScreen
import com.playbox.app.games.game2048.Game2048RouteScreen
import com.playbox.app.games.chess.ChessRouteScreen
import com.playbox.app.games.gomoku.GomokuRouteScreen
import com.playbox.app.games.memory.MemoryRouteScreen
import com.playbox.app.games.minesweeper.MinesweeperRouteScreen
import com.playbox.app.games.tictactoe.TicTacToeRouteScreen
import com.playbox.app.games.fifteenpuzzle.FifteenPuzzleRouteScreen
import com.playbox.app.games.reaction.ReactionRouteScreen
import com.playbox.app.games.sudoku.SudokuRouteScreen
import com.playbox.app.games.snake.SnakeRouteScreen
import com.playbox.app.games.tetris.TetrisRouteScreen
import com.playbox.app.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHostScreen(
    container: AppContainer,
    gameSlug: String,
    onExit: () -> Unit,
) {
    val gameId = GameId.fromSlug(gameSlug)
    if (gameId == null || !GameRegistry.isPlayable(gameId)) {
        val name = gameId?.let(GameCatalog::findById)?.name ?: "该游戏"
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(text = "游戏") },
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回游戏大厅",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            EmptyState(
                title = "$name 即将开放",
                message = "玩法引擎正在按计划接入，先试试已经开放的游戏吧。",
                actionLabel = "返回大厅",
                onAction = onExit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
        return
    }

    when (gameId) {
        GameId.GAME_2048 -> Game2048RouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.TETRIS -> TetrisRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.SNAKE -> SnakeRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.BREAKOUT -> BreakoutRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.MINESWEEPER -> MinesweeperRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.MEMORY -> MemoryRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.SUDOKU -> SudokuRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.TIC_TAC_TOE -> TicTacToeRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.FIFTEEN_PUZZLE -> FifteenPuzzleRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.REACTION -> ReactionRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.DINO -> DinoRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.BALL_BATTLE -> BallBattleRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.GOMOKU -> GomokuRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.CHESS -> ChessRouteScreen(
            container = container,
            onExit = onExit,
        )

        GameId.EATCAT -> EatcatRouteScreen(
            container = container,
            onExit = onExit,
        )

        else -> onExit()
    }
}
