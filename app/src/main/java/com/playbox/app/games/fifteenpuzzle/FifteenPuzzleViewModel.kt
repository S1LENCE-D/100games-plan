package com.playbox.app.games.fifteenpuzzle

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

class FifteenPuzzleViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.FIFTEEN_PUZZLE,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = FifteenPuzzleEngine.newGame()
    private var elapsedSeconds = 0
    private var finishHandled = false

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<FifteenPuzzleUiState> = _uiState.asStateFlow()

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        engine = FifteenPuzzleEngine.newGame()
        elapsedSeconds = 0
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        markGameOpenedOnce()
        startTimer()
    }

    fun move(index: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver) return
        if (engine.slideTo(index) <= 0) return
        afterMove()
    }

    fun swipe(dx: Int, dy: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver) return
        if (engine.swipe(dx, dy) <= 0) return
        afterMove()
    }

    private fun afterMove() {
        FeedbackController.tap()
        syncState()
        if (engine.isSolved()) finishGame()
    }

    fun togglePause() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver) return
        if (state.isPaused) {
            _uiState.update { it.copy(isPaused = false) }
            startTimer()
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
        engine = FifteenPuzzleEngine.newGame()
        elapsedSeconds = 0
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        startTimer()
    }

    private fun startTimer() {
        startGameLoop(
            intervalMillis = { 1000L },
            onTick = {
                val state = _uiState.value
                if (!state.isPaused && !state.isGameOver) {
                    elapsedSeconds += 1
                    _uiState.update { it.copy(elapsedSeconds = elapsedSeconds) }
                }
            },
        )
    }

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        stopGameLoop()
        val finalScore = (2000 - engine.currentMoves() * 5 - elapsedSeconds * 2).coerceAtLeast(1)
        _uiState.update { it.copy(isGameOver = true, isPaused = false, score = finalScore) }
        FeedbackController.win()
        viewModelScope.launch {
            val isNewRecord = persistFinishedGame(finalScore)
            _uiState.update { it.copy(isNewRecord = isNewRecord, showResult = true) }
        }
    }

    private fun syncState() {
        _uiState.update {
            it.copy(
                cells = (0..15).map(engine::cellAt),
                moves = engine.currentMoves(),
                elapsedSeconds = elapsedSeconds,
                isGameOver = engine.isSolved(),
            )
        }
    }

    private fun previewState(): FifteenPuzzleUiState = FifteenPuzzleUiState(
        cells = (0..15).map(engine::cellAt),
    )

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    FifteenPuzzleViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
