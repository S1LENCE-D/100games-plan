package com.playbox.app.ui.components.game

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 统一的新游戏操作按钮内容：Material 图标 + 文字。
 * 图标使用 Material Icons Core，避免为新游戏引入额外的图标依赖。
 */
@Composable
fun GameButtonContent(
    icon: ImageVector,
    text: String,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
    )
}
