package com.playbox.app.games.dino

import androidx.compose.runtime.Immutable

@Immutable
data class DinoUiState(
    val dinoY: Float = 0f,
    val isJumping: Boolean = false,
    val obstacles: List<DinoObstacle> = emptyList(),
    val speed: Float = 90f,
    val score: Int = 0,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewRecord: Boolean = false,
    val showResult: Boolean = false,
)
