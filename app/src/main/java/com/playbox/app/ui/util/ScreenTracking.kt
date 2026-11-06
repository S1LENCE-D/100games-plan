package com.playbox.app.ui.util

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.playbox.app.diagnostics.AppLogger

/**
 * 记录当前页面，并在页面首次绘制后写一条性能日志。
 * 用于把 JankStats 的掉帧尖峰对应到具体页面进入成本。
 */
@Composable
fun TrackScreen(name: String) {
    val startedAt = remember(name) { SystemClock.elapsedRealtime() }

    DisposableEffect(name) {
        AppLogger.setScreen(name)
        onDispose { }
    }

    LaunchedEffect(name) {
        withFrameNanos { }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (elapsed >= 50) {
            AppLogger.perf("Screen", "$name 首次绘制 ${elapsed}ms")
        }
    }
}
