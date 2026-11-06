package com.playbox.app.games.sudoku

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

class SudokuViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.SUDOKU,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var difficulty = SudokuDifficulty.EASY
    private var engine = SudokuEngine.newGame(difficulty)
    private var elapsedSeconds = 0
    private var finishHandled = false

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<SudokuUiState> = _uiState.asStateFlow()

    fun selectDifficulty(selected: SudokuDifficulty) {
        if (_uiState.value.hasStarted) return
        difficulty = selected
        engine = SudokuEngine.newGame(selected)
        elapsedSeconds = 0
        _uiState.value = previewState()
    }

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        engine = SudokuEngine.newGame(difficulty)
        elapsedSeconds = 0
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        markGameOpenedOnce()
        startTimer()
    }

    fun selectCell(row: Int, column: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver) return
        if (row !in 0..8 || column !in 0..8) return
        _uiState.update { it.copy(selectedRow = row, selectedColumn = column) }
    }

    fun toggleNotes() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver) return
        FeedbackController.tap()
        _uiState.update { it.copy(notesMode = !it.notesMode) }
    }

    fun enterNumber(value: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver) return
        if (state.selectedRow !in 0..8 || state.selectedColumn !in 0..8) return
        if (state.notesMode) {
            if (engine.toggleNote(state.selectedRow, state.selectedColumn, value)) {
                FeedbackController.tap()
                syncState()
            }
            return
        }
        when (engine.setValue(state.selectedRow, state.selectedColumn, value)) {
            SudokuInputResult.IGNORED -> Unit
            SudokuInputResult.WRONG -> {
                FeedbackController.error()
                syncState()
            }
            SudokuInputResult.PLACED -> {
                FeedbackController.tap()
                syncState()
            }
            SudokuInputResult.COMPLETED -> {
                syncState()
                FeedbackController.win()
                finishGame()
            }
        }
    }

    fun eraseCell() {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver) return
        if (engine.erase(state.selectedRow, state.selectedColumn)) {
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
        engine = SudokuEngine.newGame(difficulty)
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

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        stopGameLoop()
        val finalScore = (difficulty.baseScore - elapsedSeconds * 2 - engine.currentMistakes * 40)
            .coerceAtLeast(1)
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
                cells = engine.currentCells,
                mistakes = engine.currentMistakes,
                elapsedSeconds = elapsedSeconds,
                isGameOver = engine.isCompleted,
            )
        }
    }

    private fun previewState(): SudokuUiState = SudokuUiState(
        cells = engine.currentCells,
        difficulty = difficulty,
    )

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SudokuViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
