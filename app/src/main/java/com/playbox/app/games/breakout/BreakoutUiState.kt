package com.playbox.app.games.breakout

import androidx.compose.runtime.Immutable

@Immutable
data class BreakoutUiState(
    val bricks: List<BreakoutBrick> = emptyList(),
    val ballX: Float = BREAKOUT_WORLD_WIDTH / 2f,
    val ballY: Float = BREAKOUT_PADDLE_Y - 30f,
    val paddleCenter: Float = BREAKOUT_WORLD_WIDTH / 2f,
    val score: Int = 0,
    val lives: Int = 3,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val isWon: Boolean = false,
    val isNewRecord: Boolean = false,
    val showResult: Boolean = false,
)
