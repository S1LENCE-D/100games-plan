package com.playbox.app.domain.repository

import com.playbox.app.domain.model.GameId

/**
 * 非即时游戏的持久化存档。payload 为各游戏自己定义的 JSON。
 */
interface GameSaveRepository {
    suspend fun save(gameId: GameId, payload: String)
    suspend fun load(gameId: GameId): String?
    suspend fun clear(gameId: GameId)
}
