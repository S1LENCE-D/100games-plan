package com.playbox.app.games.minesweeper

import androidx.compose.runtime.Immutable

@Immutable
data class MinesweeperUiState(
    val cells: List<MineCell> = emptyList(),
    val difficulty: MinesweeperDifficulty = MinesweeperDifficulty.EASY,
    val minesRemaining: Int = MinesweeperDifficulty.EASY.mines,
    val elapsedSeconds: Int = 0,
    val score: Int = 0,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val isWon: Boolean = false,
    val isNewRecord: Boolean = false,
    val showResult: Boolean = false,
)
