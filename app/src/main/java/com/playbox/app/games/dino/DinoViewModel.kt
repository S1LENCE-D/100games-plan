package com.playbox.app.games.dino

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

class DinoViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.DINO,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = DinoEngine()
    private var finishHandled = false

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<DinoUiState> = _uiState.asStateFlow()

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        engine = DinoEngine()
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        markGameOpenedOnce()
        startLoop()
    }

    fun jump() {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver || state.showResult) return
        if (engine.jump()) {
            FeedbackController.move()
            syncState()
        }
    }

    fun togglePause() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver) return
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
        if (!state.hasStarted) return
        if (!state.isPaused && !state.isGameOver) {
            stopGameLoop()
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    fun restart() {
        stopGameLoop()
        engine = DinoEngine()
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        startLoop()
    }

    private fun startLoop() {
        startGameLoop(
            intervalMillis = { 16L },
            onTick = {
                val state = _uiState.value
                if (state.hasStarted && !state.isPaused && !state.isGameOver) {
                    engine.update(0.016f)
                    syncState()
                    if (engine.isGameOver) finishGame()
                }
            },
        )
    }

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        stopGameLoop()
        val score = engine.score
        _uiState.update {
            it.copy(
                isGameOver = true,
                isPaused = false,
                score = score,
            )
        }
        FeedbackController.gameOver()
        viewModelScope.launch {
            val isNewRecord = persistFinishedGame(score)
            _uiState.update { state ->
                state.copy(isNewRecord = isNewRecord, showResult = true)
            }
        }
    }

    private fun syncState() {
        _uiState.update {
            it.copy(
                dinoY = engine.dinoY,
                isJumping = engine.isJumping,
                obstacles = engine.obstacles,
                speed = engine.speed,
                score = engine.score,
                isGameOver = engine.isGameOver,
            )
        }
    }

    private fun previewState(): DinoUiState = DinoUiState(
        dinoY = engine.dinoY,
        obstacles = engine.obstacles,
        speed = engine.speed,
    )

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DinoViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
