package com.playbox.app

import android.content.Context
import com.playbox.app.data.repository.PreferencesGameSaveRepository
import com.playbox.app.data.repository.PreferencesGameStatsRepository
import com.playbox.app.data.repository.PreferencesSettingsRepository
import com.playbox.app.domain.repository.GameSaveRepository
import com.playbox.app.domain.repository.GameStatsRepository
import com.playbox.app.domain.repository.SettingsRepository

/**
 * 轻量依赖容器：阶段 1 不引入 DI 框架，保持构建简单且所有依赖均可替换测试实现。
 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val gameStatsRepository: GameStatsRepository = PreferencesGameStatsRepository(applicationContext)

    val settingsRepository: SettingsRepository = PreferencesSettingsRepository(applicationContext)

    val gameSaveRepository: GameSaveRepository = PreferencesGameSaveRepository(applicationContext)
}
