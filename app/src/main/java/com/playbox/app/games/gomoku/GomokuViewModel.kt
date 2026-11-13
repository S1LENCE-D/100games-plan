package com.playbox.app.games.gomoku

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class SavedGomoku(
    val snapshot: GomokuSnapshot,
    val mode: GomokuMode,
    val humanColor: Int,
)

class GomokuViewModel(
    statsRepository: GameStatsRepository,
    private val saveRepository: GameSaveRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.GOMOKU,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = GomokuEngine()
    private var aiJob: Job? = null
    private var finishHandled = false
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(GomokuUiState())

    init {
        viewModelScope.launch { restorePersistentSave() }
    }
    val uiState: StateFlow<GomokuUiState> = _uiState.asStateFlow()

    fun selectMode(mode: GomokuMode) {
        if (_uiState.value.hasStarted) return
        _uiState.update { it.copy(mode = mode) }
    }

    fun selectHumanColor(color: Int) {
        if (_uiState.value.hasStarted) return
        _uiState.update { it.copy(humanColor = color) }
    }

    fun startGame() {
        aiJob?.cancel()
        engine = GomokuEngine()
        finishHandled = false
        val state = _uiState.value
        _uiState.value = GomokuUiState(
            board = engine.board,
            mode = state.mode,
            humanColor = state.humanColor,
            currentPlayer = engine.currentPlayer,
            hasStarted = true,
        )
        markGameOpenedOnce()
        clearPersistentSave()
        if (state.mode == GomokuMode.AI && state.humanColor == GOMOKU_WHITE) {
            triggerAi()
        }
    }

    fun tapCell(row: Int, column: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver || state.showResult || state.isAiThinking) return
        if (state.mode == GomokuMode.AI && state.currentPlayer != state.humanColor) return
        if (!engine.place(row, column)) return
        FeedbackController.drop()
        syncState()
        val after = _uiState.value
        if (after.isGameOver) {
            finishGame()
        } else if (after.mode == GomokuMode.AI && after.currentPlayer != after.humanColor) {
            triggerAi()
        }
    }

    fun undo() {
        val state = _uiState.value
        if (!state.hasStarted || state.showResult || state.moveCount == 0) return
        aiJob?.cancel()
        _uiState.update { it.copy(isAiThinking = false) }
        if (state.mode == GomokuMode.AI) {
            if (state.lastMove?.player == state.humanColor) {
                engine.undo()
            } else {
                engine.undo()
                if (engine.moveCount > 0) engine.undo()
            }
        } else {
            engine.undo()
        }
        FeedbackController.tap()
        syncState()
        _uiState.update { it.copy(showResult = false) }
        val after = _uiState.value
        if (after.mode == GomokuMode.AI && after.currentPlayer != after.humanColor && !after.isGameOver) {
            triggerAi()
        }
    }

    fun restart() {
        aiJob?.cancel()
        startGame()
    }

    fun saveGame() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver || state.isAiThinking) return
        val saved = SavedGomoku(
            snapshot = engine.snapshot(),
            mode = state.mode,
            humanColor = state.humanColor,
        )
        viewModelScope.launch {
            runCatching { saveRepository.save(gameId, json.encodeToString(saved)) }
                .onFailure { throwable ->
                    com.playbox.app.diagnostics.AppLogger.warn("Save", "保存五子棋进度失败", throwable)
                }
        }
    }

    private suspend fun restorePersistentSave() {
        if (_uiState.value.hasStarted) return
        val raw = runCatching { saveRepository.load(gameId) }.getOrNull() ?: return
        val saved = runCatching { json.decodeFromString<SavedGomoku>(raw) }.getOrNull() ?: return
        if (saved.snapshot.winner != 0) {
            runCatching { saveRepository.clear(gameId) }
            return
        }
        engine = GomokuEngine.restore(saved.snapshot)
        _uiState.value = GomokuUiState(
            board = engine.board,
            mode = saved.mode,
            humanColor = saved.humanColor,
            currentPlayer = engine.currentPlayer,
            winner = engine.winner,
            winningLine = engine.winningLine,
            lastMove = engine.lastMove,
            moveCount = engine.moveCount,
            hasStarted = true,
        )
        if (saved.mode == GomokuMode.AI && engine.currentPlayer != saved.humanColor) {
            triggerAi()
        }
    }

    private fun clearPersistentSave() {
        viewModelScope.launch { runCatching { saveRepository.clear(gameId) } }
    }

    private fun triggerAi() {
        if (_uiState.value.mode != GomokuMode.AI) return
        aiJob?.cancel()
        _uiState.update { it.copy(isAiThinking = true) }
        aiJob = viewModelScope.launch {
            delay(260)
            if (_uiState.value.currentPlayer == _uiState.value.humanColor) {
                _uiState.update { it.copy(isAiThinking = false) }
                return@launch
            }
            engine.aiMove()
            FeedbackController.drop()
            _uiState.update { it.copy(isAiThinking = false) }
            syncState()
            if (_uiState.value.isGameOver) finishGame()
        }
    }

    private fun syncState() {
        _uiState.update {
            it.copy(
                board = engine.board,
                currentPlayer = engine.currentPlayer,
                winner = engine.winner,
                winningLine = engine.winningLine,
                lastMove = engine.lastMove,
                moveCount = engine.moveCount,
                isDraw = engine.isDraw,
            )
        }
    }

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        aiJob?.cancel()
        val state = _uiState.value
        val humanWon = state.mode == GomokuMode.AI && state.winner == state.humanColor
        val score = when {
            state.mode != GomokuMode.AI -> 0
            humanWon -> (1000 - state.moveCount * 8).coerceAtLeast(100)
            state.isDraw -> 300
            else -> 0
        }
        _uiState.update { it.copy(score = score, isAiThinking = false) }
        if (state.mode == GomokuMode.AI) {
            viewModelScope.launch {
                clearPersistentSave()
                val isNewRecord = if (score > 0) persistFinishedGame(score) else false
                _uiState.update { it.copy(showResult = true, isNewRecord = isNewRecord) }
            }
        } else {
            clearPersistentSave()
            _uiState.update { it.copy(showResult = true) }
        }
    }

    companion object {
        fun factory(
            statsRepository: GameStatsRepository,
            saveRepository: GameSaveRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    GomokuViewModel(
                        statsRepository = statsRepository,
                        saveRepository = saveRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
