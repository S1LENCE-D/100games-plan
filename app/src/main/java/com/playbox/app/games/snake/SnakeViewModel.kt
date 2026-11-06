package com.playbox.app.games.snake

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
private data class SavedSnake(
    val engine: SnakeSnapshot,
    val hasStarted: Boolean,
    val isPaused: Boolean,
    val isNewRecord: Boolean,
    val showResult: Boolean,
)

class SnakeViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.SNAKE,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var difficulty: SnakeDifficulty = savedStateHandle.get<String>(DIFFICULTY_KEY)
        ?.let { name -> SnakeDifficulty.entries.firstOrNull { it.name == name } }
        ?: SnakeDifficulty.NORMAL
    private var wrapAround: Boolean = savedStateHandle[WRAP_KEY] ?: false
    private var engine = SnakeEngine.newGame(
        difficulty = difficulty,
        wrapAround = wrapAround,
    )
    private var gameOverHandled = false

    private val _uiState = MutableStateFlow(previewState())
    val uiState: StateFlow<SnakeUiState> = _uiState.asStateFlow()

    init {
        restoreSession()
    }

    fun selectDifficulty(selected: SnakeDifficulty) {
        if (_uiState.value.hasStarted) return
        difficulty = selected
        savedStateHandle[DIFFICULTY_KEY] = selected.name
        rebuildPreview()
    }

    fun setWrapAround(enabled: Boolean) {
        if (_uiState.value.hasStarted) return
        wrapAround = enabled
        savedStateHandle[WRAP_KEY] = enabled
        rebuildPreview()
    }

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        engine = SnakeEngine.newGame(
            difficulty = difficulty,
            wrapAround = wrapAround,
        )
        gameOverHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        markGameOpenedOnce()
        persistSession()
        startSnakeLoop()
    }

    fun togglePause() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver || state.showResult) return
        if (state.isPaused) {
            _uiState.update { it.copy(isPaused = false) }
            startSnakeLoop()
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

    fun turn(direction: Direction) {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver || state.showResult) return
        if (engine.turn(direction)) {
            FeedbackController.move()
            _uiState.update { it.copy(direction = engine.currentDirection) }
        }
    }

    fun restart() {
        stopGameLoop()
        engine = SnakeEngine.newGame(
            difficulty = difficulty,
            wrapAround = wrapAround,
        )
        gameOverHandled = false
        _uiState.value = previewState().copy(hasStarted = true)
        persistSession()
        startSnakeLoop()
    }

    private fun startSnakeLoop() {
        startGameLoop(
            intervalMillis = { engine.tickIntervalMillis },
            onTick = {
                if (!_uiState.value.isPaused && !_uiState.value.isGameOver) {
                    val lengthBefore = engine.length
                    engine.tick()
                    if (engine.isGameOver) {
                        syncEngineState()
                        finishGame()
                    } else {
                        if (engine.length > lengthBefore) {
                            FeedbackController.eat()
                        }
                        syncEngineState()
                    }
                }
            },
        )
    }

    private fun syncEngineState() {
        _uiState.update { state ->
            state.copy(
                body = engine.snakeBody,
                food = engine.currentFood,
                direction = engine.currentDirection,
                score = engine.currentScore,
                length = engine.length,
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

    private fun previewState(): SnakeUiState = SnakeUiState(
        body = engine.snakeBody,
        food = engine.currentFood,
        direction = engine.currentDirection,
        score = engine.currentScore,
        length = engine.length,
        difficulty = difficulty,
        wrapAround = wrapAround,
    )

    private fun rebuildPreview() {
        engine = SnakeEngine.newGame(
            difficulty = difficulty,
            wrapAround = wrapAround,
        )
        _uiState.value = previewState()
    }

    private fun persistSession() {
        val state = _uiState.value
        if (!state.hasStarted) {
            savedStateHandle.remove<String>(SESSION_KEY)
            return
        }
        val saved = SavedSnake(
            engine = engine.snapshot(),
            hasStarted = state.hasStarted,
            isPaused = state.isPaused,
            isNewRecord = state.isNewRecord,
            showResult = state.showResult,
        )
        savedStateHandle[SESSION_KEY] = json.encodeToString(saved)
    }

    private fun restoreSession() {
        val raw = savedStateHandle.get<String>(SESSION_KEY) ?: return
        val saved = runCatching { json.decodeFromString<SavedSnake>(raw) }
            .onFailure { throwable ->
                AppLogger.warn("Snapshot", "恢复游戏快照失败：${gameId.slug}", throwable)
            }
            .getOrNull() ?: return
        engine = SnakeEngine.restore(saved.engine)
        difficulty = saved.engine.difficulty
        wrapAround = saved.engine.wrapAround
        gameOverHandled = saved.engine.gameOver
        _uiState.value = previewState().copy(
            hasStarted = saved.hasStarted,
            isPaused = !saved.engine.gameOver,
            isGameOver = saved.engine.gameOver,
            isNewRecord = saved.isNewRecord,
            showResult = saved.showResult || saved.engine.gameOver,
        )
        savedStateHandle[DIFFICULTY_KEY] = difficulty.name
        savedStateHandle[WRAP_KEY] = wrapAround
    }

    companion object {
        private const val SESSION_KEY = "snake_session"
        private const val DIFFICULTY_KEY = "snake_difficulty"
        private const val WRAP_KEY = "snake_wrap"

        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SnakeViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
