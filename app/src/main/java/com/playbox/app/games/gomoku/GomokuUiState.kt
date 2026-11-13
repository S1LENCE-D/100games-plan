package com.playbox.app.games.gomoku

import androidx.compose.runtime.Immutable

@Immutable
data class GomokuUiState(
    val board: List<Int> = List(GOMOKU_BOARD_SIZE * GOMOKU_BOARD_SIZE) { 0 },
    val mode: GomokuMode = GomokuMode.AI,
    val humanColor: Int = GOMOKU_BLACK,
    val currentPlayer: Int = GOMOKU_BLACK,
    val winner: Int = 0,
    val winningLine: List<GomokuPoint> = emptyList(),
    val lastMove: GomokuMove? = null,
    val moveCount: Int = 0,
    val isAiThinking: Boolean = false,
    val isDraw: Boolean = false,
    val hasStarted: Boolean = false,
    val showResult: Boolean = false,
    val isNewRecord: Boolean = false,
    val score: Int = 0,
) {
    val isGameOver: Boolean get() = winner != 0 || isDraw
    val humanTurn: Boolean
        get() = mode == GomokuMode.LOCAL || currentPlayer == humanColor
}
