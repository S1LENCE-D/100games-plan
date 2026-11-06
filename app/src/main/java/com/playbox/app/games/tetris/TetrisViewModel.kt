package com.playbox.app.games.tetris

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.domain.model.GameId
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
private data class SavedTetris(
    val engine: TetrisSnapshot,
    val difficulty: String,
    val hasStarted: Boolean,
    val isPaused: Boolean,
    val isNewRecord: Boolean,
    val showResult: Boolean,
)

class TetrisViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.TETRIS,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var difficulty: TetrisDifficulty = savedStateHandle.get<String>(DIFFICULTY_KEY)
        ?.let { name -> TetrisDifficulty.entries.firstOrNull { it.name == name } }
        ?: TetrisDifficulty.NORMAL
    private var engine = TetrisEngine.newGame(startLevel = difficulty.startLevel)
    private var gameOverHandled = false

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<TetrisUiState> = _uiState.asStateFlow()

    init {
        restoreSession()
    }

    fun selectDifficulty(selected: TetrisDifficulty) {
        if (_uiState.value.hasStarted) return
        difficulty = selected
        savedStateHandle[DIFFICULTY_KEY] = selected.name
        engine = TetrisEngine.newGame(startLevel = selected.startLevel)
        _uiState.value = previewState()
    }

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        engine = TetrisEngine.newGame(startLevel = difficulty.startLevel)
        gameOverHandled = false
        _uiState.value = previewState().copy(
            hasStarted = true,
            level = engine.currentLevel,
        )
        markGameOpenedOnce()
        persistSession()
        startGravity()
    }

    fun togglePause() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver || state.showResult) return
        if (state.isPaused) {
            _uiState.update { it.copy(isPaused = false) }
            startGravity()
        } else {
            stopGameLoop()
            _uiState.update { it.copy(isPaused = true) }
            persistSession()
        }
    }

    fun onLifecyclePause() {
        val state = _uiState.value
        if (!state.hasStarted) return
        if (!state.isPaused && !state.isGameOver) {
            stopGameLoop()
            _uiState.update { it.copy(isPaused = true) }
        }
        persistSession()
    }

    fun moveLeft() {
        if (runCommand { engine.moveLeft() }) FeedbackController.move()
    }

    fun moveRight() {
        if (runCommand { engine.moveRight() }) FeedbackController.move()
    }

    fun rotate() {
        if (runCommand { engine.rotate() }) FeedbackController.rotate()
    }

    fun softDrop() {
        if (runCommand { engine.softDrop() }) FeedbackController.move()
    }

    fun hardDrop() {
        val linesBefore = engine.clearedLines
        if (runCommand { engine.hardDrop() }) {
            if (engine.clearedLines > linesBefore) {
                FeedbackController.clear()
            } else {
                FeedbackController.drop()
            }
        }
    }

    fun restart() {
        stopGameLoop()
        engine = TetrisEngine.newGame(startLevel = difficulty.startLevel)
        gameOverHandled = false
        _uiState.value = previewState().copy(
            hasStarted = true,
            level = engine.currentLevel,
        )
        persistSession()
        startGravity()
    }

    private fun runCommand(command: () -> Unit): Boolean {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver || state.showResult) return false
        command()
        syncEngineState()
        if (engine.isGameOver) {
            finishGame()
            return false
        }
        return true
    }

    private fun startGravity() {
        startGameLoop(
            intervalMillis = { engine.gravityDelayMillis() },
            onTick = {
                if (!_uiState.value.isPaused && !_uiState.value.isGameOver) {
                    engine.tick()
                    syncEngineState()
                    if (engine.isGameOver) {
                        finishGame()
                    }
                }
            },
        )
    }

    private fun syncEngineState() {
        _uiState.update { state ->
            state.copy(
                board = engine.boardValues,
                activeCells = engine.activeCellPositions,
                activeType = engine.currentPiece?.type,
                nextType = engine.nextType,
                score = engine.currentScore,
                clearedLines = engine.clearedLines,
                level = engine.currentLevel,
                isGameOver = engine.isGameOver,
            )
        }
    }

    private fun finishGame() {
        if (gameOverHandled) return
        gameOverHandled = true
        FeedbackController.gameOver()
        stopGameLoop()
        syncEngineState()
        _uiState.update { it.copy(isGameOver = true, isPaused = false) }
        viewModelScope.launch {
            val isNewRecord = persistFinishedGame(score = engine.currentScore)
            _uiState.update {
                it.copy(
                    isNewRecord = isNewRecord,
                    showResult = true,
                )
            }
            persistSession()
        }
    }

    private fun previewState(): TetrisUiState = TetrisUiState(
        board = engine.boardValues,
        activeCells = engine.activeCellPositions,
        activeType = engine.currentPiece?.type,
        nextType = engine.nextType,
        score = engine.currentScore,
        clearedLines = engine.clearedLines,
        level = engine.currentLevel,
        difficulty = difficulty,
    )

    private fun persistSession() {
        val state = _uiState.value
        if (!state.hasStarted) {
            savedStateHandle.remove<String>(SESSION_KEY)
            return
        }
        val saved = SavedTetris(
            engine = engine.snapshot(),
            difficulty = difficulty.name,
            hasStarted = state.hasStarted,
            isPaused = state.isPaused,
            isNewRecord = state.isNewRecord,
            showResult = state.showResult,
        )
        savedStateHandle[SESSION_KEY] = json.encodeToString(saved)
    }

    private fun restoreSession() {
        val raw = savedStateHandle.get<String>(SESSION_KEY) ?: return
        val saved = runCatching { json.decodeFromString<SavedTetris>(raw) }
            .onFailure { throwable ->
                AppLogger.warn("Snapshot", "恢复游戏快照失败：${gameId.slug}", throwable)
            }
            .getOrNull() ?: return
        engine = TetrisEngine.restore(saved.engine)
        difficulty = saved.engine.startLevel.let(TetrisDifficulty::fromStartLevel)
        gameOverHandled = saved.engine.gameOver
        _uiState.value = previewState().copy(
            hasStarted = saved.hasStarted,
            isPaused = !saved.engine.gameOver,
            isGameOver = saved.engine.gameOver,
            isNewRecord = saved.isNewRecord,
            showResult = saved.showResult || saved.engine.gameOver,
        )
    }

    companion object {
        private const val SESSION_KEY = "tetris_session"
        private const val DIFFICULTY_KEY = "tetris_difficulty"

        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TetrisViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
