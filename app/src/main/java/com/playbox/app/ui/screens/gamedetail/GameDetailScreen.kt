package com.playbox.app.ui.screens.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.SoftCard
import com.playbox.app.domain.model.GameId
import com.playbox.app.domain.model.GameInfo
import com.playbox.app.games.GameRegistry
import com.playbox.app.ui.components.EmptyState
import com.playbox.app.ui.components.GameGlyph
import com.playbox.app.ui.components.HighScoreBadge
import com.playbox.app.ui.components.hasScore
import com.playbox.app.ui.util.TrackScreen
import com.playbox.app.diagnostics.TrackRecomposition
import com.playbox.app.ui.util.ScrollProbe

@Composable
fun GameDetailRouteScreen(
    container: AppContainer,
    gameSlug: String,
    onBack: () -> Unit,
    onStartGame: (GameId) -> Unit,
) {
    val viewModel: GameDetailViewModel = viewModel(
        factory = GameDetailViewModel.factory(
            gameSlug = gameSlug,
            statsRepository = container.gameStatsRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    TrackScreen("游戏详情")

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is GameDetailEvent.FavoriteChanged -> {
                    if (event.isFavorite) {
                        "已收藏《${event.gameName}》"
                    } else {
                        "已取消收藏《${event.gameName}》"
                    }
                }

                GameDetailEvent.OperationFailed -> "操作失败，请稍后再试"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    GameDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleFavorite = viewModel::toggleFavorite,
        onStartGame = {
            uiState.game?.id?.let(onStartGame)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    uiState: GameDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStartGame: () -> Unit,
) {
    TrackRecomposition("GameDetailScreen")
    val scrollState = rememberScrollState()
    ScrollProbe(screen = "游戏详情") { scrollState.isScrollInProgress }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TrackRecomposition("GameDetailTopBar")
            TopAppBar(
                title = { Text(text = "游戏详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回游戏大厅",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        TrackRecomposition("GameDetailContent")
        val game = uiState.game
        if (game == null) {
            EmptyState(
                title = "找不到这款游戏",
                message = "它可能已经下架，返回游戏大厅看看其他游戏吧。",
                actionLabel = "返回大厅",
                onAction = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = if (LocalConfiguration.current.screenWidthDp >= 600) {
                            32.dp
                        } else {
                            16.dp
                        },
                    ),
            ) {
            Spacer(modifier = Modifier.height(8.dp))
            GameHero(
                game = game,
                isFavorite = uiState.isFavorite,
                onToggleFavorite = onToggleFavorite,
            )
            Spacer(modifier = Modifier.height(20.dp))
            GameStatsCard(uiState = uiState, game = game)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "玩法规则",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    game.rules.forEachIndexed { index, rule ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(8.dp),
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primary,
                                content = {},
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = rule,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            val isPlayable = GameRegistry.isPlayable(game.id)
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isPlayable) "准备开始" else "即将开放",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPlayable) {
                            "开始后最高分会自动保存在本机，随时可以再来一局。"
                        } else {
                            "玩法引擎将在后续阶段接入，先试试已经开放的游戏吧。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onStartGame,
                        enabled = isPlayable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(text = if (isPlayable) "开始游戏" else "游戏即将开放")
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun GameHero(
    game: GameInfo,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        GameGlyph(gameId = game.id, size = 88.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = game.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = game.category.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                AssistChip(
                    onClick = onToggleFavorite,
                    label = {
                        Text(text = if (isFavorite) "已收藏" else "收藏")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun GameStatsCard(
    uiState: GameDetailUiState,
    game: GameInfo,
) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (game.hasScore(uiState.highScore)) "本机最佳成绩" else "最高分",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HighScoreBadge(game = game, score = uiState.highScore)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "游玩次数",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${uiState.playCount} 局",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "正在读取本机成绩…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
