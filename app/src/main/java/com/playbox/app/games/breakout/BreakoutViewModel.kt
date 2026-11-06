package com.playbox.app.games.breakout

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BreakoutViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.BREAKOUT,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = BreakoutEngine.newGame()
    private var gameOverHandled = false

    private val _uiState = MutableStateFlow(stateFromEngine(hasStarted = false))
    val uiState: StateFlow<BreakoutUiState> = _uiState.asStateFlow()

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        engine = BreakoutEngine.newGame()
        gameOverHandled = false
        _uiState.value = stateFromEngine(hasStarted = true)
        markGameOpenedOnce()
        startBreakoutLoop()
    }

    fun movePaddle(fraction: Float) {
        if (!_uiState.value.hasStarted || _uiState.value.isPaused || _uiState.value.isGameOver) return
        engine.movePaddle(fraction)
        _uiState.update { it.copy(paddleCenter = engine.currentPaddleCenter) }
    }

    fun togglePause() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver || state.showResult) return
        if (state.isPaused) {
            _uiState.update { it.copy(isPaused = false) }
            startBreakoutLoop()
        } else {
            stopGameLoop()
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    fun onLifecyclePause() {
        val state = _uiState.value
        if (!state.hasStarted) return
        if (!state.isPaused && !state.isGameOver) {
            stopGameLoop()
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    fun restart() {
        stopGameLoop()
        engine = BreakoutEngine.newGame()
        gameOverHandled = false
        _uiState.value = stateFromEngine(hasStarted = true)
        startBreakoutLoop()
    }

    private fun startBreakoutLoop() {
        startGameLoop(
            intervalMillis = { 16L },
            onTick = {
                val state = _uiState.value
                if (!state.isPaused && !state.isGameOver) {
                    val scoreBefore = engine.currentScore
                    val livesBefore = engine.currentLives
                    engine.tick(16L)
                    if (engine.currentScore > scoreBefore) FeedbackController.merge()
                    if (engine.currentLives < livesBefore) FeedbackController.error()
                    _uiState.update { current ->
                        current.copy(
                            bricks = engine.currentBricks,
                            ballX = engine.currentBallX,
                            ballY = engine.currentBallY,
                            paddleCenter = engine.currentPaddleCenter,
                            score = engine.currentScore,
                            lives = engine.currentLives,
                            isWon = engine.isWon,
                            isGameOver = engine.isGameOver,
                        )
                    }
                    if (engine.isGameOver) finishGame(engine.isWon)
                }
            },
        )
    }

    private fun finishGame(won: Boolean) {
        if (gameOverHandled) return
        gameOverHandled = true
        stopGameLoop()
        if (won) FeedbackController.win() else FeedbackController.gameOver()
        viewModelScope.launch {
            val isNewRecord = persistFinishedGame(engine.currentScore)
            _uiState.update {
                it.copy(
                    isPaused = false,
                    isWon = won,
                    isGameOver = true,
                    isNewRecord = isNewRecord,
                    showResult = true,
                )
            }
        }
    }

    private fun stateFromEngine(hasStarted: Boolean): BreakoutUiState = BreakoutUiState(
        bricks = engine.currentBricks,
        ballX = engine.currentBallX,
        ballY = engine.currentBallY,
        paddleCenter = engine.currentPaddleCenter,
        score = engine.currentScore,
        lives = engine.currentLives,
        hasStarted = hasStarted,
        isWon = engine.isWon,
        isGameOver = engine.isGameOver,
    )

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    BreakoutViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
