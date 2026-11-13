package com.playbox.app.games.sudoku

import androidx.compose.runtime.Immutable

@Immutable
data class SudokuUiState(
    val cells: List<SudokuCell> = emptyList(),
    val difficulty: SudokuDifficulty = SudokuDifficulty.EASY,
    val selectedRow: Int = -1,
    val selectedColumn: Int = -1,
    val notesMode: Boolean = false,
    val mistakes: Int = 0,
    val elapsedSeconds: Int = 0,
    val score: Int = 0,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false,
    val showSaveChoice: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewRecord: Boolean = false,
    val showResult: Boolean = false,
)
