package com.playbox.app.domain.repository

import com.playbox.app.domain.model.AppColorPalette
import com.playbox.app.domain.model.AppSettings
import com.playbox.app.domain.model.SnakeButtonSize
import com.playbox.app.domain.model.SnakeControlMode
import com.playbox.app.domain.model.SnakeControlSide
import com.playbox.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColorEnabled(enabled: Boolean)

    suspend fun setColorPalette(palette: AppColorPalette)

    suspend fun setSoundEnabled(enabled: Boolean)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setPredictiveBackEnabled(enabled: Boolean)

    suspend fun setSnakeControlMode(mode: SnakeControlMode)

    suspend fun setSnakeControlSide(side: SnakeControlSide)

    suspend fun setSnakeButtonSize(size: SnakeButtonSize)

    suspend fun setReactionLatencyCalibration(offsetMs: Int, saved: Boolean)

    suspend fun setNearbyRoomName(name: String)
}
