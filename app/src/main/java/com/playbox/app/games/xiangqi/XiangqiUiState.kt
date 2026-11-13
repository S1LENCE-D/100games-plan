package com.playbox.app.games.xiangqi

import androidx.compose.runtime.Immutable

enum class XiangqiMode {
    AI,
    LOCAL,
}

@Immutable
data class XiangqiUiState(
    val board: List<XiangqiPiece?> = List(90) { null },
    val mode: XiangqiMode = XiangqiMode.AI,
    val humanColor: XiangqiColor = XiangqiColor.RED,
    val sideToMove: XiangqiColor = XiangqiColor.RED,
    val selected: XiangqiPoint? = null,
    val legalTargets: List<XiangqiPoint> = emptyList(),
    val lastMove: XiangqiMove? = null,
    val outcome: XiangqiOutcome = XiangqiOutcome.ONGOING,
    val winner: XiangqiColor? = null,
    val inCheck: Boolean = false,
    val isAiThinking: Boolean = false,
    val moveCount: Int = 0,
    val hasStarted: Boolean = false,
    val showResult: Boolean = false,
    val isNewRecord: Boolean = false,
    val score: Int = 0,
) {
    val isGameOver: Boolean get() = outcome != XiangqiOutcome.ONGOING
    val humanTurn: Boolean get() = mode == XiangqiMode.LOCAL || sideToMove == humanColor
}
