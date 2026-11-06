package com.playbox.app.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.diagnostics.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 日志页使用手动刷新而不是持续收集 AppLogger.entries。
 * 查看日志本身也会产生 Jank 记录，持续刷新会导致列表反复重排并制造新的卡顿。
 */
class DiagnosticsViewModel : ViewModel() {

    private val currentFilter = MutableStateFlow(LogFilter.ALL)
    private val _uiState = MutableStateFlow(buildState(LogFilter.ALL))
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    fun setFilter(newFilter: LogFilter) {
        currentFilter.value = newFilter
        refresh()
    }

    fun refresh() {
        _uiState.value = buildState(currentFilter.value)
    }

    fun clearLogs() {
        AppLogger.clearLogs()
        refresh()
    }

    fun exportText(): String = AppLogger.exportText()

    private fun buildState(filter: LogFilter): DiagnosticsUiState {
        val entries = AppLogger.entries.value
        val filtered = when (filter) {
            LogFilter.ALL -> entries
            LogFilter.JANK -> entries.filter { it.level == LogLevel.PERF }
            LogFilter.ERRORS -> entries.filter {
                it.level == LogLevel.ERROR || it.level == LogLevel.WARN
            }
        }
        return DiagnosticsUiState(
            filter = filter,
            entries = filtered.asReversed(),
            totalCount = entries.size,
            jankCount = entries.count { it.level == LogLevel.PERF },
            errorCount = entries.count {
                it.level == LogLevel.ERROR || it.level == LogLevel.WARN
            },
            currentScreen = AppLogger.currentScreen,
        )
    }
}
