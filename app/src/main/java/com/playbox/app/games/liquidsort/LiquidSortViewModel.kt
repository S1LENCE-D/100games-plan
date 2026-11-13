package com.playbox.app.games.liquidsort

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
private data class SavedLiquidSort(
    val snapshot: LiquidSortSnapshot,
)

class LiquidSortViewModel(
    statsRepository: GameStatsRepository,
    private val saveRepository: GameSaveRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.LIQUID_SORT,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private var engine = LiquidSortEngine.newGame(1)
    private var finishHandled = false
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<LiquidSortUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { restorePersistentSave() }
    }

    fun startGame() {
        startLevel(1)
    }

    fun nextLevel() {
        startLevel(_uiState.value.level + 1)
    }

    fun restart() {
        startLevel(_uiState.value.level)
    }

    fun selectTube(index: Int) {
        val state = _uiState.value
        if (!state.hasStarted || state.isSolved) return
        if (index !in state.tubes.indices) return
        val selected = state.selectedTube
        if (selected == null) {
            if (state.tubes[index].isNotEmpty()) {
                _uiState.update { it.copy(selectedTube = index) }
            }
            return
        }
        if (selected == index) {
            _uiState.update { it.copy(selectedTube = null) }
            return
        }
        if (engine.pour(selected, index)) {
            FeedbackController.drop()
            val solved = engine.isSolved()
            _uiState.update {
                it.copy(
                    tubes = engine.currentTubes,
                    selectedTube = null,
                    moves = engine.moves,
                    canUndo = engine.canUndo,
                    isSolved = solved,
                )
            }
            if (solved) finishGame()
        } else if (state.tubes[index].isNotEmpty()) {
            _uiState.update { it.copy(selectedTube = index) }
        } else {
            FeedbackController.error()
        }
    }

    fun undo() {
        if (!engine.undo()) return
        FeedbackController.tap()
        _uiState.update {
            it.copy(
                tubes = engine.currentTubes,
                selectedTube = null,
                moves = engine.moves,
                canUndo = engine.canUndo,
                isSolved = false,
            )
        }
    }

    fun saveGame() {
        val state = _uiState.value
        if (!state.hasStarted || state.isSolved) return
        val saved = SavedLiquidSort(engine.snapshot())
        viewModelScope.launch {
            runCatching { saveRepository.save(gameId, json.encodeToString(saved)) }
                .onFailure { throwable ->
                    com.playbox.app.diagnostics.AppLogger.warn("Save", "保存彩液分瓶进度失败", throwable)
                }
        }
    }

    private fun startLevel(level: Int) {
        engine = LiquidSortEngine.newGame(level)
        finishHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        markGameOpenedOnce()
        clearPersistentSave()
    }

    private fun finishGame() {
        if (finishHandled) return
        finishHandled = true
        val score = (1000 - engine.moves * 8).coerceAtLeast(100)
        _uiState.update { it.copy(score = score, selectedTube = null) }
        viewModelScope.launch {
            clearPersistentSave()
            val isNewRecord = persistFinishedGame(score)
            _uiState.update { it.copy(showResult = true, isNewRecord = isNewRecord) }
        }
    }

    private suspend fun restorePersistentSave() {
        if (_uiState.value.hasStarted) return
        val raw = runCatching { saveRepository.load(gameId) }.getOrNull() ?: return
        val saved = runCatching { json.decodeFromString<SavedLiquidSort>(raw) }.getOrNull() ?: return
        if (saved.snapshot.tubes.all { it.isEmpty() }) {
            runCatching { saveRepository.clear(gameId) }
            return
        }
        engine = LiquidSortEngine.restore(saved.snapshot)
        if (engine.isSolved()) {
            runCatching { saveRepository.clear(gameId) }
            return
        }
        _uiState.value = previewState().copy(
            tubes = engine.currentTubes,
            level = saved.snapshot.level,
            colorCount = saved.snapshot.colorCount,
            moves = engine.moves,
            canUndo = false,
            hasStarted = true,
        )
    }

    private fun previewState(): LiquidSortUiState = LiquidSortUiState(
        tubes = engine.currentTubes,
        level = engine.level,
        colorCount = engine.tubeCount - 2,
        moves = engine.moves,
        canUndo = engine.canUndo,
    )

    private fun clearPersistentSave() {
        viewModelScope.launch { runCatching { saveRepository.clear(gameId) } }
    }

    companion object {
        fun factory(
            statsRepository: GameStatsRepository,
            saveRepository: GameSaveRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    LiquidSortViewModel(
                        statsRepository = statsRepository,
                        saveRepository = saveRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
