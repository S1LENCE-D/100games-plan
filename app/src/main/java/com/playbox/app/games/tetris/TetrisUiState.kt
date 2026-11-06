package com.playbox.app.games.tetris

import androidx.compose.runtime.Immutable

enum class TetrisDifficulty(
    val label: String,
    val startLevel: Int,
) {
    SLOW(label = "慢速", startLevel = 1),
    NORMAL(label = "标准", startLevel = 2),
    FAST(label = "快速", startLevel = 4),
    ;

    companion object {
        fun fromStartLevel(startLevel: Int): TetrisDifficulty =
            entries.minByOrNull { kotlin.math.abs(it.startLevel - startLevel) } ?: NORMAL
    }
}

@Immutable
data class TetrisUiState(
    val board: List<Int> = emptyList(),
    val activeCells: List<TetrominoCell> = emptyList(),
    val activeType: TetrominoType? = null,
    val nextType: TetrominoType? = null,
    val score: Int = 0,
    val clearedLines: Int = 0,
    val level: Int = 1,
    val difficulty: TetrisDifficulty = TetrisDifficulty.NORMAL,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewRecord: Boolean = false,
    val showResult: Boolean = false,
)
