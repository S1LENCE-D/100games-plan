package com.playbox.app.ui.screens.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.BuildConfig
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.diagnostics.LogEntry
import com.playbox.app.diagnostics.LogLevel
import com.playbox.app.ui.components.SoftCard
import com.playbox.app.ui.util.TrackScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import com.playbox.app.diagnostics.TrackRecomposition
import com.playbox.app.ui.util.ScrollProbe

@Composable
fun DiagnosticsRouteScreen(onBack: () -> Unit) {
    TrackScreen("运行日志")
    val viewModel: DiagnosticsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    DiagnosticsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onFilterSelected = viewModel::setFilter,
        onRefreshLogs = viewModel::refresh,
        onClearLogs = viewModel::clearLogs,
        onExportReport = viewModel::exportText,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(
    uiState: DiagnosticsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onFilterSelected: (LogFilter) -> Unit,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onExportReport: () -> String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val timeFormatter = remember { LogTimeFormatter() }
    TrackRecomposition("DiagnosticsScreen")
    val listState = rememberLazyListState()
    ScrollProbe(screen = "运行日志") { listState.isScrollInProgress }

    fun copyReport() {
        val report = buildDiagnosticsReport(context, onExportReport())
        val copied = runCatching {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("100Games Plan diagnostics", report))
                ?: error("ClipboardManager unavailable")
        }.isSuccess
        scope.launch {
            snackbarHostState.showSnackbar(
                if (copied) "诊断报告已复制到剪贴板" else "复制失败，请稍后重试",
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TrackRecomposition("DiagnosticsTopBar")
            TopAppBar(
                title = { Text(text = "运行日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回设置",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRefreshLogs) {
                        Text(text = "刷新")
                    }
                    IconButton(
                        onClick = { showClearConfirmation = true },
                        enabled = uiState.totalCount > 0,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "清空日志",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        TrackRecomposition("DiagnosticsContent")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 920.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(contentType = "summary") {
                    DiagnosticsSummary(
                        uiState = uiState,
                        onCopyReport = ::copyReport,
                    )
                }
                item(contentType = "filters") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LogFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = uiState.filter == filter,
                                onClick = { onFilterSelected(filter) },
                                label = { Text(text = filter.label) },
                            )
                        }
                    }
                }
                if (uiState.entries.isEmpty()) {
                    item(contentType = "empty") {
                        EmptyLogs(filter = uiState.filter)
                    }
                } else {
                    items(
                        items = uiState.entries,
                        key = { entry -> "${entry.timestampMillis}-${entry.tag}-${entry.message.hashCode()}" },
                        contentType = { "log" },
                    ) { entry ->
                        LogRow(
                            entry = entry,
                            timeFormatter = timeFormatter,
                        )
                    }
                }
                item(contentType = "footer") { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                )
            },
            title = { Text(text = "清空运行日志？") },
            text = {
                Text(text = "内存中的日志和本机日志文件都会被删除，此操作无法撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearLogs()
                        scope.launch { snackbarHostState.showSnackbar("运行日志已清空") }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = "清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(text = "取消")
                }
            },
        )
    }
}

@Composable
private fun DiagnosticsSummary(
    uiState: DiagnosticsUiState,
    onCopyReport: () -> Unit,
) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "诊断概览",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "当前页面：${uiState.currentScreen}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DiagnosticMetric(
                    label = "日志",
                    value = uiState.totalCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                DiagnosticMetric(
                    label = "卡顿",
                    value = uiState.jankCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                DiagnosticMetric(
                    label = "错误/警告",
                    value = uiState.errorCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "日志保存在本机应用私有目录，最多保留 500 条。列表为手动刷新，避免查看日志时因列表反复重排产生额外卡顿；掉帧检测基于 AndroidX JankStats。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前为 Debug 构建，帧耗时包含调试开销；正式性能以 release 构建为准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onCopyReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(text = "复制诊断报告")
            }
        }
    }
}

@Composable
private fun DiagnosticMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Stable
private class LogTimeFormatter {
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun format(timestampMillis: Long): String = formatter.format(Date(timestampMillis))
}

@Composable
private fun LogRow(
    entry: LogEntry,
    timeFormatter: LogTimeFormatter,
) {
    val levelColor = entry.level.logColor()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val messageColor = if (entry.level == LogLevel.ERROR) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val time = remember(entry.timestampMillis) { timeFormatter.format(entry.timestampMillis) }
    val text = remember(entry, time, levelColor, mutedColor, messageColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = mutedColor)) {
                append(time)
            }
            append("  ")
            withStyle(
                SpanStyle(
                    color = levelColor,
                    background = levelColor.copy(alpha = 0.14f),
                    fontWeight = FontWeight.SemiBold,
                ),
            ) {
                append(entry.level.logLabel())
            }
            append("  ")
            withStyle(
                SpanStyle(
                    color = mutedColor,
                    fontWeight = FontWeight.Medium,
                ),
            ) {
                append(entry.tag)
            }
            append("\n")
            withStyle(SpanStyle(color = messageColor)) {
                append(entry.message)
            }
        }
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height - stroke),
                    end = Offset(size.width, size.height - stroke),
                    strokeWidth = stroke,
                )
            }
            .padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyLogs(filter: LogFilter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when (filter) {
                    LogFilter.ALL -> "还没有运行日志"
                    LogFilter.JANK -> "还没有检测到卡顿"
                    LogFilter.ERRORS -> "还没有错误或警告"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "继续使用应用，日志会实时出现在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LogLevel.logLabel(): String = when (this) {
    LogLevel.DEBUG -> "调试"
    LogLevel.INFO -> "信息"
    LogLevel.WARN -> "警告"
    LogLevel.ERROR -> "错误"
    LogLevel.PERF -> "卡顿"
}

@Composable
private fun LogLevel.logColor(): Color = when (this) {
    LogLevel.DEBUG -> MaterialTheme.colorScheme.outline
    LogLevel.INFO -> MaterialTheme.colorScheme.primary
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
    LogLevel.PERF -> MaterialTheme.colorScheme.secondary
}

private fun buildDiagnosticsReport(context: Context, logs: String): String {
    val configuration = context.resources.configuration
    val refreshRate = context.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
        ?.refreshRate
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    return buildString {
        appendLine("100Games Plan 诊断报告")
        appendLine("版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${if (BuildConfig.DEBUG) "debug" else "release"}")
        appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("系统：Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine(
            "屏幕：${configuration.screenWidthDp}×${configuration.screenHeightDp}dp · " +
                "刷新率 ${refreshRate?.let { rate -> String.format(Locale.US, "%.1f", rate) } ?: "未知"}Hz · " +
                "density ${context.resources.displayMetrics.density}",
        )
        appendLine("当前页面：${AppLogger.currentScreen}")
        appendLine("导出时间：${dateFormatter.format(Date())}")
        appendLine("----- 日志 -----")
        val maxReportChars = 200_000
        if (logs.length > maxReportChars) {
            appendLine("（日志过长，仅包含最近的内容）")
            append(logs.takeLast(maxReportChars))
        } else {
            append(logs)
        }
    }
}
