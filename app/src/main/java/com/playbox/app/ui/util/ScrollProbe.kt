package com.playbox.app.ui.util

import android.annotation.SuppressLint
import android.os.SystemClock
import android.os.Trace
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.diagnostics.RecompositionTracker
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 记录一次纵向滚动会话：
 * - 起止时间、滚动时长；
 * - 滚动期间 Choreographer 帧数、平均 FPS、最差帧、>32ms 帧数；
 * - 关键 Composable 的重组次数差量。
 * 只在滚动开始/结束时写两条日志，不逐帧写文件。
 */
@SuppressLint("UnclosedTrace")
@Composable
fun ScrollProbe(
    screen: String,
    isScrolling: () -> Boolean,
) {
    val frameSampler = remember(screen) { ScrollFrameSampler() }
    var startedAt by remember(screen) { mutableLongStateOf(0L) }
    var baseline by remember(screen) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val traceName = remember(screen) { "Scroll: $screen" }

    LaunchedEffect(screen) {
        var traceOpen = false
        try {
            snapshotFlow(isScrolling).distinctUntilChanged().collect { scrolling ->
                if (scrolling) {
                    startedAt = SystemClock.elapsedRealtime()
                    baseline = RecompositionTracker.snapshot()
                    frameSampler.start()
                    if (!traceOpen) {
                        Trace.beginSection(traceName)
                        traceOpen = true
                    }
                    AppLogger.setScrolling(true)
                    AppLogger.info("Scroll", "$screen 开始滚动")
                } else if (startedAt > 0L) {
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    val frameStats = frameSampler.stop()
                    if (traceOpen) {
                        Trace.endSection()
                        traceOpen = false
                    }
                    val recompositions = RecompositionTracker.delta(baseline)
                        .entries
                        .sortedByDescending { it.value }
                        .joinToString(separator = "，") { "${it.key}+${it.value}" }
                        .ifEmpty { "无" }
                    AppLogger.setScrolling(false)
                    AppLogger.perf(
                        "Scroll",
                        "$screen 滚动结束 ${elapsed}ms · ${frameStats.describe()} · 重组：$recompositions",
                    )
                    startedAt = 0L
                    baseline = emptyMap()
                }
            }
        } finally {
            frameSampler.stop()
            if (traceOpen) {
                Trace.endSection()
            }
            AppLogger.setScrolling(false)
        }
    }
}

private class ScrollFrameSampler {
    private val choreographer = Choreographer.getInstance()
    private var active = false
    private var firstFrameNanos = 0L
    private var lastFrameNanos = 0L
    private var sampledFrames = 0
    private var worstFrameNanos = 0L
    private var overBudgetFrames = 0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!active) return
            if (firstFrameNanos == 0L) {
                firstFrameNanos = frameTimeNanos
            }
            if (lastFrameNanos != 0L) {
                val interval = frameTimeNanos - lastFrameNanos
                sampledFrames += 1
                if (interval > worstFrameNanos) {
                    worstFrameNanos = interval
                }
                if (interval > OVER_BUDGET_NANOS) {
                    overBudgetFrames += 1
                }
            }
            lastFrameNanos = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }

    fun start() {
        if (active) return
        active = true
        firstFrameNanos = 0L
        lastFrameNanos = 0L
        sampledFrames = 0
        worstFrameNanos = 0L
        overBudgetFrames = 0
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop(): FrameStats {
        active = false
        choreographer.removeFrameCallback(frameCallback)
        val spanNanos = if (lastFrameNanos > firstFrameNanos) {
            lastFrameNanos - firstFrameNanos
        } else {
            0L
        }
        return FrameStats(
            frames = sampledFrames,
            spanMillis = spanNanos / 1_000_000L,
            worstMillis = worstFrameNanos / 1_000_000.0,
            overBudgetFrames = overBudgetFrames,
        )
    }

    private companion object {
        const val OVER_BUDGET_NANOS = 32_000_000L
    }
}

private data class FrameStats(
    val frames: Int,
    val spanMillis: Long,
    val worstMillis: Double,
    val overBudgetFrames: Int,
) {
    fun describe(): String {
        if (frames == 0 || spanMillis <= 0L) return "无有效帧样本"
        val fps = frames * 1_000.0 / spanMillis
        return String.format(
            Locale.US,
            "帧 %d · 平均 %.1ffps · 最差 %.1fms · >32ms %d 帧",
            frames,
            fps,
            worstMillis,
            overBudgetFrames,
        )
    }
}
