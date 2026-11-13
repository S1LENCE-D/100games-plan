package com.playbox.app.ui.screens.nearby

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.R
import com.playbox.app.games.ballbattle.BallAttribute
import com.playbox.app.games.nearby.NearbyGame
import com.playbox.app.games.chess.ChessSquare
import com.playbox.app.games.xiangqi.XiangqiPoint
import com.playbox.app.games.nearby.NearbyPeer
import com.playbox.app.games.nearby.NearbySignalQuality
import com.playbox.app.games.nearby.NearbyPlayer
import com.playbox.app.games.nearby.NearbyStage
import com.playbox.app.games.nearby.NearbyUiState
import com.playbox.app.games.nearby.NearbyViewModel
import com.playbox.app.games.nearby.RouletteEngine
import com.playbox.app.games.nearby.RoulettePull
import com.playbox.app.games.nearby.RpsChoice
import com.playbox.app.games.nearby.RpsOutcome
import com.playbox.app.ui.components.SoftCard
import com.playbox.app.ui.util.TrackScreen

private enum class NearbyExitMode {
    LEAVE_GAME,
    LEAVE_ROOM,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyRouteScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: NearbyViewModel = viewModel(
        factory = NearbyViewModel.factory(
            application,
            container.settingsRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bluetoothManager = remember {
        context.getSystemService(BluetoothManager::class.java)
    }
    val adapter = bluetoothManager?.adapter
    var bluetoothEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var exitMode by remember { mutableStateOf<NearbyExitMode?>(null) }

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    LaunchedEffect(Unit) {
        bluetoothEnabled = adapter?.isEnabled == true
    }
    LaunchedEffect(uiState.roomDissolved) {
        if (uiState.roomDissolved) {
            Toast.makeText(
                context,
                uiState.dissolveNotice ?: "房主已退出，房间已解散",
                Toast.LENGTH_SHORT,
            ).show()
            onBack()
        }
    }

    val requestBack: () -> Unit = {
        val inMultiplayer = when (uiState.stage) {
            NearbyStage.IDLE,
            NearbyStage.HOSTING,
            NearbyStage.SCANNING,
            NearbyStage.CONNECTING,
            -> false
            else -> true
        }
        if (inMultiplayer) {
            exitMode = if (uiState.stage == NearbyStage.CONNECTED) {
                NearbyExitMode.LEAVE_ROOM
            } else {
                NearbyExitMode.LEAVE_GAME
            }
        } else {
            viewModel.cancel()
            onBack()
        }
    }

    BackHandler { requestBack() }

    TrackScreen("附近联机")
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "附近联机") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回大厅",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val scrollLobby = when (uiState.stage) {
            NearbyStage.IDLE,
            NearbyStage.HOSTING,
            NearbyStage.SCANNING,
            NearbyStage.CONNECTING,
            NearbyStage.CONNECTED,
            -> true
            else -> false
        }
        val lobbyScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(
                    if (scrollLobby) {
                        Modifier.verticalScroll(lobbyScrollState)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            when {
                adapter == null -> InfoCard(
                    title = "设备不支持蓝牙",
                    message = "附近联机需要支持 BLE 的 Android 设备。",
                )

                !bluetoothEnabled -> InfoCard(
                    title = "蓝牙未开启",
                    message = "开启蓝牙后，才能广播或搜索附近的 100Games 房间。",
                ) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }
                        },
                    ) { Text(text = "打开蓝牙设置") }
                    TextButton(onClick = { bluetoothEnabled = adapter?.isEnabled == true }) {
                        Text(text = "已开启，重新检查")
                    }
                }

                !permissionsGranted -> InfoCard(
                    title = "需要蓝牙权限",
                    message = "附近联机使用蓝牙扫描和广播发现朋友设备；不会上传位置或游戏内容。",
                ) {
                    Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                        Text(text = "授予蓝牙权限")
                    }
                }

                else -> NearbyContent(
                    state = uiState,
                    onHost = viewModel::startHost,
                    onScan = viewModel::startScan,
                    onConnect = viewModel::connect,
                    onCancel = viewModel::cancel,
                    onDisconnect = {
                        exitMode = if (uiState.stage == NearbyStage.CONNECTED) {
                            NearbyExitMode.LEAVE_ROOM
                        } else {
                            NearbyExitMode.LEAVE_GAME
                        }
                    },
                    onChooseGame = viewModel::chooseGame,
                    onRoomNameChange = viewModel::updateRoomName,
                    onRps = viewModel::selectRps,
                    onNextRps = viewModel::nextRpsRound,
                    onTtt = viewModel::selectTttCell,
                    onNextTtt = viewModel::nextTttRound,
                    onGomokuCell = viewModel::selectGomokuCell,
                    onNextGomoku = viewModel::nextGomokuRound,
                    onChessSquare = viewModel::tapChessSquare,
                    onNextChess = viewModel::nextChessRound,
                    onXiangqiPoint = viewModel::tapXiangqiPoint,
                    onNextXiangqi = viewModel::nextXiangqiRound,
                    onUnoPlay = viewModel::tapUnoCard,
                    onUnoDraw = viewModel::drawUnoCard,
                    onNextUno = viewModel::nextUnoRound,
                    onDdzBid = viewModel::bidDdz,
                    onDdzPlay = viewModel::playDdz,
                    onDdzPass = viewModel::passDdz,
                    onNextDdz = viewModel::nextDdzRound,
                    onPullRoulette = viewModel::pullRoulette,
                    onRematchRoulette = viewModel::rematchRoulette,
                    onBombGuess = viewModel::submitBombGuess,
                    onBombRematch = viewModel::rematchBomb,
                    onAddBall = viewModel::addBallPoint,
                    onRemoveBall = viewModel::removeBallPoint,
                    onRandomBall = viewModel::randomizeBallBuild,
                    onReadyBall = viewModel::readyBall,
                    onLeaveBall = viewModel::leaveBallBattle,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    val mode = exitMode
    if (mode != null) {
        AlertDialog(
            onDismissRequest = { exitMode = null },
            title = { Text("退出联机游戏？") },
            text = {
                Text(
                    if (mode == NearbyExitMode.LEAVE_ROOM) {
                        "退出后会断开与附近玩家的连接，并返回主页。"
                    } else {
                        "退出当前游戏后会回到游戏选择界面，联机连接会保留。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exitMode = null
                        when (mode) {
                            NearbyExitMode.LEAVE_ROOM -> {
                                viewModel.disconnect()
                                onBack()
                            }
                            NearbyExitMode.LEAVE_GAME -> viewModel.leaveGame()
                        }
                    },
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { exitMode = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun NearbyContent(
    state: NearbyUiState,
    onHost: () -> Unit,
    onScan: () -> Unit,
    onConnect: (NearbyPeer) -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onChooseGame: (NearbyGame) -> Unit,
    onRps: (RpsChoice) -> Unit,
    onNextRps: () -> Unit,
    onTtt: (Int) -> Unit,
    onNextTtt: () -> Unit,
    onGomokuCell: (Int) -> Unit,
    onNextGomoku: () -> Unit,
    onChessSquare: (ChessSquare) -> Unit,
    onNextChess: () -> Unit,
    onXiangqiPoint: (XiangqiPoint) -> Unit,
    onNextXiangqi: () -> Unit,
    onUnoPlay: (Int, com.playbox.app.games.uno.UnoColor?) -> Unit,
    onUnoDraw: () -> Unit,
    onNextUno: () -> Unit,
    onDdzBid: (Int) -> Unit,
    onDdzPlay: (List<Int>) -> Unit,
    onDdzPass: () -> Unit,
    onNextDdz: () -> Unit,
    onPullRoulette: () -> Unit,
    onRematchRoulette: () -> Unit,
    onBombGuess: (Int) -> Unit,
    onBombRematch: () -> Unit,
    onAddBall: (BallAttribute) -> Unit,
    onRemoveBall: (BallAttribute) -> Unit,
    onRandomBall: () -> Unit,
    onReadyBall: () -> Unit,
    onLeaveBall: () -> Unit,
    onRoomNameChange: (String) -> Unit,
) {
    if (state.stage != NearbyStage.IDLE &&
        state.stage != NearbyStage.HOSTING &&
        state.stage != NearbyStage.SCANNING &&
        state.stage != NearbyStage.CONNECTING
    ) {
        SignalQualityChip(state.signalQuality, state.signalRssi)
        Spacer(modifier = Modifier.height(10.dp))
    }
    when (state.stage) {
        NearbyStage.IDLE -> NearbyLobby(
            state = state,
            onHost = onHost,
            onScan = onScan,
            onRoomNameChange = onRoomNameChange,
        )
        NearbyStage.HOSTING -> WaitingCard(
            title = "正在广播房间",
            message = "房间「${state.roomName}」正在等待附近玩家。",
            onCancel = onCancel,
        )
        NearbyStage.SCANNING -> ScanContent(
            peers = state.peers,
            onConnect = onConnect,
            onRescan = onScan,
            onCancel = onCancel,
        )
        NearbyStage.CONNECTING -> WaitingCard(
            title = "正在连接",
            message = "请保持两台设备靠近。",
            onCancel = onCancel,
        )
        NearbyStage.CONNECTED -> ConnectedLobby(
            state = state,
            onChooseGame = onChooseGame,
            onDisconnect = onDisconnect,
        )
        NearbyStage.RPS -> RpsContent(
            state = state,
            onChoice = onRps,
            onNext = onNextRps,
            onDisconnect = onDisconnect,
        )
        NearbyStage.TIC_TAC_TOE -> TttContent(
            state = state,
            onCell = onTtt,
            onNext = onNextTtt,
            onDisconnect = onDisconnect,
        )
        NearbyStage.GOMOKU -> NearbyGomokuContent(
            state = state,
            onCell = onGomokuCell,
            onNext = onNextGomoku,
            onDisconnect = onDisconnect,
        )
        NearbyStage.CHESS -> NearbyChessContent(
            state = state,
            onSquare = onChessSquare,
            onNext = onNextChess,
            onDisconnect = onDisconnect,
        )
        NearbyStage.XIANGQI -> NearbyXiangqiContent(
            state = state,
            onPoint = onXiangqiPoint,
            onNext = onNextXiangqi,
            onDisconnect = onDisconnect,
        )
        NearbyStage.UNO -> NearbyUnoContent(
            state = state,
            onPlay = onUnoPlay,
            onDraw = onUnoDraw,
            onNext = onNextUno,
            onDisconnect = onDisconnect,
        )
        NearbyStage.DDZ -> NearbyDdzContent(
            state = state,
            onBid = onDdzBid,
            onPlay = onDdzPlay,
            onPass = onDdzPass,
            onNext = onNextDdz,
            onDisconnect = onDisconnect,
        )
        NearbyStage.ROULETTE -> RouletteContent(
            state = state,
            onPull = onPullRoulette,
            onRematch = onRematchRoulette,
            onDisconnect = onDisconnect,
        )
        NearbyStage.BOMB -> BombContent(
            state = state,
            onGuess = onBombGuess,
            onRematch = onBombRematch,
            onDisconnect = onDisconnect,
        )
        NearbyStage.BALL -> NearbyBallContent(
            state = state,
            onAdd = onAddBall,
            onRemove = onRemoveBall,
            onRandom = onRandomBall,
            onReady = onReadyBall,
            onLeave = onLeaveBall,
            onDisconnect = onDisconnect,
        )
    }
}

@Composable
private fun NearbyLobby(
    state: NearbyUiState,
    onHost: () -> Unit,
    onScan: () -> Unit,
    onRoomNameChange: (String) -> Unit,
) {
    InfoCard(
        title = "和朋友在附近玩",
        message = "通过蓝牙低功耗广播配对，不需要联网。联机内容只用于本局游戏同步。",
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = state.roomName,
        onValueChange = onRoomNameChange,
        label = { Text(text = "房间名称") },
        supportingText = {
            Column {
                Text(text = "创建房间时显示给附近玩家，最多 8 个字符")
                Text(
                    text = "请勿使用违反当地法律法规的名称。",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(12.dp))
    GameEntryCard(
        title = "创建房间",
        description = "广播一个临时房间，等待附近玩家加入。",
        onClick = onHost,
        icon = Icons.Filled.AddCircle,
    )
    Spacer(modifier = Modifier.height(12.dp))
    GameEntryCard(
        title = "寻找附近玩家",
        description = "扫描附近正在广播的 100Games 房间。",
        onClick = onScan,
        icon = Icons.Filled.Search,
    )
}

@Composable
private fun ScanContent(
    peers: List<NearbyPeer>,
    onConnect: (NearbyPeer) -> Unit,
    onRescan: () -> Unit,
    onCancel: () -> Unit,
) {
    InfoCard(
        title = "正在寻找附近玩家",
        message = if (peers.isEmpty()) {
            "请让朋友先创建房间，并保持蓝牙开启。"
        } else {
            "找到 ${peers.size} 个房间，点击即可连接。"
        },
        showProgress = peers.isEmpty(),
    )
    Spacer(modifier = Modifier.height(12.dp))
    peers.forEach { peer ->
        SoftCard(
            onClick = { onConnect(peer) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = peer.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "信号 ${peer.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "重新扫描")
        }
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text(text = "取消")
        }
    }
}

@Composable
private fun WaitingCard(
    title: String,
    message: String,
    onCancel: () -> Unit,
) {
    InfoCard(title = title, message = message, showProgress = true)
    Spacer(modifier = Modifier.height(12.dp))
    TextButton(onClick = onCancel) { Text(text = "取消") }
}

@Composable
private fun ConnectedLobby(
    state: NearbyUiState,
    onChooseGame: (NearbyGame) -> Unit,
    onDisconnect: () -> Unit,
) {
    InfoCard(
        title = "已连接 ${state.playerCount} 人",
        message = "房间「${state.roomName}」· ${state.connectedPeer.orEmpty()} · ${if (state.isHost) "房主" else "客人"}",
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = if (state.playerCount >= 3) "多人游戏" else "选择双人游戏",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(10.dp))
    if (state.playerCount >= 3) {
        if (state.isHost) {
            GameEntryCard(
                title = "联机斗地主",
                description = "3 人叫地主、出牌，先出完手牌的一方获胜。",
                onClick = { onChooseGame(NearbyGame.DDZ) },
                centered = true,
                icon = Icons.Filled.Groups,
            )
            Spacer(modifier = Modifier.height(12.dp))
            GameEntryCard(
                title = "多人数字炸弹",
                description = "3–6 人轮流猜 1–100，踩中炸弹的玩家出局。",
                onClick = { onChooseGame(NearbyGame.BOMB) },
                centered = true,
                icon = Icons.Filled.Groups,
            )
        } else {
            InfoCard(
                title = "等待房主开始",
                message = "房主会在人数到齐后选择多人数字炸弹。",
            )
        }
    } else if (!state.isHost) {
        InfoCard(
            title = "等待房主选择游戏",
            message = "房主开始游戏后，你会自动进入对应界面。",
        )
    } else {
        GameEntryCard(
            title = "石头剪子布",
            description = "同时出拳，一局定胜负。",
            onClick = { onChooseGame(NearbyGame.RPS) },
            centered = true,
            icon = Icons.Filled.SportsEsports,
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameEntryCard(
            title = "联机五子棋",
            description = "15×15 棋盘，轮流落子，先连成五子者获胜。",
            onClick = { onChooseGame(NearbyGame.GOMOKU) },
            centered = true,
            icon = Icons.Filled.GridOn,
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameEntryCard(
            title = "联机国际象棋",
            description = "房主执白先手，标准 8×8 棋盘。",
            onClick = { onChooseGame(NearbyGame.CHESS) },
            centered = true,
            icon = Icons.Filled.EmojiEvents,
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameEntryCard(
            title = "联机中国象棋",
            description = "房主执红先手，标准 9×10 棋盘。",
            onClick = { onChooseGame(NearbyGame.XIANGQI) },
            centered = true,
            icon = Icons.Filled.Map,
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameEntryCard(
            title = "联机 UNO",
            description = "双人轮流出牌，先出完手牌者获胜。",
            onClick = { onChooseGame(NearbyGame.UNO) },
            centered = true,
            icon = Icons.Filled.SportsEsports,
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameEntryCard(
            title = "双人井字棋",
            description = "轮流落子，先连成三子者获胜。",
            onClick = { onChooseGame(NearbyGame.TIC_TAC_TOE) },
            centered = true,
            icon = Icons.Filled.Apps,
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameEntryCard(
            title = "小球对对碰",
            description = "各自加点，两个自定义球碰撞决胜负。",
            onClick = { onChooseGame(NearbyGame.BALL) },
            centered = true,
            icon = Icons.Filled.BubbleChart,
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameEntryCard(
            title = "虚拟俄罗斯轮盘",
            description = "6 个弹巢，轮流扣动，纯虚拟娱乐。",
            onClick = { onChooseGame(NearbyGame.ROULETTE) },
            centered = true,
            icon = Icons.Filled.Casino,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    TextButton(onClick = onDisconnect) { Text(text = "断开连接") }
}

@Composable
private fun RpsContent(
    state: NearbyUiState,
    onChoice: (RpsChoice) -> Unit,
    onNext: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val rps = state.rps
    InfoCard(
        title = "石头剪子布",
        message = "和附近的朋友同时选择；双方都出拳后自动判定。",
    )
    Spacer(modifier = Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RpsChoice.entries.forEach { choice ->
            FilledTonalButton(
                onClick = { onChoice(choice) },
                enabled = rps.localChoice == null && rps.outcome == null,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
            ) {
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(18.dp))
    val revealed = rps.outcome != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RpsChoicePanel(
            label = "你",
            value = rps.localChoice?.label ?: "未选择",
            modifier = Modifier.weight(1f),
        )
        RpsChoicePanel(
            label = "对方",
            value = when {
                revealed -> rps.remoteChoice?.label ?: "未选择"
                rps.remoteChoice != null -> "已选择"
                else -> "选择中"
            },
            modifier = Modifier.weight(1f),
        )
    }
    rps.outcome?.let { outcome ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when (outcome) {
                RpsOutcome.WIN -> "你赢了"
                RpsOutcome.LOSE -> "对方赢了"
                RpsOutcome.DRAW -> "平局"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(text = "再来一局")
        }
    }
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text(text = "断开连接")
    }
}

@Composable
private fun RpsChoicePanel(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RouletteContent(
    state: NearbyUiState,
    onPull: () -> Unit,
    onRematch: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val roulette = state.roulette
    val myTurn = (state.isHost && roulette.turn == NearbyPlayer.HOST) ||
        (!state.isHost && roulette.turn == NearbyPlayer.GUEST)
    val loserIsMe = (state.isHost && roulette.loser == NearbyPlayer.HOST) ||
        (!state.isHost && roulette.loser == NearbyPlayer.GUEST)

    InfoCard(
        title = "虚拟轮盘",
        message = "6 个弹巢，1 个虚拟“砰”。轮流扣动，纯娱乐。",
    )
    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(RouletteEngine.CHAMBER_COUNT) { index ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < roulette.chamber) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = when {
            roulette.isOver && loserIsMe -> "砰！这一局你输了"
            roulette.isOver -> "砰！对方输了，你赢了"
            myTurn -> "轮到你了"
            else -> "等待对方扣动扳机"
        },
        style = MaterialTheme.typography.titleLarge,
        color = if (roulette.isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "已扣动 ${roulette.chamber}/6 次" +
            when (roulette.lastResult) {
                RoulettePull.SAFE -> " · 上一枪是空的"
                RoulettePull.BANG -> " · 上一枪是“砰”"
                null -> ""
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onPull,
        enabled = !roulette.isOver && myTurn && !roulette.waiting,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text = if (roulette.waiting) "等待结果…" else "扣动扳机")
    }
    if (roulette.isOver) {
        Spacer(modifier = Modifier.height(8.dp))
        if (state.isHost) {
            OutlinedButton(onClick = onRematch, modifier = Modifier.fillMaxWidth()) {
                Text(text = "再来一局")
            }
        } else {
            Text(
                text = "等待房主开始下一局",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text(text = "断开连接")
    }
}

@Composable
private fun SignalQualityChip(
    quality: NearbySignalQuality,
    rssi: Int?,
) {
    val color = when (quality) {
        NearbySignalQuality.GOOD -> Color(0xFF4CAF50)
        NearbySignalQuality.FAIR -> Color(0xFFFFB300)
        NearbySignalQuality.POOR -> Color(0xFFE53935)
        NearbySignalQuality.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when (quality) {
        NearbySignalQuality.GOOD -> "信号良好"
        NearbySignalQuality.FAIR -> "信号一般"
        NearbySignalQuality.POOR -> "信号较差"
        NearbySignalQuality.UNKNOWN -> "信号检测中"
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SignalCellularAlt,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
            rssi?.let {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$it dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GameEntryCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    centered: Boolean = false,
    icon: ImageVector = Icons.Filled.Person,
) {
    SoftCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        if (centered) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun InfoCard(
    title: String,
    message: String,
    showProgress: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            actions()
        }
    }
}
