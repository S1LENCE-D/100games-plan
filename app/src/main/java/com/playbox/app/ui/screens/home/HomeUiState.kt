package com.playbox.app.ui.screens.home

import androidx.compose.runtime.Immutable
import com.playbox.app.domain.model.Achievement
import com.playbox.app.domain.model.AchievementProgress
import com.playbox.app.domain.model.GameInfo

enum class HomeFilter(val label: String) {
    ALL("全部"),
    FAVORITES("收藏"),
    RECENT("最近玩过"),
}

@Immutable
data class HomeGameItem(
    val game: GameInfo,
    val highScore: Int,
    val isFavorite: Boolean,
    val playCount: Int,
)

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val filter: HomeFilter = HomeFilter.ALL,
    val games: List<HomeGameItem> = emptyList(),
    val totalPlays: Int = 0,
    val achievements: List<AchievementProgress> = Achievement.entries.map {
        AchievementProgress(achievement = it, current = 0)
    },
) {
    val unlockedAchievementCount: Int get() = achievements.count { it.isUnlocked }
    val achievementFraction: Float
        get() = if (achievements.isEmpty()) 0f else {
            unlockedAchievementCount.toFloat() / achievements.size
        }
}
