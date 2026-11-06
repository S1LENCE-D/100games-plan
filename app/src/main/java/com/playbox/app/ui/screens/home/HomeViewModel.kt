package com.playbox.app.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playbox.app.domain.model.Achievement
import com.playbox.app.domain.model.GameCatalog
import com.playbox.app.domain.model.GameId
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.domain.repository.GameStatsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeEvent {
    data class FavoriteChanged(
        val gameName: String,
        val isFavorite: Boolean,
    ) : HomeEvent

    data object OperationFailed : HomeEvent
}

class HomeViewModel(
    private val statsRepository: GameStatsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val eventsChannel = Channel<HomeEvent>(capacity = Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventsChannel.receiveAsFlow()

    private val filterFlow = savedStateHandle.getStateFlow(
        key = FILTER_KEY,
        initialValue = HomeFilter.ALL.name,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        statsRepository.stats,
        filterFlow,
    ) { stats, storedFilter ->
        val filter = HomeFilter.entries.firstOrNull { it.name == storedFilter }
            ?: HomeFilter.ALL

        val visibleGames = when (filter) {
            HomeFilter.ALL -> GameCatalog.all
            HomeFilter.FAVORITES -> GameCatalog.all.filter { it.id in stats.favorites }
            HomeFilter.RECENT -> stats.recentGameIds.mapNotNull(GameCatalog::findById)
        }

        HomeUiState(
            isLoading = false,
            filter = filter,
            games = visibleGames.map { game ->
                HomeGameItem(
                    game = game,
                    highScore = stats.highScoreFor(game.id),
                    isFavorite = game.id in stats.favorites,
                    playCount = stats.playCountFor(game.id),
                )
            },
            totalPlays = stats.totalPlays,
            achievements = Achievement.entries.map { it.progressFor(stats) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = HomeUiState(),
    )

    fun selectFilter(filter: HomeFilter) {
        savedStateHandle[FILTER_KEY] = filter.name
    }

    fun toggleFavorite(gameId: GameId) {
        viewModelScope.launch {
            val result = runCatching {
                val currentlyFavorite = statsRepository.stats.first().favorites.contains(gameId)
                val nextFavorite = !currentlyFavorite
                statsRepository.setFavorite(gameId, nextFavorite)
                nextFavorite
            }

            result.onSuccess { isFavorite ->
                val name = GameCatalog.findById(gameId)?.name.orEmpty()
                eventsChannel.send(HomeEvent.FavoriteChanged(name, isFavorite))
            }.onFailure { throwable ->
                AppLogger.warn("Home", "切换收藏失败：${gameId.slug}", throwable)
                eventsChannel.send(HomeEvent.OperationFailed)
            }
        }
    }

    companion object {
        private const val FILTER_KEY = "home_filter"

        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HomeViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
