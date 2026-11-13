package com.playbox.app.games.sudoku

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playbox.app.domain.model.GameId
import com.playbox.app.domain.repository.GameSaveRepository
import com.playbox.app.domain.repository.GameStatsRepository
import com.playbox.app.feedback.FeedbackController
import com.playbox.app.games.common.BaseGameViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class SavedSudoku(
    val snapshot: SudokuSnapshot,
    val elapsedSeconds: Int,
    val notesMode: Boolean,
)

class SudokuViewModel(
    statsRepository: GameStatsRepository,
    private val saveRepository: GameSaveRepository,
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
    private var pendingSave: SavedSudoku? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<SudokuUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadPendingSave() }
    }

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
        clearPersistentSave()
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
        clearPersistentSave()
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
            clearPersistentSave()
            val isNewRecord = persistFinishedGame(finalScore)
            _uiState.update { it.copy(isNewRecord = isNewRecord, showResult = true) }
        }
    }

    fun saveGame() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver) return
        val saved = SavedSudoku(
            snapshot = engine.snapshot(),
            elapsedSeconds = elapsedSeconds,
            notesMode = state.notesMode,
        )
        viewModelScope.launch {
            runCatching { saveRepository.save(gameId, json.encodeToString(saved)) }
                .onFailure { throwable ->
                    com.playbox.app.diagnostics.AppLogger.warn("Save", "保存数独进度失败", throwable)
                }
        }
    }

    fun resumeSavedGame() {
        val saved = pendingSave ?: return
        pendingSave = null
        difficulty = saved.snapshot.difficulty
        engine = SudokuEngine.restore(saved.snapshot)
        elapsedSeconds = saved.elapsedSeconds
        _uiState.value = SudokuUiState(
            cells = engine.currentCells,
            difficulty = difficulty,
            notesMode = saved.notesMode,
            mistakes = engine.currentMistakes,
            elapsedSeconds = elapsedSeconds,
            hasStarted = true,
            isPaused = true,
            showSaveChoice = false,
        )
    }

    fun startNewGameFromSave() {
        pendingSave = null
        _uiState.update { it.copy(showSaveChoice = false) }
        clearPersistentSave()
    }

    fun saveAndExit(onSaved: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.hasStarted && !state.isGameOver) {
                val saved = SavedSudoku(engine.snapshot(), elapsedSeconds, state.notesMode)
                runCatching { saveRepository.save(gameId, json.encodeToString(saved)) }
            }
            onSaved()
        }
    }

    private suspend fun loadPendingSave() {
        if (_uiState.value.hasStarted) return
        val raw = runCatching { saveRepository.load(gameId) }.getOrNull() ?: return
        val saved = runCatching { json.decodeFromString<SavedSudoku>(raw) }.getOrNull() ?: return
        if (saved.snapshot.completed) {
            runCatching { saveRepository.clear(gameId) }
            return
        }
        pendingSave = saved
        _uiState.update { it.copy(showSaveChoice = true) }
    }

    private fun clearPersistentSave() {
        viewModelScope.launch { runCatching { saveRepository.clear(gameId) } }
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
        fun factory(
            statsRepository: GameStatsRepository,
            saveRepository: GameSaveRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SudokuViewModel(
                        statsRepository = statsRepository,
                        saveRepository = saveRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
