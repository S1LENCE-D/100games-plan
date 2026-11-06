package com.playbox.app.games.memory

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MemoryViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.MEMORY,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var difficulty = MemoryDifficulty.EASY
    private var engine = MemoryEngine.newGame(difficulty = difficulty)
    private var elapsedSeconds = 0
    private var finishHandled = false
    private var hideJob: Job? = null

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    fun selectDifficulty(selected: MemoryDifficulty) {
        if (_uiState.value.hasStarted) return
        difficulty = selected
        engine = MemoryEngine.newGame(difficulty = difficulty)
        elapsedSeconds = 0
        _uiState.value = previewState()
    }

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        hideJob?.cancel()
        engine = MemoryEngine.newGame(difficulty = difficulty)
        elapsedSeconds = 0
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        markGameOpenedOnce()
        startTimer()
    }

    fun flip(index: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver || state.showResult) return
        when (engine.flip(index)) {
            MemoryFlipResult.IGNORED -> Unit
            MemoryFlipResult.FIRST -> {
                FeedbackController.tap()
                syncState()
            }
            MemoryFlipResult.MATCH -> {
                FeedbackController.merge()
                syncState()
            }
            MemoryFlipResult.MISMATCH -> {
                FeedbackController.error()
                syncState()
                scheduleMismatchHide()
            }
            MemoryFlipResult.WIN -> {
                FeedbackController.win()
                syncState()
                finishGame()
            }
        }
    }

    fun togglePause() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver || state.showResult) return
        if (state.isPaused) {
            _uiState.update { it.copy(isPaused = false) }
            startTimer()
            if (engine.isWaitingForHide) scheduleMismatchHide()
        } else {
            stopGameLoop()
            hideJob?.cancel()
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    fun onLifecyclePause() {
        val state = _uiState.value
        if (!state.hasStarted) return
        if (!state.isPaused && !state.isGameOver) {
            stopGameLoop()
            hideJob?.cancel()
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    fun restart() {
        stopGameLoop()
        hideJob?.cancel()
        engine = MemoryEngine.newGame(difficulty = difficulty)
        elapsedSeconds = 0
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        startTimer()
    }

    private fun scheduleMismatchHide() {
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(780L)
            engine.clearMismatch()
            syncState()
        }
    }

    private fun startTimer() {
        startGameLoop(
            intervalMillis = { 1000L },
            onTick = {
                val state = _uiState.value
                if (!state.isPaused && !state.isGameOver && !state.showResult) {
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
        val finalScore = (difficulty.baseScore - engine.currentMoves * 12 - elapsedSeconds * 3).coerceAtLeast(1)
        _uiState.update {
            it.copy(
                isPaused = false,
                isGameOver = true,
                score = finalScore,
            )
        }
        viewModelScope.launch {
            val isNewRecord = persistFinishedGame(finalScore)
            _uiState.update { it.copy(isNewRecord = isNewRecord, showResult = true) }
        }
    }

    private fun syncState() {
        _uiState.update {
            it.copy(
                cards = engine.currentCards,
                moves = engine.currentMoves,
                matchedPairs = engine.currentMatchedPairs,
                elapsedSeconds = elapsedSeconds,
                isGameOver = engine.isGameOver,
            )
        }
    }

    private fun previewState(): MemoryUiState = MemoryUiState(
        cards = engine.currentCards,
        difficulty = difficulty,
    )

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MemoryViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
