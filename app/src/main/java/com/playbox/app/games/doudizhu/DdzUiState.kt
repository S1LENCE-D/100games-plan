package com.playbox.app.games.doudizhu

import androidx.compose.runtime.Immutable

@Immutable
data class DdzUiState(
    val phase: DdzPhase = DdzPhase.BIDDING,
    val hand: List<DdzCard> = emptyList(),
    val counts: List<Int> = emptyList(),
    val bottomCards: List<DdzCard> = emptyList(),
    val bottomRevealed: Boolean = false,
    val currentPlayer: Int = 0,
    val currentBidder: Int = 0,
    val highestBid: Int = 0,
    val highestBidder: Int = -1,
    val landlord: Int = -1,
    val lastPlay: DdzPlay? = null,
    val passCount: Int = 0,
    val multiplier: Int = 1,
    val lastAction: String = "",
    val winner: Int? = null,
    val landlordWon: Boolean? = null,
    val spring: Boolean = false,
    val antiSpring: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val selectedCombo: DdzCombo? = null,
    val message: String = "",
    val hasStarted: Boolean = false,
    val isAiThinking: Boolean = false,
    val showResult: Boolean = false,
    val isNewRecord: Boolean = false,
    val score: Int = 0,
) {
    val isGameOver: Boolean get() = phase == DdzPhase.FINISHED
    val humanTurn: Boolean get() = phase == DdzPhase.PLAYING && currentPlayer == 0
    val canPass: Boolean get() = humanTurn && lastPlay != null
    val humanRole: String get() = when {
        landlord < 0 -> "未定"
        landlord == 0 -> "地主"
        else -> "农民"
    }
}
