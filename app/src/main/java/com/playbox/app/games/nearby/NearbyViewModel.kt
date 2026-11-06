package com.playbox.app.games.nearby

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playbox.app.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 附近联机：BLE 发现/配对 + 双人游戏 + 3–6 人数字炸弹。
 * 房主是游戏状态权威端，蓝牙消息只用于本局同步。
 */
class NearbyViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application) {
    private var host: NearbyBleHost? = null
    private var client: NearbyBleClient? = null
    private val hostGuests = linkedMapOf<String, String>()
    private var rouletteEngine: RouletteEngine? = null
    private var tttMatch: TttMatch? = null
    private var closing = false
    private var roomLocked = false
    private var roomNameLoaded = false
    private val random = Random.Default

    private var bombSecret = 0
    private var bombLow = 1
    private var bombHigh = 100
    private var bombTurn = 0
    private var bombLast: Int? = null
    private var bombLastPlayer: Int? = null
    private var bombLoser: Int? = null
    private var bombOver = false

    private val _uiState = MutableStateFlow(NearbyUiState())
    val uiState: StateFlow<NearbyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                if (!roomNameLoaded) {
                    roomNameLoaded = true
                    _uiState.update { it.copy(roomName = settings.nearbyRoomName.take(8)) }
                }
            }
        }
    }

    fun updateRoomName(name: String) {
        if (_uiState.value.stage != NearbyStage.IDLE) return
        roomNameLoaded = true
        val trimmed = name.take(8)
        _uiState.update { it.copy(roomName = trimmed) }
        viewModelScope.launch { settingsRepository.setNearbyRoomName(trimmed) }
    }

    fun startHost() {
        stopTransport()
        resetGameState()
        hostGuests.clear()
        roomLocked = false
        val name = _uiState.value.roomName.ifBlank { NearbyProtocol.DEFAULT_ROOM_NAME }
        _uiState.value = NearbyUiState(
            stage = NearbyStage.HOSTING,
            isHost = true,
            roomName = name,
            playerCount = 1,
            myIndex = 0,
        )
        val newHost = NearbyBleHost(
            context = getApplication(),
            roomName = name,
            onPeerConnected = ::onHostPeerConnected,
            onDisconnected = ::onHostPeerDisconnected,
            onMessage = ::handleMessage,
            onError = ::showError,
        )
        host = newHost
        if (!newHost.start()) {
            host = null
            _uiState.update { it.copy(stage = NearbyStage.IDLE) }
        }
    }

    fun startScan() {
        stopTransport()
        resetGameState()
        _uiState.value = NearbyUiState(stage = NearbyStage.SCANNING, isHost = false)
        val newClient = NearbyBleClient(
            context = getApplication(),
            onPeers = { peers ->
                if (_uiState.value.stage == NearbyStage.SCANNING) {
                    _uiState.update { it.copy(peers = peers) }
                }
            },
            onConnected = { name ->
                _uiState.update {
                    it.copy(
                        stage = NearbyStage.CONNECTED,
                        connectedPeer = name,
                        isHost = false,
                        playerCount = 2,
                        myIndex = 1,
                        error = null,
                        peers = emptyList(),
                    )
                }
                client?.send("H")
            },
            onDisconnected = ::onGuestDisconnected,
            onMessage = { message -> handleMessage(message, null) },
            onError = ::showError,
        )
        client = newClient
        if (!newClient.startScan()) {
            client = null
            _uiState.update { it.copy(stage = NearbyStage.IDLE) }
        }
    }

    fun connect(peer: NearbyPeer) {
        val client = client ?: return
        _uiState.update {
            it.copy(
                stage = NearbyStage.CONNECTING,
                connectedPeer = peer.name,
            )
        }
        client.connect(peer)
    }

    fun cancel() {
        notifyHostExit()
        stopTransport()
        resetGameState()
        hostGuests.clear()
        _uiState.value = NearbyUiState()
    }

    fun disconnect() {
        notifyHostExit()
        stopTransport()
        resetGameState()
        hostGuests.clear()
        _uiState.value = NearbyUiState()
    }

    fun chooseGame(game: NearbyGame) {
        val state = _uiState.value
        if (state.stage != NearbyStage.CONNECTED) return
        if (game == NearbyGame.BOMB) {
            if (!state.isHost || state.playerCount < 3) return
        } else {
            if (state.playerCount != 2) return
        }
        val stage = when (game) {
            NearbyGame.RPS -> NearbyStage.RPS
            NearbyGame.ROULETTE -> NearbyStage.ROULETTE
            NearbyGame.TIC_TAC_TOE -> NearbyStage.TIC_TAC_TOE
            NearbyGame.BOMB -> NearbyStage.BOMB
        }
        if (state.isHost) lockRoomForGame()
        _uiState.update {
            it.copy(
                stage = stage,
                game = game,
                rps = RpsUiState(),
                roulette = RouletteUiState(),
                ttt = TttUiState(),
                bomb = BombUiState(),
                error = null,
            )
        }
        send("G|${game.wire}")
        when (game) {
            NearbyGame.TIC_TAC_TOE -> startTttMatch()
            NearbyGame.ROULETTE -> if (state.isHost) startRouletteHost()
            NearbyGame.BOMB -> startBombHost()
            NearbyGame.RPS -> Unit
        }
    }

    fun selectRps(choice: RpsChoice) {
        val state = _uiState.value
        if (state.stage != NearbyStage.RPS || state.rps.localChoice != null || state.rps.outcome != null) return
        _uiState.update { it.copy(rps = it.rps.copy(localChoice = choice)) }
        send("C|${choice.wire}")
        resolveRps()
    }

    fun nextRpsRound() {
        val state = _uiState.value
        if (state.stage != NearbyStage.RPS || state.rps.outcome == null) return
        _uiState.update { it.copy(rps = RpsUiState()) }
        send("N")
    }

    fun selectTttCell(index: Int) {
        val state = _uiState.value
        if (state.stage != NearbyStage.TIC_TAC_TOE) return
        val match = tttMatch ?: return
        if (!match.playLocal(index)) return
        send("T|M|$index")
        syncTtt()
    }

    fun nextTttRound() {
        val state = _uiState.value
        if (state.stage != NearbyStage.TIC_TAC_TOE) return
        if (!state.ttt.isDraw && state.ttt.winner == 0) return
        _uiState.update { it.copy(ttt = TttUiState()) }
        tttMatch?.reset()
        send("T|N")
    }

    fun pullRoulette() {
        val state = _uiState.value
        if (state.stage != NearbyStage.ROULETTE) return
        val roulette = state.roulette
        if (roulette.isOver || roulette.waiting) return
        if (state.isHost) {
            if (roulette.turn != NearbyPlayer.HOST) return
            hostPull()
        } else {
            if (roulette.turn != NearbyPlayer.GUEST) return
            if (send("R|P")) {
                _uiState.update { it.copy(roulette = it.roulette.copy(waiting = true)) }
            }
        }
    }

    fun rematchRoulette() {
        val state = _uiState.value
        if (state.stage != NearbyStage.ROULETTE || !state.roulette.isOver || !state.isHost) return
        startRouletteHost()
    }

    fun submitBombGuess(value: Int) {
        val state = _uiState.value
        if (state.stage != NearbyStage.BOMB || state.bomb.isOver) return
        if (value !in state.bomb.low..state.bomb.high) return
        if (state.bomb.turn != state.myIndex) return
        if (state.isHost) {
            processBombGuess(value, 0)
        } else {
            if (send("Q|$value")) {
                _uiState.update { it.copy(bomb = it.bomb.copy(waiting = true)) }
            }
        }
    }

    fun rematchBomb() {
        val state = _uiState.value
        if (state.stage != NearbyStage.BOMB || !state.bomb.isOver || !state.isHost) return
        startBombHost()
    }

    private fun onHostPeerConnected(name: String, address: String) {
        if (closing) return
        if (roomLocked) {
            host?.sendTo(address, "E|LOCKED")
            viewModelScope.launch {
                delay(350)
                host?.disconnect(address)
            }
            return
        }
        if (hostGuests.size >= 5) {
            host?.disconnect(address)
            showError("房间人数已满（最多 6 人）")
            return
        }
        hostGuests[address] = name
        resetGameState()
        _uiState.update {
            it.copy(
                stage = NearbyStage.CONNECTED,
                connectedPeer = hostGuests.values.firstOrNull() ?: name,
                isHost = true,
                playerCount = 1 + hostGuests.size,
                myIndex = 0,
                error = null,
                peers = emptyList(),
            )
        }
        broadcastRoster()
        send("X")
    }

    private fun onHostPeerDisconnected(address: String) {
        if (closing) return
        hostGuests.remove(address)
        resetGameState()
        val count = 1 + hostGuests.size
        if (roomLocked && count == 1) {
            stopTransport()
            _uiState.value = NearbyUiState(
                stage = NearbyStage.IDLE,
                error = "玩家已离开，房间已关闭",
            )
            return
        }
        _uiState.update {
            it.copy(
                stage = if (count >= 2) NearbyStage.CONNECTED else NearbyStage.HOSTING,
                connectedPeer = hostGuests.values.firstOrNull(),
                playerCount = count,
                error = if (count == 1) "玩家已断开，等待新的附近玩家" else null,
            )
        }
        if (count >= 2) {
            broadcastRoster()
            send("X")
        }
    }

    private fun onGuestDisconnected() {
        if (closing || _uiState.value.roomDissolved) return
        resetGameState()
        _uiState.value = NearbyUiState(
            stage = NearbyStage.IDLE,
            roomDissolved = true,
        )
    }

    private fun notifyHostExit() {
        if (_uiState.value.isHost) host?.send("D|CLOSE")
    }

    private fun broadcastRoster() {
        val count = 1 + hostGuests.size
        send("P|$count")
        hostGuests.keys.forEachIndexed { index, address ->
            host?.sendTo(address, "I|${index + 1}")
        }
    }

    private fun lockRoomForGame() {
        if (!_uiState.value.isHost || roomLocked) return
        roomLocked = true
        host?.stopAdvertising()
    }

    private fun startTttMatch() {
        tttMatch = TttMatch(localMark = if (_uiState.value.isHost) 1 else 2)
        _uiState.update {
            it.copy(
                stage = NearbyStage.TIC_TAC_TOE,
                game = NearbyGame.TIC_TAC_TOE,
                ttt = TttUiState(),
            )
        }
    }

    private fun syncTtt() {
        val match = tttMatch ?: return
        _uiState.update {
            it.copy(
                ttt = TttUiState(
                    board = match.board,
                    winner = match.winner,
                    isDraw = match.isDraw,
                ),
            )
        }
    }

    private fun startRouletteHost() {
        rouletteEngine = RouletteEngine()
        _uiState.update {
            it.copy(
                stage = NearbyStage.ROULETTE,
                game = NearbyGame.ROULETTE,
                roulette = RouletteUiState(turn = NearbyPlayer.HOST),
            )
        }
        send("R|START|H")
    }

    private fun hostPull() {
        val engine = rouletteEngine ?: return
        val result = engine.pull()
        val message = if (result == RoulettePull.BANG) {
            "R|B|${engine.loser?.wire ?: NearbyPlayer.HOST.wire}|${engine.chamber}"
        } else {
            "R|S|${engine.turn.wire}|${engine.chamber}"
        }
        send(message)
        _uiState.update {
            it.copy(
                roulette = RouletteUiState(
                    turn = engine.turn,
                    chamber = engine.chamber,
                    lastResult = result,
                    loser = engine.loser,
                    isOver = engine.isOver,
                    waiting = false,
                ),
            )
        }
    }

    private fun startBombHost() {
        val count = _uiState.value.playerCount
        if (!_uiState.value.isHost || count < 3) return
        bombSecret = random.nextInt(1, 101)
        bombLow = 1
        bombHigh = 100
        bombTurn = 0
        bombLast = null
        bombLastPlayer = null
        bombLoser = null
        bombOver = false
        _uiState.update {
            it.copy(
                stage = NearbyStage.BOMB,
                game = NearbyGame.BOMB,
                bomb = bombUi(),
            )
        }
        sendBombState()
    }

    private fun processBombGuess(value: Int, playerIndex: Int) {
        if (bombOver || playerIndex != bombTurn) return
        if (value !in bombLow..bombHigh) return
        bombLast = value
        bombLastPlayer = playerIndex
        if (value == bombSecret) {
            bombOver = true
            bombLoser = playerIndex
        } else {
            if (value < bombSecret) bombLow = (value + 1).coerceAtMost(bombHigh) else bombHigh = (value - 1).coerceAtLeast(bombLow)
            val count = 1 + hostGuests.size
            bombTurn = (bombTurn + 1) % count
        }
        sendBombState()
        _uiState.update { it.copy(bomb = bombUi()) }
    }

    private fun sendBombState() {
        send(
            "B|$bombLow|$bombHigh|$bombTurn|${bombLast ?: -1}|${bombLastPlayer ?: -1}|${bombLoser ?: -1}",
        )
    }

    private fun bombUi(): BombUiState = BombUiState(
        low = bombLow,
        high = bombHigh,
        turn = bombTurn,
        lastGuess = bombLast,
        lastPlayer = bombLastPlayer,
        loser = bombLoser,
        isOver = bombOver,
        waiting = false,
    )

    private fun resolveRps() {
        val state = _uiState.value
        val local = state.rps.localChoice ?: return
        val remote = state.rps.remoteChoice ?: return
        if (state.rps.outcome != null) return
        _uiState.update {
            it.copy(rps = it.rps.copy(outcome = judgeRps(local, remote)))
        }
    }

    private fun handleMessage(message: String, fromAddress: String?) {
        if (message.isBlank() || closing) return
        val parts = message.split('|')
        when (parts.firstOrNull()) {
            "H" -> Unit
            "D" -> {
                if (!_uiState.value.isHost) {
                    resetGameState()
                    _uiState.value = NearbyUiState(
                        stage = NearbyStage.IDLE,
                        roomDissolved = true,
                    )
                }
            }
            "E" -> {
                if (!_uiState.value.isHost) {
                    resetGameState()
                    _uiState.value = NearbyUiState(
                        stage = NearbyStage.IDLE,
                        roomDissolved = true,
                        dissolveNotice = "游戏已开始，无法加入",
                    )
                }
            }
            "X" -> {
                if (!_uiState.value.isHost) {
                    resetGameState()
                    _uiState.update {
                        it.copy(
                            stage = NearbyStage.CONNECTED,
                            error = null,
                        )
                    }
                }
            }
            "P" -> {
                val count = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (!_uiState.value.isHost) {
                    _uiState.update { it.copy(playerCount = count) }
                }
            }
            "I" -> {
                val index = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (!_uiState.value.isHost) {
                    _uiState.update { it.copy(myIndex = index) }
                }
            }
            "G" -> parts.getOrNull(1)?.let { wire ->
                val game = NearbyGame.fromWire(wire) ?: return
                if (game == NearbyGame.BOMB && _uiState.value.isHost) return
                val stage = when (game) {
                    NearbyGame.RPS -> NearbyStage.RPS
                    NearbyGame.ROULETTE -> NearbyStage.ROULETTE
                    NearbyGame.TIC_TAC_TOE -> NearbyStage.TIC_TAC_TOE
                    NearbyGame.BOMB -> NearbyStage.BOMB
                }
                if (_uiState.value.isHost) lockRoomForGame()
                _uiState.update {
                    it.copy(
                        stage = stage,
                        game = game,
                        rps = RpsUiState(),
                        roulette = RouletteUiState(),
                        ttt = TttUiState(),
                        bomb = BombUiState(),
                        error = null,
                    )
                }
                if (game == NearbyGame.TIC_TAC_TOE) startTttMatch()
            }
            "C" -> {
                val choice = parts.getOrNull(1)?.let(RpsChoice::fromWire) ?: return
                if (_uiState.value.stage != NearbyStage.RPS) return
                _uiState.update { it.copy(rps = it.rps.copy(remoteChoice = choice)) }
                resolveRps()
            }
            "N" -> {
                if (_uiState.value.stage == NearbyStage.RPS) {
                    _uiState.update { it.copy(rps = RpsUiState()) }
                }
            }
            "R" -> handleRouletteMessage(parts)
            "T" -> handleTttMessage(parts)
            "Q" -> {
                val value = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (!_uiState.value.isHost || fromAddress == null) return
                val playerIndex = hostGuests.keys.indexOf(fromAddress) + 1
                if (playerIndex <= 0) return
                processBombGuess(value, playerIndex)
            }
            "B" -> handleBombState(parts)
        }
    }

    private fun handleBombState(parts: List<String>) {
        if (_uiState.value.isHost) return
        val low = parts.getOrNull(1)?.toIntOrNull() ?: return
        val high = parts.getOrNull(2)?.toIntOrNull() ?: return
        val turn = parts.getOrNull(3)?.toIntOrNull() ?: return
        val last = parts.getOrNull(4)?.toIntOrNull()?.takeIf { it >= 0 }
        val lastPlayer = parts.getOrNull(5)?.toIntOrNull()?.takeIf { it >= 0 }
        val loser = parts.getOrNull(6)?.toIntOrNull()?.takeIf { it >= 0 }
        _uiState.update {
            it.copy(
                stage = NearbyStage.BOMB,
                bomb = BombUiState(
                    low = low,
                    high = high,
                    turn = turn,
                    lastGuess = last,
                    lastPlayer = lastPlayer,
                    loser = loser,
                    isOver = loser != null,
                    waiting = false,
                ),
            )
        }
    }

    private fun handleTttMessage(parts: List<String>) {
        when (parts.getOrNull(1)) {
            "M" -> {
                val index = parts.getOrNull(2)?.toIntOrNull() ?: return
                tttMatch?.playRemote(index)
                syncTtt()
            }
            "N" -> {
                tttMatch?.reset()
                _uiState.update { it.copy(ttt = TttUiState()) }
            }
        }
    }

    private fun handleRouletteMessage(parts: List<String>) {
        val command = parts.getOrNull(1) ?: return
        when (command) {
            "START" -> _uiState.update {
                it.copy(
                    stage = NearbyStage.ROULETTE,
                    roulette = RouletteUiState(turn = NearbyPlayer.HOST),
                )
            }
            "P" -> {
                if (_uiState.value.isHost && _uiState.value.roulette.turn == NearbyPlayer.GUEST) {
                    hostPull()
                }
            }
            "S" -> {
                val next = parts.getOrNull(2)?.let(NearbyPlayer::fromWire) ?: return
                val chamber = parts.getOrNull(3)?.toIntOrNull() ?: return
                _uiState.update {
                    it.copy(
                        roulette = it.roulette.copy(
                            turn = next,
                            chamber = chamber,
                            lastResult = RoulettePull.SAFE,
                            waiting = false,
                            isOver = false,
                        ),
                    )
                }
            }
            "B" -> {
                val loser = parts.getOrNull(2)?.let(NearbyPlayer::fromWire) ?: return
                val chamber = parts.getOrNull(3)?.toIntOrNull() ?: return
                _uiState.update {
                    it.copy(
                        roulette = it.roulette.copy(
                            chamber = chamber,
                            lastResult = RoulettePull.BANG,
                            loser = loser,
                            isOver = true,
                            waiting = false,
                        ),
                    )
                }
            }
        }
    }

    private fun send(message: String): Boolean {
        val sent = when {
            _uiState.value.isHost -> host?.send(message) == true
            else -> client?.send(message) == true
        }
        if (!sent) showError("蓝牙尚未连接")
        return sent
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    private fun resetGameState() {
        rouletteEngine = null
        tttMatch = null
        bombOver = false
        _uiState.update {
            it.copy(
                game = null,
                rps = RpsUiState(),
                roulette = RouletteUiState(),
                ttt = TttUiState(),
                bomb = BombUiState(),
            )
        }
    }

    private fun stopTransport() {
        closing = true
        host?.stop()
        client?.close()
        host = null
        client = null
        closing = false
    }

    override fun onCleared() {
        notifyHostExit()
        stopTransport()
        super.onCleared()
    }

    companion object {
        fun factory(
            application: Application,
            settingsRepository: SettingsRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { NearbyViewModel(application, settingsRepository) }
            }
    }
}
