package com.playbox.app.games.chess

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

class ChessViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.CHESS,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = ChessEngine()
    private var aiJob: Job? = null
    private var finishHandled = false

    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    fun selectMode(mode: ChessMode) {
        if (_uiState.value.hasStarted) return
        _uiState.update { it.copy(mode = mode) }
    }

    fun selectHumanColor(color: ChessColor) {
        if (_uiState.value.hasStarted) return
        _uiState.update { it.copy(humanColor = color) }
    }

    fun startGame() {
        aiJob?.cancel()
        engine = ChessEngine()
        finishHandled = false
        val selectedMode = _uiState.value.mode
        val selectedColor = _uiState.value.humanColor
        _uiState.value = ChessUiState(
            board = engine.boardList(),
            mode = selectedMode,
            humanColor = selectedColor,
            sideToMove = engine.sideToMove,
            outcome = engine.status.outcome,
            winner = engine.status.winner,
            inCheck = engine.status.inCheck,
            hasStarted = true,
        )
        markGameOpenedOnce()
        if (selectedMode == ChessMode.AI && selectedColor == ChessColor.BLACK) {
            triggerAi()
        }
    }

    fun tapSquare(square: ChessSquare) {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver || state.showResult || state.isAiThinking) return
        if (state.pendingPromotion.isNotEmpty()) return
        if (state.mode == ChessMode.AI && engine.sideToMove != state.humanColor) return
        val piece = engine.pieceAt(square.row, square.column)
        val selected = state.selected
        if (selected == null) {
            if (piece != null && piece.color == engine.sideToMove) selectSquare(square)
            return
        }
        if (selected == square) {
            clearSelection()
            return
        }
        if (piece != null && piece.color == engine.sideToMove) {
            selectSquare(square)
            return
        }
        val candidates = engine.legalMovesFrom(selected)
            .filter { it.to == square }
        if (candidates.isEmpty()) {
            clearSelection()
            return
        }
        if (candidates.size > 1) {
            _uiState.update {
                it.copy(
                    pendingPromotion = candidates,
                    legalTargets = emptyList(),
                )
            }
        } else {
            playMove(candidates.first())
        }
    }

    fun choosePromotion(type: ChessPieceType) {
        val state = _uiState.value
        val move = state.pendingPromotion.firstOrNull { it.promotion == type } ?: return
        _uiState.update { it.copy(pendingPromotion = emptyList()) }
        playMove(move)
    }

    fun cancelPromotion() {
        _uiState.update { it.copy(pendingPromotion = emptyList()) }
    }

    fun undo() {
        val state = _uiState.value
        if (!state.hasStarted || state.showResult || state.isAiThinking || state.moveCount == 0) return
        aiJob?.cancel()
        if (state.mode == ChessMode.LOCAL) {
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
        if (_uiState.value.mode == ChessMode.AI &&
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

    private fun selectSquare(square: ChessSquare) {
        val moves = engine.legalMovesFrom(square)
        _uiState.update {
            it.copy(
                selected = square,
                legalTargets = moves.map { move -> move.to }.distinct(),
            )
        }
        FeedbackController.tap()
    }

    private fun clearSelection() {
        _uiState.update { it.copy(selected = null, legalTargets = emptyList()) }
    }

    private fun playMove(move: ChessMove) {
        if (!engine.makeMove(move)) return
        FeedbackController.drop()
        _uiState.update {
            it.copy(
                pendingPromotion = emptyList(),
                selected = null,
                legalTargets = emptyList(),
            )
        }
        syncState()
        val state = _uiState.value
        if (state.isGameOver) {
            finishGame()
        } else if (state.mode == ChessMode.AI && state.sideToMove != state.humanColor) {
            triggerAi()
        }
    }

    private fun triggerAi() {
        val state = _uiState.value
        if (state.mode != ChessMode.AI || state.sideToMove == state.humanColor) return
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
                pendingPromotion = emptyList(),
            )
        }
    }

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        aiJob?.cancel()
        val state = _uiState.value
        val score = when {
            state.mode != ChessMode.AI -> 0
            state.winner == state.humanColor -> (1000 - state.moveCount * 6).coerceAtLeast(100)
            state.winner == null -> 300
            else -> 0
        }
        _uiState.update { it.copy(score = score, isAiThinking = false) }
        if (state.mode == ChessMode.AI) {
            viewModelScope.launch {
                val isNewRecord = if (score > 0) persistFinishedGame(score) else false
                _uiState.update { it.copy(showResult = true, isNewRecord = isNewRecord) }
            }
        } else {
            _uiState.update { it.copy(showResult = true) }
        }
    }

    private fun ChessEngine.boardList(): List<ChessPiece?> =
        List(64) { index -> pieceAt(index / 8, index % 8) }

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ChessViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
