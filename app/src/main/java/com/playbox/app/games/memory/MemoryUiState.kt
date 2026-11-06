package com.playbox.app.games.memory

import androidx.compose.runtime.Immutable

@Immutable
data class MemoryUiState(
    val cards: List<MemoryCard> = emptyList(),
    val difficulty: MemoryDifficulty = MemoryDifficulty.EASY,
    val moves: Int = 0,
    val matchedPairs: Int = 0,
    val elapsedSeconds: Int = 0,
    val score: Int = 0,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewRecord: Boolean = false,
    val showResult: Boolean = false,
)
