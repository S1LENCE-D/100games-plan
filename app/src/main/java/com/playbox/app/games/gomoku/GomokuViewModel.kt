package com.playbox.app.games.gomoku

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

class GomokuViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.GOMOKU,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = GomokuEngine()
    private var aiJob: Job? = null
    private var finishHandled = false

    private val _uiState = MutableStateFlow(GomokuUiState())
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
                val isNewRecord = if (score > 0) persistFinishedGame(score) else false
                _uiState.update { it.copy(showResult = true, isNewRecord = isNewRecord) }
            }
        } else {
            _uiState.update { it.copy(showResult = true) }
        }
    }

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    GomokuViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
