package com.playbox.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.domain.model.AchievementProgress
import com.playbox.app.domain.model.GameCatalog
import com.playbox.app.domain.model.GameId
import com.playbox.app.ui.components.EmptyState
import com.playbox.app.ui.components.GameCard
import com.playbox.app.ui.components.SoftCard
import kotlinx.coroutines.flow.Flow
import com.playbox.app.ui.util.TrackScreen
import com.playbox.app.diagnostics.TrackRecomposition
import com.playbox.app.ui.util.ScrollProbe

@Composable
fun HomeRouteScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenGame: (GameId) -> Unit,
    onOpenNearby: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(container.gameStatsRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    TrackScreen("首页")

    ShowHomeSnackbars(events = viewModel.events, snackbarHostState = snackbarHostState)

    HomeScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSettingsClick = onOpenSettings,
        onGameClick = onOpenGame,
        onToggleFavorite = viewModel::toggleFavorite,
        onFilterSelected = viewModel::selectFilter,
        onRandomGame = {
            GameCatalog.all.randomOrNull()?.let { game -> onOpenGame(game.id) }
        },
        onOpenNearby = onOpenNearby,
    )
}

@Composable
private fun ShowHomeSnackbars(
    events: Flow<HomeEvent>,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(events, snackbarHostState) {
        events.collect { event ->
            val message = when (event) {
                is HomeEvent.FavoriteChanged -> {
                    if (event.isFavorite) {
                        "已收藏《${event.gameName}》"
                    } else {
                        "已取消收藏《${event.gameName}》"
                    }
                }

                HomeEvent.OperationFailed -> "操作失败，请稍后再试"
            }
            snackbarHostState.showSnackbar(message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onSettingsClick: () -> Unit,
    onGameClick: (GameId) -> Unit,
    onToggleFavorite: (GameId) -> Unit,
    onFilterSelected: (HomeFilter) -> Unit,
    onRandomGame: () -> Unit,
    onOpenNearby: () -> Unit,
) {
    TrackRecomposition("HomeScreen")
    var showAchievements by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val columnCount = when {
        configuration.screenWidthDp < 600 -> 2
        configuration.screenWidthDp < 900 -> 3
        configuration.screenWidthDp < 1200 -> 4
        else -> 5
    }
    val horizontalContentPadding = if (configuration.screenWidthDp >= 600) 24.dp else 16.dp
    val gridState = rememberLazyGridState()
    ScrollProbe(screen = "首页") { gridState.isScrollInProgress }

    LaunchedEffect(uiState.filter) {
        if (gridState.firstVisibleItemIndex > 0) {
            gridState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TrackRecomposition("HomeTopBar")
            // 使用标准高度顶栏：标题紧贴系统栏下方，减少顶部空白，也降低首帧测量成本。
            TopAppBar(
                title = {
                    Text(
                        text = "100Games Plan",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "打开设置",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRandomGame,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                },
                text = { Text(text = "随机开一局") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        TrackRecomposition("HomeContent")
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(
                start = horizontalContentPadding,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = horizontalContentPadding,
                bottom = innerPadding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(
                key = "stats",
                contentType = "stats",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                HomeStatsCard(
                    uiState = uiState,
                    onShowAchievements = { showAchievements = true },
                )
            }

            item(
                key = "nearby",
                contentType = "nearby",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                NearbyEntryCard(onClick = onOpenNearby)
            }

            item(
                key = "filters",
                contentType = "filters",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "游戏库",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${uiState.games.size} 款",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        HomeFilter.entries.forEachIndexed { index, filter ->
                            SegmentedButton(
                                selected = uiState.filter == filter,
                                onClick = { onFilterSelected(filter) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = HomeFilter.entries.size,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                icon = {},
                                label = {
                                    Text(
                                        text = filter.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    item(
                        key = "loading",
                        contentType = "loading",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                uiState.games.isEmpty() -> {
                    item(
                        key = "empty",
                        contentType = "empty",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        val emptyCopy = emptyStateCopy(uiState.filter)
                        EmptyState(
                            title = emptyCopy.title,
                            message = emptyCopy.message,
                            actionLabel = "查看全部游戏",
                            onAction = { onFilterSelected(HomeFilter.ALL) },
                        )
                    }
                }

                else -> {
                    items(
                        items = uiState.games,
                        key = { item -> item.game.id.slug },
                        contentType = { "game" },
                    ) { item ->
                        GameCard(
                            game = item.game,
                            highScore = item.highScore,
                            isFavorite = item.isFavorite,
                            playCount = item.playCount,
                            onClick = { onGameClick(item.game.id) },
                            onToggleFavorite = { onToggleFavorite(item.game.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAchievements) {
        AchievementSheet(
            achievements = uiState.achievements,
            onDismiss = { showAchievements = false },
        )
    }
}

@Composable
private fun NearbyEntryCard(onClick: () -> Unit) {
    SoftCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "附近联机", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "蓝牙配对 · 猜拳 / 井字棋 / 轮盘 / 小球 / 数字炸弹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeStatsCard(
    uiState: HomeUiState,
    onShowAchievements: () -> Unit,
) {
    SoftCard(
        onClick = onShowAchievements,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "欢迎来到 100Games Plan",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "100游戏计划 · 小游戏合集，持续更新。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.76f),
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatMetric(
                    value = uiState.totalPlays.toString(),
                    label = "总游玩次数",
                    modifier = Modifier.weight(1f),
                )
                StatMetric(
                    value = "${uiState.unlockedAchievementCount}/${uiState.achievements.size}",
                    label = "已解锁成就",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { uiState.achievementFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = LocalContentColor.current.copy(alpha = 0.18f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "成就进度",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "查看全部",
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StatMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.72f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementSheet(
    achievements: List<AchievementProgress>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val unlockedCount = achievements.count { it.isUnlocked }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "成就",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "已解锁 $unlockedCount/${achievements.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = {
                    if (achievements.isEmpty()) 0f else unlockedCount.toFloat() / achievements.size
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(modifier = Modifier.height(16.dp))
            achievements.forEachIndexed { index, progress ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                AchievementRow(progress = progress)
            }
        }
    }
}

@Composable
private fun AchievementRow(progress: AchievementProgress) {
    val unlocked = progress.isUnlocked
    val iconColor = if (unlocked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = if (unlocked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = iconColor,
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Filled.Check else Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier
                    .padding(12.dp)
                    .size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = progress.achievement.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = progress.achievement.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (unlocked) "已解锁" else "${progress.current}/${progress.target}",
            style = MaterialTheme.typography.labelMedium,
            color = if (unlocked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private data class EmptyCopy(
    val title: String,
    val message: String,
)

private fun emptyStateCopy(filter: HomeFilter): EmptyCopy = when (filter) {
    HomeFilter.ALL -> EmptyCopy(
        title = "游戏库暂时为空",
        message = "稍后再来看看吧。",
    )

    HomeFilter.FAVORITES -> EmptyCopy(
        title = "还没有收藏",
        message = "点击游戏卡片右上角的心形按钮，把喜欢的游戏放进收藏。",
    )

    HomeFilter.RECENT -> EmptyCopy(
        title = "还没有最近记录",
        message = "开始一局游戏后，它会自动出现在这里。",
    )
}
