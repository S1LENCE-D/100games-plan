package com.playbox.app.games.reaction

import androidx.compose.runtime.Immutable

enum class ReactionPhase {
    CALIBRATION,
    WAITING,
    GO,
    TOO_SOON,
    ROUND_RESULT,
    FINISHED,
}

@Immutable
data class ReactionUiState(
    val phase: ReactionPhase = ReactionPhase.WAITING,
    val round: Int = 1,
    val totalRounds: Int = 5,
    val results: List<Long> = emptyList(),
    val lastMillis: Long? = null,
    val averageMillis: Int = 0,
    val bestMillis: Int = 0,
    val tooSoonCount: Int = 0,
    val score: Int = 0,
    val calibrationPulse: Boolean = false,
    val calibrationAccent: Boolean = false,
    val calibrationSamples: Int = 0,
    val calibrationRound: Int = 1,
    val compensationMs: Int = 0,
    val calibrationComplete: Boolean = false,
    val calibrationReused: Boolean = false,
    val calibrationSaved: Boolean = false,
    val hasStarted: Boolean = false,
    val showResult: Boolean = false,
    val isNewRecord: Boolean = false,
)
