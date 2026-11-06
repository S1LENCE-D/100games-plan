package com.playbox.app.games.snake

import androidx.compose.runtime.Immutable

@Immutable
data class SnakeUiState(
    val body: List<SnakeCell> = emptyList(),
    val food: SnakeCell? = null,
    val direction: Direction = Direction.RIGHT,
    val score: Int = 0,
    val length: Int = 0,
    val difficulty: SnakeDifficulty = SnakeDifficulty.NORMAL,
    val wrapAround: Boolean = false,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewRecord: Boolean = false,
    val showResult: Boolean = false,
)
