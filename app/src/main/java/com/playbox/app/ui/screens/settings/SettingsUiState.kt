package com.playbox.app.ui.screens.settings

import androidx.compose.runtime.Immutable
import com.playbox.app.domain.model.AppSettings

@Immutable
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
)

sealed interface SettingsEvent {
    data object RecordsCleared : SettingsEvent
    data object OperationFailed : SettingsEvent
}
