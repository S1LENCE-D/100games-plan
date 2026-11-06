package com.playbox.app.domain.model

import androidx.compose.runtime.Immutable

enum class GameCategory(val label: String) {
    PUZZLE("益智"),
    ARCADE("街机"),
    STRATEGY("策略"),
    CASUAL("休闲"),
}

/**
 * 游戏大厅与详情页共享的静态元数据。实际玩法由各游戏的独立引擎负责。
 */
@Immutable
data class GameInfo(
    val id: GameId,
    val name: String,
    val tagline: String,
    val rules: List<String>,
    val category: GameCategory,
    val lowerScoreIsBetter: Boolean = false,
)
