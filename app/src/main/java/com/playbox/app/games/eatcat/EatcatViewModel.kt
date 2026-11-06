package com.playbox.app.games.eatcat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playbox.app.domain.model.GameId
import com.playbox.app.domain.repository.GameStatsRepository
import com.playbox.app.games.common.BaseGameViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EatcatViewModel(
    statsRepository: GameStatsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.EATCAT,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private val _webState = MutableStateFlow(EatcatWebState())
    val webState: StateFlow<EatcatWebState> = _webState.asStateFlow()

    private var finishHandled = false

    fun onWebState(state: EatcatWebState) {
        if (state.started) markGameOpenedOnce()
        if (!state.over) finishHandled = false
        _webState.value = state
        if (state.over && !finishHandled) {
            finishHandled = true
            if (state.score > 0) {
                viewModelScope.launch { persistFinishedGame(state.score) }
            }
        }
    }

    companion object {
        fun factory(statsRepository: GameStatsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    EatcatViewModel(
                        statsRepository = statsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
