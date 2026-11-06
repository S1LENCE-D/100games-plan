package com.playbox.app.ui.screens.gamedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playbox.app.domain.model.GameCatalog
import com.playbox.app.domain.model.GameInfo
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.domain.repository.GameStatsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameDetailViewModel(
    gameSlug: String,
    private val statsRepository: GameStatsRepository,
) : ViewModel() {

    private val game: GameInfo? = GameCatalog.findBySlug(gameSlug)

    private val eventsChannel = Channel<GameDetailEvent>(capacity = Channel.BUFFERED)
    val events: Flow<GameDetailEvent> = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<GameDetailUiState> = if (game == null) {
        MutableStateFlow(GameDetailUiState(isLoading = false)).asStateFlow()
    } else {
        statsRepository.stats
            .map { stats ->
                GameDetailUiState(
                    game = game,
                    highScore = stats.highScoreFor(game.id),
                    playCount = stats.playCountFor(game.id),
                    isFavorite = game.id in stats.favorites,
                    isLoading = false,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = GameDetailUiState(game = game),
            )
    }

    fun toggleFavorite() {
        val currentGame = game ?: return
        viewModelScope.launch {
            val result = runCatching {
                val currentlyFavorite = statsRepository.stats.first().favorites.contains(currentGame.id)
                val nextFavorite = !currentlyFavorite
                statsRepository.setFavorite(currentGame.id, nextFavorite)
                nextFavorite
            }

            result.onSuccess { isFavorite ->
                eventsChannel.send(GameDetailEvent.FavoriteChanged(currentGame.name, isFavorite))
            }.onFailure { throwable ->
                AppLogger.warn("GameDetail", "切换收藏失败：${currentGame.id.slug}", throwable)
                eventsChannel.send(GameDetailEvent.OperationFailed)
            }
        }
    }

    companion object {
        fun factory(
            gameSlug: String,
            statsRepository: GameStatsRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameDetailViewModel(
                    gameSlug = gameSlug,
                    statsRepository = statsRepository,
                )
            }
        }
    }
}
