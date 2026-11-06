package com.playbox.app.domain.model

import androidx.compose.runtime.Immutable

/**
 * 所有统计数据都由 DataStore 派生。没有记录时最高分为 0；
 * 反应力挑战的最高分语义是“最低耗时”，由 GameInfo.lowerScoreIsBetter 描述。
 */
@Immutable
data class PlayerStats(
    val highScores: Map<GameId, Int> = emptyMap(),
    val playCounts: Map<GameId, Int> = emptyMap(),
    val favorites: Set<GameId> = emptySet(),
    val recentGameIds: List<GameId> = emptyList(),
) {
    val totalPlays: Int get() = playCounts.values.sum()

    val playedGameCount: Int get() = playCounts.count { (_, count) -> count > 0 }

    fun highScoreFor(gameId: GameId): Int = highScores[gameId] ?: 0

    fun playCountFor(gameId: GameId): Int = playCounts[gameId] ?: 0
}
