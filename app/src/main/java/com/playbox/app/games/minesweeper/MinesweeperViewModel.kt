package com.playbox.app.games.minesweeper

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

class MinesweeperViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.MINESWEEPER,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var difficulty = MinesweeperDifficulty.EASY
    private var engine = MinesweeperEngine.newGame(difficulty)
    private var elapsedSeconds = 0
    private var finishHandled = false

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<MinesweeperUiState> = _uiState.asStateFlow()

    fun selectDifficulty(selected: MinesweeperDifficulty) {
        if (_uiState.value.hasStarted) return
        difficulty = selected
        engine = MinesweeperEngine.newGame(selected)
        elapsedSeconds = 0
        _uiState.value = previewState()
    }

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        engine = MinesweeperEngine.newGame(difficulty)
        elapsedSeconds = 0
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        markGameOpenedOnce()
        startTimer()
    }

    fun reveal(row: Int, column: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver || state.showResult) return
        when (engine.reveal(row, column)) {
            RevealOutcome.IGNORED -> Unit
            RevealOutcome.REVEALED -> {
                FeedbackController.tap()
                syncState()
            }
            RevealOutcome.MINE -> {
                syncState()
                finishGame(won = false)
            }
            RevealOutcome.WIN -> {
                syncState()
                finishGame(won = true)
            }
        }
    }

    fun toggleFlag(row: Int, column: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver || state.showResult) return
        if (engine.toggleFlag(row, column)) {
            FeedbackController.tap()
            syncState()
        }
    }

    fun togglePause() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver || state.showResult) return
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
        engine = MinesweeperEngine.newGame(difficulty)
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
                if (!state.isPaused && !state.isGameOver && !state.showResult) {
                    elapsedSeconds += 1
                    _uiState.update { it.copy(elapsedSeconds = elapsedSeconds) }
                }
            },
        )
    }

    private fun finishGame(won: Boolean) {
        if (finishHandled) return
        finishHandled = true
        stopGameLoop()
        val finalScore = if (won) {
            (difficulty.baseScore - elapsedSeconds * 5).coerceAtLeast(1)
        } else {
            0
        }
        _uiState.update {
            it.copy(
                isPaused = false,
                isGameOver = true,
                isWon = won,
                score = finalScore,
            )
        }
        if (won) FeedbackController.win() else FeedbackController.gameOver()
        viewModelScope.launch {
            val isNewRecord = if (won) {
                persistFinishedGame(finalScore)
            } else {
                runCatching { persistFinishedGame(0) }.getOrDefault(false)
            }
            _uiState.update {
                it.copy(isNewRecord = isNewRecord, showResult = true)
            }
        }
    }

    private fun syncState() {
        _uiState.update {
            it.copy(
                cells = engine.currentCells,
                minesRemaining = engine.minesRemaining,
                elapsedSeconds = elapsedSeconds,
                isGameOver = engine.isGameOver,
                isWon = engine.isWon,
            )
        }
    }

    private fun previewState(): MinesweeperUiState = MinesweeperUiState(
        cells = engine.currentCells,
        difficulty = difficulty,
        minesRemaining = engine.minesRemaining,
    )

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MinesweeperViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
