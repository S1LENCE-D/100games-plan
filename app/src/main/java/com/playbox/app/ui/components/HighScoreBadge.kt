package com.playbox.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.playbox.app.domain.model.GameInfo

fun GameInfo.hasScore(score: Int): Boolean = score > 0

fun GameInfo.formatBestScore(score: Int): String = when {
    score <= 0 -> "暂无纪录"
    lowerScoreIsBetter -> "最佳 ${score}ms"
    else -> "最高 $score"
}

@Composable
fun HighScoreBadge(
    game: GameInfo,
    score: Int,
    modifier: Modifier = Modifier,
) {
    val hasRecord = game.hasScore(score)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (hasRecord) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (hasRecord) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = game.formatBestScore(score),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
