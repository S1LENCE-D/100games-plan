package com.playbox.app.games.chess

import androidx.compose.runtime.Immutable

@Immutable
data class ChessUiState(
    val board: List<ChessPiece?> = List(64) { null },
    val mode: ChessMode = ChessMode.AI,
    val humanColor: ChessColor = ChessColor.WHITE,
    val sideToMove: ChessColor = ChessColor.WHITE,
    val selected: ChessSquare? = null,
    val legalTargets: List<ChessSquare> = emptyList(),
    val lastMove: ChessMove? = null,
    val outcome: ChessOutcome = ChessOutcome.ONGOING,
    val winner: ChessColor? = null,
    val inCheck: Boolean = false,
    val pendingPromotion: List<ChessMove> = emptyList(),
    val isAiThinking: Boolean = false,
    val moveCount: Int = 0,
    val hasStarted: Boolean = false,
    val showSaveChoice: Boolean = false,
    val showResult: Boolean = false,
    val isNewRecord: Boolean = false,
    val score: Int = 0,
) {
    val isGameOver: Boolean get() = outcome != ChessOutcome.ONGOING
    val humanTurn: Boolean
        get() = mode == ChessMode.LOCAL || sideToMove == humanColor
}

enum class ChessMode {
    AI,
    LOCAL,
}
