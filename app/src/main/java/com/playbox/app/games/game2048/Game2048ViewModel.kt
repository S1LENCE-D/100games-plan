package com.playbox.app.games.game2048

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
import kotlin.random.Random

@Serializable
private data class SavedGame2048(
    val engine: Snapshot2048,
    val hasStarted: Boolean,
    val isPaused: Boolean,
    val isNewRecord: Boolean,
    val showResult: Boolean,
)

class Game2048ViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.GAME_2048,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var engine = Game2048Engine.newGame()
    private var pendingDirection: MoveDirection? = null
    private var gameOverHandled = false

    private val _uiState = MutableStateFlow(
        Game2048UiState(
            tiles = engine.boardTiles,
            score = engine.currentScore,
            largestTile = engine.largestTile,
        ),
    )
    val uiState: StateFlow<Game2048UiState> = _uiState.asStateFlow()

    init {
        restoreSession()
    }

    fun startGame() {
        if (_uiState.value.hasStarted && !_uiState.value.isGameOver) return
        engine = Game2048Engine.newGame()
        gameOverHandled = false
        pendingDirection = null
        _uiState.value = Game2048UiState(
            tiles = engine.boardTiles,
            score = engine.currentScore,
            largestTile = engine.largestTile,
            hasStarted = true,
        )
        markGameOpenedOnce()
        persistSession()
    }

    fun move(direction: MoveDirection) {
        val state = _uiState.value
        if (!state.hasStarted || state.isPaused || state.isGameOver) return
        if (state.animation != null) {
            pendingDirection = direction
            return
        }
        performMove(direction)
    }

    fun undo() {
        if (_uiState.value.animation != null) return
        if (!_uiState.value.hasStarted || _uiState.value.isPaused || _uiState.value.isGameOver) return
        if (!engine.undo()) return
        FeedbackController.tap()
        gameOverHandled = false
        _uiState.update {
            it.copy(
                tiles = engine.boardTiles,
                score = engine.currentScore,
                largestTile = engine.largestTile,
                canUndo = engine.canUndo,
                isGameOver = false,
                showResult = false,
                isNewRecord = false,
                animation = null,
            )
        }
        persistSession()
    }

    fun togglePause() {
        val state = _uiState.value
        if (!state.hasStarted || state.isGameOver) return
        _uiState.update { it.copy(isPaused = !it.isPaused) }
        persistSession()
    }

    /**
     * 应用退到后台或旋转造成 Activity 暂停时自动暂停，并写入 SavedStateHandle，
     * 保证进程被回收后可以恢复棋盘。
     */
    fun onLifecyclePause() {
        val state = _uiState.value
        if (state.hasStarted && !state.isGameOver && !state.isPaused) {
            _uiState.update { it.copy(isPaused = true) }
        }
        if (state.hasStarted) {
            persistSession()
        }
    }

    fun restart() {
        engine = Game2048Engine.newGame()
        gameOverHandled = false
        pendingDirection = null
        _uiState.value = Game2048UiState(
            tiles = engine.boardTiles,
            score = engine.currentScore,
            largestTile = engine.largestTile,
            hasStarted = true,
        )
        persistSession()
    }

    private fun performMove(direction: MoveDirection) {
        val outcome = engine.move(direction)
        if (!outcome.moved) {
            FeedbackController.error()
            return
        }
        if (outcome.scoreGained > 0) {
            FeedbackController.merge()
        } else {
            FeedbackController.move()
        }

        _uiState.update {
            it.copy(
                tiles = engine.boardTiles,
                score = engine.currentScore,
                largestTile = engine.largestTile,
                canUndo = engine.canUndo,
                isGameOver = outcome.isGameOver,
                animation = outcome.animation,
            )
        }
    }

    /**
     * 由棋盘在滑动补间真正结束后回调，避免 ViewModel 定时器与 Compose 动画时钟竞争，
     * 否则动画未完成就被切换成最终棋盘，会出现数字方块“闪一下”的现象。
     */
    fun onMoveAnimationFinished(animationId: Long) {
        val state = _uiState.value
        if (state.animation?.id != animationId) return
        _uiState.update { it.copy(animation = null) }
        if (state.isGameOver) {
            finishGame()
        } else {
            pendingDirection?.let { nextDirection ->
                pendingDirection = null
                move(nextDirection)
            }
        }
    }

    private fun finishGame() {
        if (gameOverHandled) return
        gameOverHandled = true
        FeedbackController.gameOver()
        viewModelScope.launch {
            val newRecord = persistFinishedGame(score = engine.currentScore)
            _uiState.update {
                it.copy(
                    isGameOver = true,
                    isPaused = false,
                    animation = null,
                    isNewRecord = newRecord,
                    showResult = true,
                )
            }
            persistSession()
        }
    }

    private fun persistSession() {
        val state = _uiState.value
        if (!state.hasStarted) {
            savedStateHandle.remove<String>(SESSION_KEY)
            return
        }
        val saved = SavedGame2048(
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
        val saved = runCatching { json.decodeFromString<SavedGame2048>(raw) }
            .onFailure { throwable ->
                AppLogger.warn("Snapshot", "恢复游戏快照失败：${gameId.slug}", throwable)
            }
            .getOrNull() ?: return
        engine = Game2048Engine.restore(saved.engine)
        gameOverHandled = saved.engine.isGameOver
        _uiState.value = Game2048UiState(
            tiles = engine.boardTiles,
            score = engine.currentScore,
            largestTile = engine.largestTile,
            hasStarted = saved.hasStarted,
            isPaused = !saved.engine.isGameOver,
            isGameOver = saved.engine.isGameOver,
            canUndo = engine.canUndo,
            isNewRecord = saved.isNewRecord,
            showResult = saved.showResult || saved.engine.isGameOver,
        )
    }

    companion object {
        private const val SESSION_KEY = "game_2048_session"

        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    Game2048ViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
