package com.playbox.app.ui.screens.diagnostics

import androidx.compose.runtime.Immutable
import com.playbox.app.diagnostics.LogEntry

enum class LogFilter(val label: String) {
    ALL("全部"),
    JANK("卡顿"),
    ERRORS("错误/警告"),
}

@Immutable
data class DiagnosticsUiState(
    val filter: LogFilter = LogFilter.ALL,
    val entries: List<LogEntry> = emptyList(),
    val totalCount: Int = 0,
    val jankCount: Int = 0,
    val errorCount: Int = 0,
    val currentScreen: String = "启动",
)
