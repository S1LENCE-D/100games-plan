package com.playbox.app.ui.components.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

@Composable
fun PauseGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        val barWidth = size.width * 0.24f
        val barHeight = size.height * 0.7f
        val top = (size.height - barHeight) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.2f, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth * 0.35f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.56f, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth * 0.35f),
        )
    }
}
