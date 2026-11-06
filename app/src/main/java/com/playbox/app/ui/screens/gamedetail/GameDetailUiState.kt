package com.playbox.app.ui.screens.gamedetail

import androidx.compose.runtime.Immutable
import com.playbox.app.domain.model.GameInfo

@Immutable
data class GameDetailUiState(
    val game: GameInfo? = null,
    val highScore: Int = 0,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
)

sealed interface GameDetailEvent {
    data class FavoriteChanged(
        val gameName: String,
        val isFavorite: Boolean,
    ) : GameDetailEvent

    data object OperationFailed : GameDetailEvent
}
