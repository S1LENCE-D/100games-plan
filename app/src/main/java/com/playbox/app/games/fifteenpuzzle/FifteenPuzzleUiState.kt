package com.playbox.app.games.fifteenpuzzle

import androidx.compose.runtime.Immutable

@Immutable
data class FifteenPuzzleUiState(
    val cells: List<Int> = (1..15).toList() + 0,
    val moves: Int = 0,
    val elapsedSeconds: Int = 0,
    val score: Int = 0,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewRecord: Boolean = false,
    val showResult: Boolean = false,
)
