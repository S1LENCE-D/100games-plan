package com.playbox.app.games.tictactoe

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

class TicTacToeViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.TIC_TAC_TOE,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var aiLevel = TicTacToeAi.EASY
    private var engine = TicTacToeEngine(aiLevel)
    private var aiJob: Job? = null
    private var finishHandled = false

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<TicTacToeUiState> = _uiState.asStateFlow()

    fun selectAiLevel(level: TicTacToeAi) {
        if (_uiState.value.hasStarted) return
        aiLevel = level
        engine = TicTacToeEngine(level)
        _uiState.value = previewState()
    }

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        aiJob?.cancel()
        engine = TicTacToeEngine(aiLevel)
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        markGameOpenedOnce()
    }

    fun play(index: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isAiThinking || state.isGameOver || state.showResult) return
        if (!engine.playPlayer(index)) return
        FeedbackController.tap()
        syncState()
        if (engine.isGameOver) {
            finishRound()
            return
        }
        _uiState.update { it.copy(isAiThinking = true) }
        aiJob = viewModelScope.launch {
            delay(280L)
            engine.playAi()
            syncState()
            if (engine.isGameOver) finishRound()
        }
    }

    fun restart() {
        aiJob?.cancel()
        engine = TicTacToeEngine(aiLevel)
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
    }

    private fun finishRound() {
        if (finishHandled) return
        finishHandled = true
        aiJob?.cancel()
        val score = when {
            engine.winner == TicTacToeEngine.AI -> 0
            engine.winner == TicTacToeEngine.PLAYER -> 100
            else -> 40
        }
        _uiState.update {
            it.copy(
                score = score,
                isGameOver = true,
                isAiThinking = false,
                winner = engine.winner,
                isDraw = engine.isDraw,
            )
        }
        if (score >= 100) FeedbackController.win() else FeedbackController.gameOver()
        viewModelScope.launch {
            val isNewRecord = persistFinishedGame(score)
            _uiState.update { state -> state.copy(isNewRecord = isNewRecord, showResult = true) }
        }
    }

    private fun syncState() {
        _uiState.update {
            it.copy(
                board = (0..8).map(engine::cellAt),
                winner = engine.winner,
                isDraw = engine.isDraw,
                isGameOver = engine.isGameOver,
                isAiThinking = false,
            )
        }
    }

    private fun previewState(): TicTacToeUiState = TicTacToeUiState(
        board = (0..8).map { 0 },
        aiLevel = aiLevel,
    )

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TicTacToeViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
