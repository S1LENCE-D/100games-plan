package com.playbox.app.games.uno

import androidx.compose.runtime.Immutable

@Immutable
data class UnoUiState(
    val hand: List<UnoCard> = emptyList(),
    val counts: List<Int> = emptyList(),
    val discard: UnoCard? = null,
    val drawPileSize: Int = 0,
    val currentColor: UnoColor = UnoColor.WILD,
    val currentPlayer: Int = 0,
    val direction: Int = 1,
    val hasDrawnThisTurn: Boolean = false,
    val unoDeclared: Boolean = false,
    val winner: Int? = null,
    val playableIndices: Set<Int> = emptySet(),
    val pendingWildIndex: Int? = null,
    val lastAction: String = "",
    val hasStarted: Boolean = false,
    val isAiThinking: Boolean = false,
    val showResult: Boolean = false,
    val isNewRecord: Boolean = false,
    val score: Int = 0,
) {
    val isGameOver: Boolean get() = winner != null
    val humanTurn: Boolean get() = currentPlayer == 0 && winner == null
}
