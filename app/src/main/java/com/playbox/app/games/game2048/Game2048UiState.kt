package com.playbox.app.games.game2048

import androidx.compose.runtime.Immutable

@Immutable
data class Game2048UiState(
    val tiles: List<Tile2048> = emptyList(),
    val score: Int = 0,
    val largestTile: Int = 0,
    val hasStarted: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val canUndo: Boolean = false,
    val isNewRecord: Boolean = false,
    val showResult: Boolean = false,
    val animation: MoveAnimation? = null,
)
