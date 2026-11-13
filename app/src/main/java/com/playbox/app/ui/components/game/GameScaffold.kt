package com.playbox.app.ui.components.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.feedback.FeedbackController
import com.playbox.app.ui.util.TrackScreen
import kotlinx.coroutines.launch

@Immutable
data class GameStatusItem(
    val label: String,
    val value: String,
)

/**
 * 所有游戏页共用的骨架：顶部栏与得分区、中央游戏区。
 * 背景返回键统一通过 [BackHandler] 交给各游戏处理退出确认。
 *
 * [onHelpRequest] 不为空时，顶部会出现帮助按钮；游戏页负责暂停与展示教程。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScaffold(
    gameName: String,
    score: Int,
    bestScore: Int,
    canPause: Boolean,
    isPaused: Boolean,
    onBackRequest: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
    scoreLabel: String = "得分",
    bestScoreLabel: String = "最高分",
    extraStats: List<GameStatusItem> = emptyList(),
    onHelpRequest: (() -> Unit)? = null,
    onSaveRequest: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    TrackScreen("游戏 · $gameName")

    val handleBackRequest: () -> Unit = {
        FeedbackController.tap()
        AppLogger.info("Game", "$gameName：触发返回")
        onBackRequest()
    }
    val handleTogglePause: () -> Unit = {
        FeedbackController.tap()
        AppLogger.info("Game", "$gameName：切换暂停，当前=${if (isPaused) "暂停" else "运行"}")
        onTogglePause()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = handleBackRequest)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                title = {
                    Text(
                        text = gameName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBackRequest) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (onSaveRequest != null) {
                        IconButton(onClick = {
                            FeedbackController.tap()
                            onSaveRequest()
                            scope.launch {
                                snackbarHostState.showSnackbar("已保存当前进度")
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "保存游戏",
                            )
                        }
                    }
                    if (onHelpRequest != null) {
                        IconButton(onClick = {
                            FeedbackController.tap()
                            onHelpRequest()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "查看玩法说明",
                            )
                        }
                    }
                    if (canPause) {
                        IconButton(
                            onClick = handleTogglePause,
                            modifier = Modifier.semantics {
                                contentDescription = if (isPaused) "继续游戏" else "暂停游戏"
                            },
                        ) {
                            if (isPaused) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                )
                            } else {
                                PauseGlyph()
                            }
                        }
                    }
                },
            )
                GameStatusBar(
                    score = score,
                    bestScore = bestScore,
                    scoreLabel = scoreLabel,
                    bestScoreLabel = bestScoreLabel,
                    extraStats = extraStats,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            content = content,
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHelpSheet(
    gameName: String,
    helpLines: List<String>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "$gameName 玩法",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            helpLines.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    FeedbackController.tap()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(text = "知道了")
            }
        }
    }
}

@Composable
private fun GameStatusBar(
    score: Int,
    bestScore: Int,
    scoreLabel: String,
    bestScoreLabel: String,
    extraStats: List<GameStatusItem>,
) {
    val items = buildList {
        add(GameStatusItem(label = scoreLabel, value = score.toString()))
        add(GameStatusItem(label = bestScoreLabel, value = bestScore.toString()))
        addAll(extraStats)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useGrid = maxWidth < 600.dp && items.size > 4
            if (useGrid) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    items.chunked(3).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { item ->
                                GameStatusCell(
                                    item = item,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                GameStatusRow(
                    items = items,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun GameStatusRow(
    items: List<GameStatusItem>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(12.dp))
            }
            GameStatusCell(
                item = item,
                compact = compact,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GameStatusCell(
    item: GameStatusItem,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val isExpanded = LocalConfiguration.current.screenWidthDp >= 600
    Column(modifier = modifier) {
        Text(
            text = item.value,
            style = when {
                compact -> MaterialTheme.typography.titleSmall
                isExpanded -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 游戏内容的统一安全留白。横屏或大屏会使用更宽的外边距。
 */
@Composable
fun GameContentContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth >= 600.dp) 24.dp else 16.dp
        val verticalPadding = if (maxHeight < 420.dp) 8.dp else 12.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            content = content,
        )
    }
}

/**
 * 游戏棋盘的统一容器：Material 3 surface、细边框和均匀内边距。
 */
@Composable
fun GameBoardFrame(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 8.dp,
    clipContent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        tonalElevation = 1.dp,
    ) {
        val contentModifier = if (clipContent) {
            Modifier
                .padding(contentPadding)
                .clip(MaterialTheme.shapes.extraLarge)
        } else {
            Modifier.padding(contentPadding)
        }
        Box(
            modifier = contentModifier,
            content = content,
        )
    }
}
