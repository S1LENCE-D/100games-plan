package com.playbox.app.diagnostics

import android.os.SystemClock
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import java.util.Locale
import kotlin.math.max

/**
 * 基于 AndroidX JankStats 的轻量帧卡顿检测。
 * JankStats 只会在界面可见期间开启追踪，这里额外每秒最多写一条日志，避免日志刷屏。
 */
object JankMonitor : JankStats.OnFrameListener {
    private const val REPORT_INTERVAL_MILLIS = 1_000L
    private const val SLOW_FRAME_THRESHOLD_MILLIS = 32.0

    private var lastReportAt: Long = 0L
    private var pendingJankCount: Int = 0
    private var pendingWorstDurationMillis: Double = 0.0

    override fun onFrame(frameData: FrameData) {
        val durationMillis = frameData.frameDurationUiNanos / 1_000_000.0
        if (!frameData.isJank && durationMillis < SLOW_FRAME_THRESHOLD_MILLIS) return

        pendingJankCount += 1
        pendingWorstDurationMillis = max(pendingWorstDurationMillis, durationMillis)
        val now = SystemClock.elapsedRealtime()
        if (lastReportAt == 0L || now - lastReportAt >= REPORT_INTERVAL_MILLIS) {
            report(now)
        }
    }

    fun flush() {
        if (pendingJankCount == 0) return
        report(SystemClock.elapsedRealtime())
    }

    fun reset() {
        pendingJankCount = 0
        pendingWorstDurationMillis = 0.0
        lastReportAt = 0L
    }

    private fun report(now: Long) {
        val worst = String.format(Locale.US, "%.1f", pendingWorstDurationMillis)
        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        AppLogger.perf(
            tag = "Jank",
            message = "卡顿：近段 ${pendingJankCount} 帧，最差 ${worst}ms，" +
                "内存 ${usedMemoryMb}MB，${if (AppLogger.isScrolling) "滚动中" else "静止"}，屏幕 ${AppLogger.currentScreen}",
        )
        pendingJankCount = 0
        pendingWorstDurationMillis = 0.0
        lastReportAt = now
    }
}
