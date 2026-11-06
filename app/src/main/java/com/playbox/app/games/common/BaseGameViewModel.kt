package com.playbox.app.games.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playbox.app.domain.model.GameId
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.domain.repository.GameStatsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 游戏 ViewModel 的公共职责：最高分订阅、最近游玩、一局结束后的结算写入、
 * 以及统一固定 tick 循环的生命周期管理。具体棋盘规则仍由各游戏纯逻辑引擎负责。
 */
abstract class BaseGameViewModel(
    val gameId: GameId,
    private val statsRepository: GameStatsRepository,
    protected val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val bestScore: StateFlow<Int> = statsRepository.stats
        .map { stats -> stats.highScoreFor(gameId) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = 0,
        )

    private var gameLoopJob: Job? = null
    private var openedMarked = false

    private val _tutorialSeen = MutableStateFlow<Boolean?>(null)
    val tutorialSeen: StateFlow<Boolean?> = _tutorialSeen.asStateFlow()

    init {
        viewModelScope.launch {
            _tutorialSeen.value = runCatching { statsRepository.hasSeenTutorial(gameId) }
                .onFailure { throwable ->
                    AppLogger.warn("Tutorial", "读取教程状态失败：${gameId.slug}", throwable)
                }
                .getOrDefault(true)
        }
    }

    fun markTutorialSeen() {
        if (_tutorialSeen.value == true) return
        _tutorialSeen.value = true
        viewModelScope.launch {
            runCatching { statsRepository.markTutorialSeen(gameId) }
                .onFailure { throwable ->
                    AppLogger.warn("Tutorial", "写入教程状态失败：${gameId.slug}", throwable)
                }
        }
    }

    protected fun markGameOpenedOnce() {
        if (openedMarked) return
        openedMarked = true
        viewModelScope.launch {
            runCatching { statsRepository.markGameOpened(gameId) }
                .onFailure { throwable ->
                    AppLogger.warn("Game", "更新最近游玩失败：${gameId.slug}", throwable)
                }
        }
    }

    /**
     * 写入本局成绩，并返回是否刷新了本机最高分。写入失败时不阻塞游戏结算。
     */
    protected suspend fun persistFinishedGame(
        score: Int,
        lowerIsBetter: Boolean = false,
    ): Boolean = runCatching {
        val previousBest = statsRepository.stats.first().highScoreFor(gameId)
        val isNewRecord = when {
            score <= 0 -> false
            previousBest <= 0 -> true
            lowerIsBetter -> score < previousBest
            else -> score > previousBest
        }
        statsRepository.recordGameFinished(
            gameId = gameId,
            score = score,
            lowerIsBetter = lowerIsBetter,
        )
        isNewRecord
    }.onFailure { throwable ->
        AppLogger.error("Game", "写入本局成绩失败：${gameId.slug}", throwable)
    }.getOrDefault(false)

    protected fun startGameLoop(
        intervalMillis: () -> Long,
        onTick: () -> Unit,
    ) {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            try {
                GameLoop(
                    tickIntervalMillis = intervalMillis,
                    onTick = onTick,
                ).run()
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                AppLogger.error("GameLoop", "游戏循环异常：${gameId.slug}", throwable)
            }
        }
    }

    protected fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
    }

    override fun onCleared() {
        stopGameLoop()
        super.onCleared()
    }
}
