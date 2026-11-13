package com.playbox.app.games.doudizhu

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

class DdzViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.DOUDIZHU,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = DdzEngine()
    private var aiJob: Job? = null
    private var finishHandled = false

    private val _uiState = MutableStateFlow(DdzUiState(hand = engine.handOf(0)))
    val uiState: StateFlow<DdzUiState> = _uiState.asStateFlow()

    fun startGame() {
        aiJob?.cancel()
        engine = DdzEngine()
        finishHandled = false
        _uiState.value = DdzUiState(
            hand = engine.handOf(0),
            hasStarted = true,
        )
        syncState(started = true)
        markGameOpenedOnce()
    }

    fun bidHuman(points: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.phase != DdzPhase.BIDDING || state.currentBidder != 0) return
        if (!engine.bid(points)) return
        syncState(clearSelection = true)
        FeedbackController.tap()
        if (_uiState.value.phase == DdzPhase.PLAYING) {
            scheduleAiTurns()
        } else if (_uiState.value.phase == DdzPhase.BIDDING) {
            scheduleAiBidding()
        }
    }

    fun toggleCard(cardId: Int) {
        val state = _uiState.value
        if (!state.hasStarted || !state.humanTurn || state.isAiThinking) return
        val selected = if (cardId in state.selectedIds) state.selectedIds - cardId else state.selectedIds + cardId
        updateSelection(selected)
    }

    fun clearSelection() {
        updateSelection(emptySet())
    }

    fun playSelected() {
        val state = _uiState.value
        if (!state.humanTurn || state.isAiThinking) return
        val cards = state.hand.filter { it.id in state.selectedIds }
        if (cards.isEmpty()) return
        if (!engine.play(cards)) {
            _uiState.update { it.copy(message = "这手牌不符合规则或压不过上家") }
            return
        }
        FeedbackController.drop()
        syncState(clearSelection = true)
        if (_uiState.value.isGameOver) finishGame() else scheduleAiTurns()
    }

    fun pass() {
        val state = _uiState.value
        if (!state.humanTurn || state.isAiThinking || state.lastPlay == null) return
        if (!engine.pass()) return
        FeedbackController.tap()
        syncState(clearSelection = true)
        scheduleAiTurns()
    }

    fun hint() {
        val state = _uiState.value
        if (!state.humanTurn || state.isAiThinking) return
        val candidates = engine.validMovesForHuman()
        val choice = candidates.minWithOrNull(
            compareBy<DdzCombo> { it.cards.size }.thenBy { it.mainRank },
        ) ?: run {
            _uiState.update { it.copy(message = "没有能压过的牌") }
            return
        }
        val ids = choice.cards.map { it.id }.toSet()
        _uiState.update {
            it.copy(
                selectedIds = ids,
                selectedCombo = choice,
                message = choice.label(),
            )
        }
    }

    fun restart() {
        aiJob?.cancel()
        startGame()
    }

    private fun updateSelection(ids: Set<Int>) {
        val hand = _uiState.value.hand
        val cards = hand.filter { it.id in ids }
        val combo = classifyDdz(cards)
        _uiState.update {
            it.copy(
                selectedIds = ids,
                selectedCombo = combo,
                message = combo?.label().orEmpty(),
            )
        }
    }

    private fun scheduleAiBidding() {
        if (_uiState.value.phase != DdzPhase.BIDDING || _uiState.value.currentBidder == 0) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            while (engine.phase == DdzPhase.BIDDING && engine.currentBidder != 0) {
                _uiState.update { it.copy(isAiThinking = true) }
                delay(700)
                engine.bid(engine.aiBid())
                syncState(clearSelection = true)
                _uiState.update { it.copy(isAiThinking = false) }
                if (engine.phase == DdzPhase.PLAYING) break
                if (engine.currentBidder == 0) break
                delay(900)
            }
            _uiState.update { it.copy(isAiThinking = false) }
            if (engine.phase == DdzPhase.PLAYING) scheduleAiTurns()
        }
    }

    private fun scheduleAiTurns() {
        val state = _uiState.value
        if (state.phase != DdzPhase.PLAYING || state.currentPlayer == 0) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            while (engine.phase == DdzPhase.PLAYING && engine.currentPlayer != 0) {
                _uiState.update { it.copy(isAiThinking = true) }
                delay(750)
                val move = engine.aiMove()
                if (move != null) {
                    engine.play(move.cards)
                } else if (!engine.pass()) {
                    break
                }
                FeedbackController.drop()
                syncState(clearSelection = true)
                _uiState.update { it.copy(isAiThinking = false) }
                if (engine.phase == DdzPhase.FINISHED) {
                    delay(1_000)
                    break
                }
                delay(1_100)
            }
            _uiState.update { it.copy(isAiThinking = false) }
            if (engine.phase == DdzPhase.FINISHED) finishGame()
        }
    }

    private fun syncState(
        started: Boolean = _uiState.value.hasStarted,
        clearSelection: Boolean = true,
    ) {
        val snapshot = engine.snapshot()
        _uiState.update {
            it.copy(
                phase = snapshot.phase,
                hand = snapshot.hands.getOrNull(0).orEmpty(),
                counts = snapshot.hands.map { hand -> hand.size },
                bottomCards = snapshot.bottomCards,
                bottomRevealed = snapshot.bottomRevealed,
                currentPlayer = snapshot.currentPlayer,
                currentBidder = snapshot.currentBidder,
                highestBid = snapshot.highestBid,
                highestBidder = snapshot.highestBidder,
                landlord = snapshot.landlord,
                lastPlay = snapshot.lastPlay,
                passCount = snapshot.passCount,
                multiplier = snapshot.multiplier,
                lastAction = snapshot.lastAction,
                winner = snapshot.winner,
                landlordWon = snapshot.landlordWon,
                spring = snapshot.spring,
                antiSpring = snapshot.antiSpring,
                hasStarted = started,
                selectedIds = if (clearSelection) emptySet() else it.selectedIds,
                selectedCombo = if (clearSelection) null else it.selectedCombo,
                message = if (clearSelection) "" else it.message,
                isAiThinking = if (snapshot.isGameOver) false else it.isAiThinking,
            )
        }
    }

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        aiJob?.cancel()
        val state = _uiState.value
        val humanWon = if (state.landlord == 0) {
            state.landlordWon == true
        } else {
            state.landlordWon == false
        }
        val base = state.highestBid.coerceAtLeast(1)
        val score = if (humanWon) (base * 100 * state.multiplier).coerceAtLeast(100) else 0
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
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DdzViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
