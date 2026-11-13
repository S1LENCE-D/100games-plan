package com.playbox.app.games.xiangqi

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XiangqiViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.XIANGQI,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = XiangqiEngine()
    private var aiJob: Job? = null
    private var finishHandled = false

    private val _uiState = MutableStateFlow(XiangqiUiState())
    val uiState: StateFlow<XiangqiUiState> = _uiState.asStateFlow()

    fun selectMode(mode: XiangqiMode) {
        if (_uiState.value.hasStarted) return
        _uiState.update { it.copy(mode = mode) }
    }

    fun selectHumanColor(color: XiangqiColor) {
        if (_uiState.value.hasStarted) return
        _uiState.update { it.copy(humanColor = color) }
    }

    fun startGame() {
        aiJob?.cancel()
        engine = XiangqiEngine()
        finishHandled = false
        val mode = _uiState.value.mode
        val humanColor = _uiState.value.humanColor
        _uiState.value = XiangqiUiState(
            board = engine.boardList(),
            mode = mode,
            humanColor = humanColor,
            sideToMove = engine.sideToMove,
            outcome = engine.status.outcome,
            winner = engine.status.winner,
            inCheck = engine.status.inCheck,
            hasStarted = true,
        )
        markGameOpenedOnce()
        if (mode == XiangqiMode.AI && humanColor == XiangqiColor.BLACK) {
            triggerAi()
        }
    }

    fun tapPoint(point: XiangqiPoint) {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver || state.showResult || state.isAiThinking) return
        if (state.mode == XiangqiMode.AI && engine.sideToMove != state.humanColor) return
        val piece = engine.pieceAt(point.row, point.column)
        val selected = state.selected
        if (selected == null) {
            if (piece != null && piece.color == engine.sideToMove) selectPoint(point)
            return
        }
        if (selected == point) {
            clearSelection()
            return
        }
        if (piece != null && piece.color == engine.sideToMove) {
            selectPoint(point)
            return
        }
        val move = engine.legalMovesFrom(selected).firstOrNull { it.to == point }
        if (move == null) {
            clearSelection()
        } else {
            playMove(move)
        }
    }

    fun undo() {
        val state = _uiState.value
        if (!state.hasStarted || state.showResult || state.isAiThinking || state.moveCount == 0) return
        aiJob?.cancel()
        if (state.mode == XiangqiMode.LOCAL) {
            engine.undo()
        } else {
            var guard = 0
            while (engine.moveCount > 0 && guard < 2) {
                engine.undo()
                guard += 1
                if (engine.sideToMove == state.humanColor) break
            }
        }
        FeedbackController.tap()
        clearSelection()
        syncState()
        if (_uiState.value.mode == XiangqiMode.AI &&
            _uiState.value.sideToMove != _uiState.value.humanColor &&
            !_uiState.value.isGameOver
        ) {
            triggerAi()
        }
    }

    fun restart() {
        aiJob?.cancel()
        startGame()
    }

    private fun selectPoint(point: XiangqiPoint) {
        val moves = engine.legalMovesFrom(point)
        _uiState.update {
            it.copy(
                selected = point,
                legalTargets = moves.map { move -> move.to }.distinct(),
            )
        }
        FeedbackController.tap()
    }

    private fun clearSelection() {
        _uiState.update { it.copy(selected = null, legalTargets = emptyList()) }
    }

    private fun playMove(move: XiangqiMove) {
        if (!engine.makeMove(move)) return
        FeedbackController.drop()
        syncState()
        val state = _uiState.value
        if (state.isGameOver) {
            finishGame()
        } else if (state.mode == XiangqiMode.AI && state.sideToMove != state.humanColor) {
            triggerAi()
        }
    }

    private fun triggerAi() {
        val state = _uiState.value
        if (state.mode != XiangqiMode.AI || state.sideToMove == state.humanColor) return
        aiJob?.cancel()
        _uiState.update {
            it.copy(
                isAiThinking = true,
                selected = null,
                legalTargets = emptyList(),
            )
        }
        aiJob = viewModelScope.launch {
            delay(180)
            val move = withContext(Dispatchers.Default) { engine.chooseAiMove(3) }
            if (_uiState.value.isGameOver) {
                _uiState.update { it.copy(isAiThinking = false) }
                return@launch
            }
            if (move != null && engine.makeMove(move)) {
                FeedbackController.drop()
                syncState()
            }
            _uiState.update { it.copy(isAiThinking = false) }
            if (_uiState.value.isGameOver) finishGame()
        }
    }

    private fun syncState() {
        _uiState.update {
            it.copy(
                board = engine.boardList(),
                sideToMove = engine.sideToMove,
                lastMove = engine.lastMove,
                outcome = engine.status.outcome,
                winner = engine.status.winner,
                inCheck = engine.status.inCheck,
                moveCount = engine.moveCount,
                selected = null,
                legalTargets = emptyList(),
            )
        }
    }

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        aiJob?.cancel()
        val state = _uiState.value
        val score = when {
            state.mode != XiangqiMode.AI -> 0
            state.winner == state.humanColor -> (1000 - state.moveCount * 5).coerceAtLeast(100)
            state.winner == null -> 300
            else -> 0
        }
        _uiState.update { it.copy(score = score, isAiThinking = false) }
        if (state.mode == XiangqiMode.AI) {
            viewModelScope.launch {
                val isNewRecord = if (score > 0) persistFinishedGame(score) else false
                _uiState.update { it.copy(showResult = true, isNewRecord = isNewRecord) }
            }
        } else {
            _uiState.update { it.copy(showResult = true) }
        }
    }

    private fun XiangqiEngine.boardList(): List<XiangqiPiece?> =
        List(90) { index -> pieceAt(index / 9, index % 9) }

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    XiangqiViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
