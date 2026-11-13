package com.playbox.app.games.uno

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

class UnoViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.UNO,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = UnoEngine()
    private var aiJob: Job? = null
    private var finishHandled = false

    private val _uiState = MutableStateFlow(UnoUiState(discard = engine.discardTop))
    val uiState: StateFlow<UnoUiState> = _uiState.asStateFlow()

    fun startGame() {
        aiJob?.cancel()
        engine = UnoEngine()
        finishHandled = false
        _uiState.update {
            it.copy(
                showResult = false,
                isNewRecord = false,
                score = 0,
                pendingWildIndex = null,
                isAiThinking = false,
            )
        }
        syncState(started = true)
        markGameOpenedOnce()
    }

    fun tapCard(index: Int) {
        val state = _uiState.value
        if (!state.hasStarted || !state.humanTurn || state.isAiThinking || state.isGameOver) return
        if (index !in state.playableIndices) return
        val card = engine.handOf(0).getOrNull(index) ?: return
        if (card.isWild) {
            _uiState.update { it.copy(pendingWildIndex = index) }
        } else {
            commitPlay(index, null)
        }
    }

    fun chooseWildColor(color: UnoColor) {
        if (color == UnoColor.WILD) return
        val index = _uiState.value.pendingWildIndex ?: return
        _uiState.update { it.copy(pendingWildIndex = null) }
        commitPlay(index, color)
    }

    fun cancelWildChoice() {
        _uiState.update { it.copy(pendingWildIndex = null) }
    }

    fun drawCard() {
        val state = _uiState.value
        if (!state.hasStarted || !state.humanTurn || state.isAiThinking || state.isGameOver) return
        if (state.hasDrawnThisTurn) return
        engine.drawForCurrent()
        FeedbackController.tap()
        syncState()
        val after = _uiState.value
        if (after.isGameOver) {
            finishGame()
        } else if (after.humanTurn && after.playableIndices.isEmpty()) {
            engine.endTurnAfterDraw()
            syncState()
            scheduleAiTurns()
        } else if (!after.humanTurn) {
            scheduleAiTurns()
        }
    }

    fun endTurnAfterDraw() {
        val state = _uiState.value
        if (!state.hasStarted || !state.humanTurn || !state.hasDrawnThisTurn) return
        engine.endTurnAfterDraw()
        syncState()
        scheduleAiTurns()
    }

    fun callUno() {
        if (!_uiState.value.humanTurn) return
        if (engine.callUno(0)) {
            FeedbackController.tap()
            syncState()
        }
    }

    fun restart() {
        aiJob?.cancel()
        startGame()
    }

    private fun commitPlay(index: Int, color: UnoColor?) {
        if (!engine.playCard(index, color)) return
        FeedbackController.drop()
        syncState()
        val state = _uiState.value
        if (state.isGameOver) {
            finishGame()
        } else {
            scheduleAiTurns()
        }
    }

    private fun scheduleAiTurns() {
        val state = _uiState.value
        if (state.isGameOver || state.humanTurn) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            while (!engine.snapshot().isGameOver && engine.currentPlayer != 0) {
                _uiState.update { it.copy(isAiThinking = true) }
                delay(AI_THINK_MS)
                val player = engine.currentPlayer
                if (engine.handOf(player).size == 2) {
                    engine.callUno(player)
                }
                val choice = engine.aiChoice()
                if (choice != null) {
                    val card = engine.handOf(player)[choice]
                    engine.playCard(choice, if (card.isWild) engine.aiColorChoice() else null)
                } else {
                    engine.drawForCurrent()
                    val choices = engine.playableIndices()
                    if (choices.isNotEmpty()) {
                        val playIndex = engine.aiChoice()
                        if (playIndex != null) {
                            val card = engine.handOf(player)[playIndex]
                            engine.playCard(playIndex, if (card.isWild) engine.aiColorChoice() else null)
                        } else {
                            engine.endTurnAfterDraw()
                        }
                    } else {
                        engine.endTurnAfterDraw()
                    }
                }
                syncState()
                _uiState.update { it.copy(isAiThinking = false) }
                if (engine.snapshot().isGameOver) {
                    delay(AI_RESULT_VIEW_MS)
                    break
                }
                // 给玩家留出看清电脑出了什么牌、是否抽牌的时间。
                delay(AI_ACTION_VIEW_MS)
            }
            _uiState.update { it.copy(isAiThinking = false) }
            if (engine.snapshot().isGameOver) finishGame()
        }
    }

    private fun syncState(started: Boolean = _uiState.value.hasStarted) {
        val snapshot = engine.snapshot()
        _uiState.update {
            it.copy(
                hand = snapshot.hands.getOrNull(0).orEmpty(),
                counts = snapshot.hands.map { hand -> hand.size },
                discard = snapshot.discardTop,
                drawPileSize = snapshot.drawPileSize,
                currentColor = snapshot.currentColor,
                currentPlayer = snapshot.currentPlayer,
                direction = snapshot.direction,
                hasDrawnThisTurn = snapshot.hasDrawnThisTurn,
                unoDeclared = snapshot.unoDeclared,
                winner = snapshot.winner,
                playableIndices = if (snapshot.humanTurn) snapshot.playableIndices.toSet() else emptySet(),
                lastAction = snapshot.lastAction,
                hasStarted = started,
                isAiThinking = if (snapshot.isGameOver) false else it.isAiThinking,
            )
        }
    }

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        aiJob?.cancel()
        val state = _uiState.value
        val humanWon = state.winner == 0
        val opponentCards = state.counts.drop(1).sum()
        val score = if (humanWon) (1000 - opponentCards * 10).coerceAtLeast(100) else 0
        _uiState.update { it.copy(score = score, isAiThinking = false) }
        if (humanWon) {
            viewModelScope.launch {
                val isNewRecord = persistFinishedGame(score)
                _uiState.update { it.copy(showResult = true, isNewRecord = isNewRecord) }
            }
        } else {
            _uiState.update { it.copy(showResult = true) }
        }
    }

    companion object {
        private const val AI_THINK_MS = 650L
        private const val AI_ACTION_VIEW_MS = 1_200L
        private const val AI_RESULT_VIEW_MS = 1_000L

        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    UnoViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
