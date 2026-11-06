package com.playbox.app.domain.model

import androidx.compose.runtime.Immutable

enum class Achievement(
    val title: String,
    val description: String,
    val target: Int,
) {
    FIRST_GAME(
        title = "初次登场",
        description = "完成第一局游戏",
        target = 1,
    ),
    FIVE_PLAYS(
        title = "渐入佳境",
        description = "累计完成 5 局游戏",
        target = 5,
    ),
    TWENTY_PLAYS(
        title = "百玩不厌",
        description = "累计完成 20 局游戏",
        target = 20,
    ),
    FIFTY_PLAYS(
        title = "百战不殆",
        description = "累计完成 50 局游戏",
        target = 50,
    ),
    FAVORITE_THREE(
        title = "收藏新手",
        description = "收藏 3 款游戏",
        target = 3,
    ),
    FAVORITE_ALL(
        title = "收藏大师",
        description = "收藏全部游戏",
        target = 10,
    ),
    EXPLORER_FIVE(
        title = "多面玩家",
        description = "游玩 5 款不同的游戏",
        target = 5,
    ),
    EXPLORER_ALL(
        title = "十项全能",
        description = "游玩全部游戏",
        target = 10,
    ),
    ;

    fun currentValue(stats: PlayerStats): Int = when (this) {
        FIRST_GAME, FIVE_PLAYS, TWENTY_PLAYS, FIFTY_PLAYS -> stats.totalPlays
        FAVORITE_THREE, FAVORITE_ALL -> stats.favorites.size
        EXPLORER_FIVE, EXPLORER_ALL -> stats.playedGameCount
    }

    fun progressFor(stats: PlayerStats): AchievementProgress = AchievementProgress(
        achievement = this,
        current = currentValue(stats),
    )
}

@Immutable
data class AchievementProgress(
    val achievement: Achievement,
    val current: Int,
) {
    val target: Int get() = achievement.target
    val isUnlocked: Boolean get() = current >= target
    val fraction: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
}
