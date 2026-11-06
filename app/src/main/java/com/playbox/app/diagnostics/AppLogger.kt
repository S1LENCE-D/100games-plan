package com.playbox.app.diagnostics

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    PERF,
}

@Immutable
@Serializable
data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/**
 * 应用内诊断日志：内存环形缓冲 + 应用私有目录 JSONL 文件。
 *
 * - 日志页可实时查看、筛选、复制，便于反馈卡顿与崩溃。
 * - 文件最多保留约 512 KB，内存最多 500 条，避免无限增长。
 * - ERROR 同步落盘，保证未捕获异常在进程退出前保存。
 */
object AppLogger {
    private const val MAX_ENTRIES = 500
    private const val MAX_FILE_BYTES = 512L * 1024L
    private const val MAX_MESSAGE_LENGTH = 12_000
    private const val FILE_NAME = "app.log"

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileLock = Any()
    @Volatile
    private var logFile: File? = null
    @Volatile
    private var generation: Long = 0L
    @Volatile
    private var currentScreenValue: String = "启动"
    @Volatile
    private var scrollingValue: Boolean = false

    val currentScreen: String
        get() = currentScreenValue

    val isScrolling: Boolean
        get() = scrollingValue

    fun setScrolling(scrolling: Boolean) {
        scrollingValue = scrolling
    }

    fun initialize(context: Context) {
        if (logFile != null) return
        val file = File(context.filesDir, "diagnostics/$FILE_NAME")
        logFile = file
        val loadGeneration = generation
        writeScope.launch {
            loadExistingLogs(file, loadGeneration)
        }
    }

    fun setScreen(screen: String) {
        if (screen == currentScreenValue) return
        currentScreenValue = screen
        info("Navigation", "进入 $screen")
    }

    fun debug(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message, throwable = null)
    }

    fun info(tag: String, message: String) {
        log(LogLevel.INFO, tag, message, throwable = null)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    fun perf(tag: String, message: String) {
        log(LogLevel.PERF, tag, message, throwable = null)
    }

    fun exportText(): String {
        val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        return entries.value.joinToString(separator = "\n") { entry ->
            val time = formatter.format(Date(entry.timestampMillis))
            "$time ${entry.level.name.padEnd(5)} [${entry.tag}] ${entry.message}"
        }
    }

    fun clearLogs() {
        val file = logFile
        synchronized(fileLock) {
            generation += 1
            if (file != null) {
                file.delete()
                File(file.parentFile, "${file.name}.1").delete()
            }
        }
        _entries.value = emptyList()
    }

    private fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val fullMessage = buildString {
            append(message.take(MAX_MESSAGE_LENGTH))
            if (throwable != null) {
                append('\n')
                append(throwable.stackTraceToString().take(MAX_MESSAGE_LENGTH))
            }
        }
        val entry = LogEntry(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = fullMessage,
        )
        _entries.update { previous ->
            (previous + entry).takeLast(MAX_ENTRIES)
        }

        Log.println(
            level.toAndroidPriority(),
            "100Games/${tag.take(40)}",
            fullMessage,
        )

        val file = logFile ?: return
        if (level == LogLevel.ERROR) {
            writeEntry(file, entry)
        } else {
            val writeGeneration = generation
            writeScope.launch {
                if (writeGeneration == generation) {
                    writeEntry(file, entry)
                }
            }
        }
    }

    private fun writeEntry(file: File, entry: LogEntry) {
        synchronized(fileLock) {
            runCatching {
                file.parentFile?.mkdirs()
                if (file.exists() && file.length() > MAX_FILE_BYTES) {
                    val backup = File(file.parentFile, "${file.name}.1")
                    backup.delete()
                    file.renameTo(backup)
                }
                file.appendText(json.encodeToString(entry) + "\n")
            }
        }
    }

    private fun loadExistingLogs(file: File, loadGeneration: Long) {
        runCatching {
            if (!file.exists()) return@runCatching
            val loaded = file.useLines { lines ->
                lines.toList()
                    .takeLast(MAX_ENTRIES)
                    .mapNotNull { line ->
                        runCatching { json.decodeFromString<LogEntry>(line) }.getOrNull()
                    }
            }
            if (loadGeneration != generation) return@runCatching
            if (loaded.isNotEmpty()) {
                _entries.update { current ->
                    (loaded + current)
                        .sortedBy { it.timestampMillis }
                        .distinctBy { Triple(it.timestampMillis, it.tag, it.message) }
                        .takeLast(MAX_ENTRIES)
                }
            }
        }
    }

    private fun LogLevel.toAndroidPriority(): Int = when (this) {
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
        LogLevel.PERF -> Log.INFO
    }
}
