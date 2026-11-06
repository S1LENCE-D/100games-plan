package com.playbox.app.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 轻量重组计数器。只在关键 Composable 的 SideEffect 中做一次自增，
 * 滚动结束时由 ScrollProbe 汇总差量写入日志，避免逐帧打日志。
 */
object RecompositionTracker {
    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    fun bump(tag: String) {
        counters.getOrPut(tag) { AtomicInteger(0) }.incrementAndGet()
    }

    fun snapshot(): Map<String, Int> = counters.mapValues { (_, counter) -> counter.get() }

    fun delta(before: Map<String, Int>): Map<String, Int> {
        val after = snapshot()
        return after.mapNotNull { (tag, count) ->
            val previous = before[tag] ?: 0
            val difference = count - previous
            if (difference > 0) tag to difference else null
        }.toMap()
    }
}

@Composable
fun TrackRecomposition(tag: String) {
    SideEffect {
        RecompositionTracker.bump(tag)
    }
}
