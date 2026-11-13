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
import com.playbox.app.domain.model.GameCategory
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
    private val queryFlow = savedStateHandle.getStateFlow(
        key = QUERY_KEY,
        initialValue = "",
    )

    val uiState: StateFlow<HomeUiState> = combine(
        statsRepository.stats,
        filterFlow,
        queryFlow,
    ) { stats, storedFilter, query ->
        val filter = HomeFilter.entries.firstOrNull { it.name == storedFilter }
            ?: HomeFilter.ALL
        val normalizedQuery = query.trim()

        val visibleGames = when (filter) {
            HomeFilter.ALL -> GameCatalog.all
            HomeFilter.FAVORITES -> GameCatalog.all.filter { it.id in stats.favorites }
            HomeFilter.RECENT -> stats.recentGameIds.mapNotNull(GameCatalog::findById)
        }.filter { game ->
            normalizedQuery.isEmpty() ||
                game.name.contains(normalizedQuery, ignoreCase = true) ||
                game.tagline.contains(normalizedQuery, ignoreCase = true) ||
                game.category.label.contains(normalizedQuery, ignoreCase = true)
        }

        val items = visibleGames.map { game ->
            HomeGameItem(
                game = game,
                highScore = stats.highScoreFor(game.id),
                isFavorite = game.id in stats.favorites,
                playCount = stats.playCountFor(game.id),
            )
        }
        val orderedCategories = listOf(
            GameCategory.STRATEGY,
            GameCategory.PUZZLE,
            GameCategory.ARCADE,
            GameCategory.CASUAL,
        )
        val sections = orderedCategories.mapNotNull { category ->
            val categoryItems = items.filter { it.game.category == category }
            if (categoryItems.isEmpty()) null else HomeSection(
                category = category,
                title = when (category) {
                    GameCategory.STRATEGY -> "棋牌策略"
                    GameCategory.PUZZLE -> "益智解谜"
                    GameCategory.ARCADE -> "街机反应"
                    GameCategory.CASUAL -> "休闲"
                },
                games = categoryItems,
            )
        }

        HomeUiState(
            isLoading = false,
            filter = filter,
            query = query,
            sections = sections,
            games = items,
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

    fun updateQuery(query: String) {
        savedStateHandle[QUERY_KEY] = query
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
        private const val QUERY_KEY = "home_query"

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
