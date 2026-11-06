package com.playbox.app.games.nearby

import androidx.compose.runtime.Immutable

enum class NearbyStage {
    IDLE,
    HOSTING,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RPS,
    ROULETTE,
    TIC_TAC_TOE,
    BOMB,
}

@Immutable
data class NearbyPeer(
    val name: String,
    val address: String,
    val rssi: Int,
)

@Immutable
data class RpsUiState(
    val localChoice: RpsChoice? = null,
    val remoteChoice: RpsChoice? = null,
    val outcome: RpsOutcome? = null,
)

@Immutable
data class RouletteUiState(
    val turn: NearbyPlayer = NearbyPlayer.HOST,
    val chamber: Int = 0,
    val lastResult: RoulettePull? = null,
    val loser: NearbyPlayer? = null,
    val isOver: Boolean = false,
    val waiting: Boolean = false,
)

@Immutable
data class BombUiState(
    val low: Int = 1,
    val high: Int = 100,
    val turn: Int = 0,
    val lastGuess: Int? = null,
    val lastPlayer: Int? = null,
    val loser: Int? = null,
    val isOver: Boolean = false,
    val waiting: Boolean = false,
)

@Immutable
data class TttUiState(
    val board: List<Int> = List(9) { 0 },
    val winner: Int = 0,
    val isDraw: Boolean = false,
)

@Immutable
data class NearbyUiState(
    val stage: NearbyStage = NearbyStage.IDLE,
    val peers: List<NearbyPeer> = emptyList(),
    val connectedPeer: String? = null,
    val isHost: Boolean = false,
    val playerCount: Int = 1,
    val myIndex: Int = 0,
    val error: String? = null,
    val roomName: String = NearbyProtocol.DEFAULT_ROOM_NAME,
    val roomDissolved: Boolean = false,
    val dissolveNotice: String? = null,
    val game: NearbyGame? = null,
    val rps: RpsUiState = RpsUiState(),
    val roulette: RouletteUiState = RouletteUiState(),
    val ttt: TttUiState = TttUiState(),
    val bomb: BombUiState = BombUiState(),
)
