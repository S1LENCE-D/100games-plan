package com.playbox.app.domain.repository

import com.playbox.app.domain.model.GameId
import com.playbox.app.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow

interface GameStatsRepository {
    val stats: Flow<PlayerStats>

    /** 打开一局游戏时更新“最近玩过”，不增加完成局数。 */
    suspend fun markGameOpened(gameId: GameId)

    /**
     * 完成一局游戏后更新游玩次数与最高分。
     * [lowerIsBetter] 用于反应力挑战这类越低越好的成绩。
     */
    suspend fun recordGameFinished(
        gameId: GameId,
        score: Int,
        lowerIsBetter: Boolean,
    )

    suspend fun setFavorite(gameId: GameId, favorite: Boolean)

    /** 当前游戏是否已经展示过首次教程。 */
    suspend fun hasSeenTutorial(gameId: GameId): Boolean

    suspend fun markTutorialSeen(gameId: GameId)

    /** 仅清空游戏记录（最高分、局数、收藏、最近玩过），不会修改设置。 */
    suspend fun clearAllRecords()
}
