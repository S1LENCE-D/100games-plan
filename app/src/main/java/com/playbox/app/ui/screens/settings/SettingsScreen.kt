package com.playbox.app.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.BuildConfig
import com.playbox.app.ui.components.SoftCard
import com.playbox.app.domain.model.AppColorPalette
import com.playbox.app.domain.model.ThemeMode
import com.playbox.app.ui.theme.seedColor
import com.playbox.app.ui.util.TrackScreen
import com.playbox.app.diagnostics.TrackRecomposition
import com.playbox.app.ui.util.ScrollProbe

@Composable
fun SettingsRouteScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenDonation: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settingsRepository = container.settingsRepository,
            statsRepository = container.gameStatsRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    TrackScreen("设置")

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.RecordsCleared -> "所有游戏记录已清除"
                SettingsEvent.OperationFailed -> "操作失败，请稍后再试"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    SettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onThemeModeSelected = viewModel::setThemeMode,
        onColorPaletteSelected = viewModel::setColorPalette,
        onDynamicColorChanged = viewModel::setDynamicColorEnabled,
        onSoundEnabledChanged = viewModel::setSoundEnabled,
        onHapticsEnabledChanged = viewModel::setHapticsEnabled,
        onPredictiveBackChanged = viewModel::setPredictiveBackEnabled,
        onClearRecords = viewModel::clearGameRecords,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenDonation = onOpenDonation,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onColorPaletteSelected: (AppColorPalette) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onHapticsEnabledChanged: (Boolean) -> Unit,
    onPredictiveBackChanged: (Boolean) -> Unit,
    onClearRecords: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenDonation: () -> Unit,
) {
    TrackRecomposition("SettingsScreen")
    val uriHandler = LocalUriHandler.current
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    ScrollProbe(screen = "设置") { scrollState.isScrollInProgress }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TrackRecomposition("SettingsTopBar")
            TopAppBar(
                title = { Text(text = "设置") },
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
        TrackRecomposition("SettingsContent")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = if (LocalConfiguration.current.screenWidthDp >= 600) {
                            24.dp
                        } else {
                            16.dp
                        },
                    ),
            ) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle(text = "外观")
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    ThemeModeRow(
                        mode = mode,
                        selected = uiState.settings.themeMode == mode,
                        onClick = { onThemeModeSelected(mode) },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ToggleSettingRow(
                        title = "动态取色",
                        description = if (uiState.settings.dynamicColorEnabled) {
                            "使用壁纸颜色生成 Material You 配色（Android 12+）"
                        } else {
                            "使用下方自定义调色板生成配色"
                        },
                        checked = uiState.settings.dynamicColorEnabled,
                        onCheckedChange = onDynamicColorChanged,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SettingsSectionTitle(text = "自定义调色板")
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                ) {
                ColorPaletteChooser(
                    selected = uiState.settings.colorPalette,
                    dynamicColorEnabled = uiState.settings.dynamicColorEnabled,
                    onSelect = onColorPaletteSelected,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SettingsSectionTitle(text = "声音与触感")
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                ) {
                ToggleSettingRow(
                    title = "音效",
                    description = "点击、移动、合并、消除与胜负提示音",
                    checked = uiState.settings.soundEnabled,
                    onCheckedChange = onSoundEnabledChanged,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ToggleSettingRow(
                    title = "震动反馈",
                    description = "点击、合并、消除与游戏结束的触感",
                    checked = uiState.settings.hapticsEnabled,
                    onCheckedChange = onHapticsEnabledChanged,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SettingsSectionTitle(text = "交互")
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                ToggleSettingRow(
                    title = "预见性返回手势",
                    description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "关闭后返回立即执行，不显示手势预测预览"
                    } else {
                        "当前系统低于 Android 13，不支持预见性返回"
                    },
                    checked = uiState.settings.predictiveBackEnabled,
                    onCheckedChange = onPredictiveBackChanged,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SettingsSectionTitle(text = "游戏记录")
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "清除本机数据",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "删除全部游戏的最高分、游玩次数、收藏和最近记录。主题与声音设置会保留。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = { showClearConfirmation = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "清空所有游戏记录")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SettingsSectionTitle(text = "诊断")
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "运行日志",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "记录掉帧、异常和页面切换。遇到卡顿或报错时，可在这里复制诊断报告。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onOpenDiagnostics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(text = "查看运行日志")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SettingsSectionTitle(text = "支持作者")
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onOpenDonation)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "请作者喝杯咖啡",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "自愿捐赠 · 支持后续游戏开发",
                            style = MaterialTheme.typography.bodyMedium,
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

            Spacer(modifier = Modifier.height(20.dp))
            SettingsSectionTitle(text = "关于")
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .size(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "100Games Plan",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "100游戏计划",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "使用 Kotlin、Jetpack Compose 与 Material 3 构建。Android 12 及以上支持壁纸动态取色；关闭动态取色或低版本系统时，使用所选的自定义调色板。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "开源协议：MIT License",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "github.com/S1LENCE-D/100games-plan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { uriHandler.openUri(REPO_URL) }) {
                        Text(text = "打开开源仓库")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "作者：@Eartecd",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "图标设计：@Anonymity_v",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "灵感策划：@十八阶",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "小恐龙素材：wayou/t-rex-runner · BSD-3-Clause",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "所有游戏成绩仅保存在本机。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                )
            },
            title = { Text(text = "清空所有游戏记录？") },
            text = {
                Text(
                    text = "最高分、游玩次数、收藏与最近游玩记录将被永久删除，此操作无法撤销。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearRecords()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = "清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(text = "取消")
                }
            },
        )
    }
}

private const val REPO_URL = "https://github.com/S1LENCE-D/100games-plan"

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun ColorPaletteChooser(
    selected: AppColorPalette,
    dynamicColorEnabled: Boolean,
    onSelect: (AppColorPalette) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "选择一套 Material 3 主题色调",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (dynamicColorEnabled) {
                "动态取色当前优先；点选调色板会自动关闭动态取色。"
            } else {
                "调色板会应用到大厅、设置与全部游戏界面。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 4.dp
            val swatchSize = (((maxWidth - gap * 4) / 5f) * 0.62f)
                .coerceIn(44.dp, 64.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                AppColorPalette.entries.forEach { palette ->
                    PaletteOption(
                        palette = palette,
                        selected = selected == palette,
                        swatchSize = swatchSize,
                        onClick = { onSelect(palette) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteOption(
    palette: AppColorPalette,
    selected: Boolean,
    swatchSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "paletteBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 1.dp,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "paletteBorderWidth",
    )
    val swatchScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "paletteScale",
    )
    Column(
        modifier = modifier
            .heightIn(min = 80.dp)
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(swatchSize)
                .scale(swatchScale)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = CircleShape,
                )
                .padding(if (selected) 5.dp else 4.dp)
                .clip(CircleShape)
                .background(palette.seedColor),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = palette.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

private fun AppColorPalette.displayName(): String = when (this) {
    AppColorPalette.BRAND -> "品牌紫"
    AppColorPalette.OCEAN -> "海蓝"
    AppColorPalette.FOREST -> "森林"
    AppColorPalette.SUNSET -> "日落"
    AppColorPalette.BERRY -> "莓果"
}

@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mode.title(),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mode.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

private fun ThemeMode.title(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun ThemeMode.description(): String = when (this) {
    ThemeMode.SYSTEM -> "根据系统的深浅色设置自动切换"
    ThemeMode.LIGHT -> "始终使用浅色 Material 3 配色"
    ThemeMode.DARK -> "始终使用深色 Material 3 配色"
}
