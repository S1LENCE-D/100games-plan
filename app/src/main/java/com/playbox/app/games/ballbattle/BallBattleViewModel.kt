package com.playbox.app.games.ballbattle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playbox.app.domain.model.GameId
import com.playbox.app.domain.repository.GameStatsRepository
import com.playbox.app.feedback.FeedbackController
import com.playbox.app.games.common.BaseGameViewModel
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BallBattleViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.BALL_BATTLE,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine: BallBattleEngine? = null
    private var finishHandled = false
    private var lastPulse = false

    private val _uiState = MutableStateFlow(BallBattleUiState())
    val uiState: StateFlow<BallBattleUiState> = _uiState.asStateFlow()

    fun addPoint(attribute: BallAttribute) {
        val state = _uiState.value
        if (state.phase != BallBattlePhase.BUILD) return
        val next = state.build.withPoint(attribute, 1)
        if (next == state.build) return
        FeedbackController.tap()
        _uiState.update {
            it.copy(
                build = next,
                playerStats = next.stats,
                score = 0,
                winner = null,
                showResult = false,
            )
        }
    }

    fun removePoint(attribute: BallAttribute) {
        val state = _uiState.value
        if (state.phase != BallBattlePhase.BUILD) return
        val next = state.build.withPoint(attribute, -1)
        if (next == state.build) return
        FeedbackController.tap()
        _uiState.update {
            it.copy(
                build = next,
                playerStats = next.stats,
                score = 0,
                winner = null,
                showResult = false,
            )
        }
    }

    fun randomizeBuild() {
        val state = _uiState.value
        if (state.phase != BallBattlePhase.BUILD) return
        val build = BallBuild.random(Random.Default)
        FeedbackController.tap()
        _uiState.update {
            it.copy(
                build = build,
                playerStats = build.stats,
                score = 0,
                winner = null,
                showResult = false,
            )
        }
    }

    fun resetBuild() {
        val state = _uiState.value
        if (state.phase != BallBattlePhase.BUILD) return
        FeedbackController.tap()
        _uiState.update {
            it.copy(
                build = BallBuild(),
                playerStats = BallBuild().stats,
                score = 0,
                winner = null,
                showResult = false,
            )
        }
    }

    fun startBattle() {
        val state = _uiState.value
        if (state.phase == BallBattlePhase.BATTLE) return
        if (state.build.remaining != 0) return
        val opponentBuild = BallBuild.random(Random.Default)
        engine = BallBattleEngine(
            playerBuild = state.build,
            opponentBuild = opponentBuild,
            random = Random.Default,
        )
        finishHandled = false
        lastPulse = false
        val snapshot = engine?.snapshot()
        _uiState.update {
            it.copy(
                phase = BallBattlePhase.BATTLE,
                opponentBuild = opponentBuild,
                opponentStats = opponentBuild.stats,
                snapshot = snapshot,
                score = 0,
                winner = null,
                isPaused = false,
                showResult = false,
                isNewRecord = false,
            )
        }
        markGameOpenedOnce()
        startLoop()
    }

    fun openBuild() {
        stopGameLoop()
        finishHandled = false
        engine = null
        _uiState.update {
            it.copy(
                phase = BallBattlePhase.BUILD,
                snapshot = null,
                score = 0,
                winner = null,
                isPaused = false,
                showResult = false,
                isNewRecord = false,
            )
        }
    }

    fun rematch() {
        stopGameLoop()
        startBattle()
    }

    fun togglePause() {
        val state = _uiState.value
        if (state.phase != BallBattlePhase.BATTLE || state.showResult) return
        if (state.isPaused) {
            _uiState.update { it.copy(isPaused = false) }
            startLoop()
        } else {
            stopGameLoop()
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    fun onLifecyclePause() {
        val state = _uiState.value
        if (state.phase != BallBattlePhase.BATTLE || state.isPaused || state.isGameOver) return
        stopGameLoop()
        _uiState.update { it.copy(isPaused = true) }
    }

    private fun startLoop() {
        startGameLoop(
            intervalMillis = { 16L },
            onTick = {
                val state = _uiState.value
                val battleEngine = engine
                if (battleEngine != null && !state.isPaused && state.phase == BallBattlePhase.BATTLE) {
                    battleEngine.step(16L)
                    val snapshot = battleEngine.snapshot()
                    val pulse = snapshot.collisionPulse
                    if (pulse && !lastPulse) FeedbackController.tap()
                    lastPulse = pulse
                    _uiState.update {
                        it.copy(
                            snapshot = snapshot,
                            winner = snapshot.winner,
                            score = if (snapshot.isFinished) battleEngine.currentScore else 0,
                        )
                    }
                    if (snapshot.isFinished) finishGame()
                }
            },
        )
    }

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        stopGameLoop()
        val finalScore = engine?.currentScore ?: 0
        val winner = engine?.snapshot()?.winner
        _uiState.update {
            it.copy(
                phase = BallBattlePhase.RESULT,
                isPaused = false,
                winner = winner,
                score = finalScore,
            )
        }
        viewModelScope.launch {
            val isNewRecord = persistFinishedGame(finalScore)
            _uiState.update { state ->
                state.copy(isNewRecord = isNewRecord, showResult = true)
            }
        }
    }

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    BallBattleViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
