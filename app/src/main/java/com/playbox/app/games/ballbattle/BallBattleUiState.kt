package com.playbox.app.games.ballbattle

import androidx.compose.runtime.Immutable

enum class BallBattlePhase {
    BUILD,
    BATTLE,
    RESULT,
}

@Immutable
data class BallBattleUiState(
    val phase: BallBattlePhase = BallBattlePhase.BUILD,
    val build: BallBuild = BallBuild(),
    val playerStats: BallStats = BallBuild().stats,
    val opponentBuild: BallBuild = BallBuild(),
    val opponentStats: BallStats = BallBuild().stats,
    val snapshot: BallBattleSnapshot? = null,
    val score: Int = 0,
    val winner: Int? = null,
    val isPaused: Boolean = false,
    val showResult: Boolean = false,
    val isNewRecord: Boolean = false,
) {
    val remainingPoints: Int get() = build.remaining
    val isGameOver: Boolean get() = phase == BallBattlePhase.RESULT
}
