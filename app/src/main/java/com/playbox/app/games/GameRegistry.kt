package com.playbox.app.games

import com.playbox.app.domain.model.GameId

/**
 * 集中维护当前可玩的游戏，后续会持续扩充此集合。
 */
object GameRegistry {
    private val playableGames: Set<GameId> = setOf(
        GameId.GAME_2048,
        GameId.TETRIS,
        GameId.SNAKE,
        GameId.BREAKOUT,
        GameId.MINESWEEPER,
        GameId.MEMORY,
        GameId.SUDOKU,
        GameId.TIC_TAC_TOE,
        GameId.FIFTEEN_PUZZLE,
        GameId.REACTION,
        GameId.DINO,
        GameId.BALL_BATTLE,
        GameId.GOMOKU,
        GameId.CHESS,
        GameId.XIANGQI,
        GameId.UNO,
        GameId.EATCAT,
    )

    fun isPlayable(gameId: GameId): Boolean = gameId in playableGames
}
