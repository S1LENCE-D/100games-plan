package com.playbox.app.games.liquidsort

import androidx.compose.runtime.Immutable

@Immutable
data class LiquidSortUiState(
    val tubes: List<List<Int>> = emptyList(),
    val selectedTube: Int? = null,
    val validTargets: Set<Int> = emptySet(),
    val message: String = "",
    val level: Int = 1,
    val colorCount: Int = 4,
    val moves: Int = 0,
    val canUndo: Boolean = false,
    val hasStarted: Boolean = false,
    val showSaveChoice: Boolean = false,
    val showResult: Boolean = false,
    val isNewRecord: Boolean = false,
    val score: Int = 0,
    val isSolved: Boolean = false,
)
