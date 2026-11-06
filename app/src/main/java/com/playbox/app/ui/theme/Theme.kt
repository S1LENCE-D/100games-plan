package com.playbox.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.playbox.app.ui.util.findActivity
import com.playbox.app.domain.model.AppColorPalette
import com.playbox.app.domain.model.ThemeMode

@Composable
fun HundredGamesTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean = true,
    colorPalette: AppColorPalette = AppColorPalette.BRAND,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> colorPalette.darkScheme()
        else -> colorPalette.lightScheme()
    }
    val colorScheme = if (darkTheme) baseColorScheme else baseColorScheme.softenedLightSurfaces()


    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/**
 * Material 默认浅色背景接近纯白，在 OLED/夜间高亮度下容易刺眼。
 * 保留调色板色相，把页面、卡片与面板整体降低 2~6 个明度层级，并去掉纯白 surface。
 */
private fun ColorScheme.softenedLightSurfaces(): ColorScheme {
    val page = lerp(surfaceContainer, surfaceContainerHigh, 0.45f)
    val card = surfaceContainer
    return copy(
        background = page,
        surface = page,
        surfaceBright = card,
        surfaceDim = surfaceContainerHigh,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = page,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
    )
}
