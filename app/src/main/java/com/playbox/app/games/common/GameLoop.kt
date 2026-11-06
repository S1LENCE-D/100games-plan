package com.playbox.app.games.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 固定 tick 的游戏循环。各游戏只在需要时启动，暂停或结束时立即取消 Job。
 * 间隔由游戏引擎动态提供，因此贪吃蛇加速、俄罗斯方块升级都能无缝生效。
 */
class GameLoop(
    private val tickIntervalMillis: () -> Long,
    private val onTick: () -> Unit,
) {
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            delay(tickIntervalMillis().coerceAtLeast(MIN_TICK_INTERVAL_MILLIS))
            if (!currentCoroutineContext().isActive) return
            onTick()
        }
    }

    companion object {
        private const val MIN_TICK_INTERVAL_MILLIS = 16L
    }
}
