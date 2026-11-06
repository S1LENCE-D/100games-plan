package com.playbox.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.playbox.app.data.local.appPreferences
import com.playbox.app.data.local.safeData
import com.playbox.app.domain.model.AppColorPalette
import com.playbox.app.domain.model.AppSettings
import com.playbox.app.domain.model.SnakeButtonSize
import com.playbox.app.domain.model.SnakeControlMode
import com.playbox.app.domain.model.SnakeControlSide
import com.playbox.app.domain.model.ThemeMode
import com.playbox.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PreferencesSettingsRepository(
    context: Context,
) : SettingsRepository {

    private val dataStore = context.applicationContext.appPreferences

    override val settings: Flow<AppSettings> = dataStore.safeData
        .map { preferences ->
            val storedTheme = preferences[THEME_MODE_KEY]
            AppSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.name == storedTheme }
                    ?: ThemeMode.SYSTEM,
                dynamicColorEnabled = preferences[DYNAMIC_COLOR_ENABLED_KEY] ?: true,
                colorPalette = preferences[COLOR_PALETTE_KEY]
                    ?.let { name -> AppColorPalette.entries.firstOrNull { it.name == name } }
                    ?: AppColorPalette.BRAND,
                soundEnabled = preferences[SOUND_ENABLED_KEY] ?: true,
                hapticsEnabled = preferences[HAPTICS_ENABLED_KEY] ?: true,
                predictiveBackEnabled = preferences[PREDICTIVE_BACK_ENABLED_KEY] ?: true,
                snakeControlMode = preferences[SNAKE_CONTROL_MODE_KEY]
                    ?.let { name -> SnakeControlMode.entries.firstOrNull { it.name == name } }
                    ?: SnakeControlMode.BOTH,
                snakeControlSide = preferences[SNAKE_CONTROL_SIDE_KEY]
                    ?.let { name -> SnakeControlSide.entries.firstOrNull { it.name == name } }
                    ?: SnakeControlSide.RIGHT,
                snakeButtonSize = preferences[SNAKE_BUTTON_SIZE_KEY]
                    ?.let { name -> SnakeButtonSize.entries.firstOrNull { it.name == name } }
                    ?: SnakeButtonSize.MEDIUM,
                reactionLatencyOffsetMs = preferences[REACTION_LATENCY_OFFSET_KEY] ?: 0,
                reactionCalibrationSaved = preferences[REACTION_CALIBRATION_SAVED_KEY] ?: false,
                nearbyRoomName = preferences[NEARBY_ROOM_NAME_KEY]
                    ?.takeIf { it.isNotBlank() }
                    ?: "我的房间",
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_ENABLED_KEY] = enabled
        }
    }

    /**
     * 调色板与动态取色在同一个 DataStore 事务中提交，避免先切换调色板、
     * 再关闭动态色造成两次全应用主题重组和可见闪烁。
     */
    override suspend fun setColorPalette(palette: AppColorPalette) {
        dataStore.edit { preferences ->
            preferences[COLOR_PALETTE_KEY] = palette.name
            preferences[DYNAMIC_COLOR_ENABLED_KEY] = false
        }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SOUND_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAPTICS_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setPredictiveBackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PREDICTIVE_BACK_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setSnakeControlMode(mode: SnakeControlMode) {
        dataStore.edit { preferences ->
            preferences[SNAKE_CONTROL_MODE_KEY] = mode.name
        }
    }

    override suspend fun setSnakeControlSide(side: SnakeControlSide) {
        dataStore.edit { preferences ->
            preferences[SNAKE_CONTROL_SIDE_KEY] = side.name
        }
    }

    override suspend fun setSnakeButtonSize(size: SnakeButtonSize) {
        dataStore.edit { preferences ->
            preferences[SNAKE_BUTTON_SIZE_KEY] = size.name
        }
    }

    override suspend fun setReactionLatencyCalibration(offsetMs: Int, saved: Boolean) {
        dataStore.edit { preferences ->
            preferences[REACTION_LATENCY_OFFSET_KEY] = offsetMs.coerceIn(-150, 150)
            preferences[REACTION_CALIBRATION_SAVED_KEY] = saved
        }
    }

    override suspend fun setNearbyRoomName(name: String) {
        dataStore.edit { preferences ->
            preferences[NEARBY_ROOM_NAME_KEY] = name.take(8)
        }
    }

    private companion object {
        val THEME_MODE_KEY = stringPreferencesKey("settings_theme_mode")
        val DYNAMIC_COLOR_ENABLED_KEY = booleanPreferencesKey("settings_dynamic_color_enabled")
        val COLOR_PALETTE_KEY = stringPreferencesKey("settings_color_palette")
        val SOUND_ENABLED_KEY = booleanPreferencesKey("settings_sound_enabled")
        val HAPTICS_ENABLED_KEY = booleanPreferencesKey("settings_haptics_enabled")
        val PREDICTIVE_BACK_ENABLED_KEY = booleanPreferencesKey("settings_predictive_back_enabled")
        val SNAKE_CONTROL_MODE_KEY = stringPreferencesKey("settings_snake_control_mode")
        val SNAKE_CONTROL_SIDE_KEY = stringPreferencesKey("settings_snake_control_side")
        val SNAKE_BUTTON_SIZE_KEY = stringPreferencesKey("settings_snake_button_size")
        val REACTION_LATENCY_OFFSET_KEY = intPreferencesKey("settings_reaction_latency_offset_ms")
        val REACTION_CALIBRATION_SAVED_KEY = booleanPreferencesKey("settings_reaction_calibration_saved")
        val NEARBY_ROOM_NAME_KEY = stringPreferencesKey("settings_nearby_room_name")
    }
}
