package com.playbox.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playbox.app.domain.model.AppColorPalette
import com.playbox.app.domain.model.ThemeMode
import com.playbox.app.domain.repository.GameStatsRepository
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val statsRepository: GameStatsRepository,
) : ViewModel() {

    private val eventsChannel = Channel<SettingsEvent>(capacity = Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { settings -> SettingsUiState(settings = settings, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SettingsUiState(),
        )

    fun setThemeMode(mode: ThemeMode) {
        launchWrite { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        launchWrite { settingsRepository.setDynamicColorEnabled(enabled) }
    }

    fun setColorPalette(palette: AppColorPalette) {
        launchWrite { settingsRepository.setColorPalette(palette) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        launchWrite { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        launchWrite { settingsRepository.setHapticsEnabled(enabled) }
    }

    fun setPredictiveBackEnabled(enabled: Boolean) {
        launchWrite { settingsRepository.setPredictiveBackEnabled(enabled) }
    }

    fun clearGameRecords() {
        viewModelScope.launch {
            runCatching { statsRepository.clearAllRecords() }
                .onSuccess { eventsChannel.send(SettingsEvent.RecordsCleared) }
                .onFailure { throwable ->
                    AppLogger.warn("Settings", "清除游戏记录失败", throwable)
                    eventsChannel.send(SettingsEvent.OperationFailed)
                }
        }
    }

    private fun launchWrite(write: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { write() }
                .onFailure { throwable ->
                    AppLogger.warn("Settings", "写入设置失败", throwable)
                    eventsChannel.send(SettingsEvent.OperationFailed)
                }
        }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            statsRepository: GameStatsRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = settingsRepository,
                    statsRepository = statsRepository,
                )
            }
        }
    }
}
