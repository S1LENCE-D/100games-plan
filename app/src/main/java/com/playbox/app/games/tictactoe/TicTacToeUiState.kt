package com.playbox.app.games.tictactoe

import androidx.compose.runtime.Immutable

@Immutable
data class TicTacToeUiState(
    val board: List<Int> = List(9) { 0 },
    val aiLevel: TicTacToeAi = TicTacToeAi.EASY,
    val winner: Int = 0,
    val isDraw: Boolean = false,
    val isGameOver: Boolean = false,
    val isAiThinking: Boolean = false,
    val score: Int = 0,
    val hasStarted: Boolean = false,
    val showResult: Boolean = false,
    val isNewRecord: Boolean = false,
)
