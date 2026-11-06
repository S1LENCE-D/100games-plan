package com.playbox.app.domain.model

import androidx.compose.runtime.Immutable

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class SnakeControlMode {
    BOTH,
    SWIPE,
    BUTTONS,
}

enum class SnakeControlSide {
    LEFT,
    RIGHT,
}

enum class SnakeButtonSize {
    SMALL,
    MEDIUM,
    LARGE,
}

enum class AppColorPalette {
    BRAND,
    OCEAN,
    FOREST,
    SUNSET,
    BERRY,
}

@Immutable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val colorPalette: AppColorPalette = AppColorPalette.BRAND,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val snakeControlMode: SnakeControlMode = SnakeControlMode.BOTH,
    val snakeControlSide: SnakeControlSide = SnakeControlSide.RIGHT,
    val snakeButtonSize: SnakeButtonSize = SnakeButtonSize.MEDIUM,
    val reactionLatencyOffsetMs: Int = 0,
    val reactionCalibrationSaved: Boolean = false,
    val nearbyRoomName: String = "我的房间",
)
