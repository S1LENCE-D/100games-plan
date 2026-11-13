package com.playbox.app.games.nearby

import androidx.compose.runtime.Immutable
import com.playbox.app.games.ballbattle.BallBattleSnapshot
import com.playbox.app.games.ballbattle.BallBuild
import com.playbox.app.games.gomoku.GomokuMove
import com.playbox.app.games.gomoku.GomokuPoint
import com.playbox.app.games.chess.ChessColor
import com.playbox.app.games.chess.ChessMove
import com.playbox.app.games.chess.ChessOutcome
import com.playbox.app.games.chess.ChessPiece
import com.playbox.app.games.chess.ChessSquare
import com.playbox.app.games.xiangqi.XiangqiColor
import com.playbox.app.games.xiangqi.XiangqiMove
import com.playbox.app.games.xiangqi.XiangqiOutcome
import com.playbox.app.games.xiangqi.XiangqiPiece
import com.playbox.app.games.xiangqi.XiangqiPoint

enum class NearbySignalQuality {
    UNKNOWN,
    GOOD,
    FAIR,
    POOR,
}

fun signalQualityFor(rssi: Int?): NearbySignalQuality = when {
    rssi == null -> NearbySignalQuality.UNKNOWN
    rssi >= -65 -> NearbySignalQuality.GOOD
    rssi >= -80 -> NearbySignalQuality.FAIR
    else -> NearbySignalQuality.POOR
}

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
    BALL,
    GOMOKU,
    CHESS,
    XIANGQI,
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
data class NearbyBallUiState(
    val build: BallBuild = BallBuild(),
    val remoteBuild: BallBuild? = null,
    val localReady: Boolean = false,
    val remoteReady: Boolean = false,
    val snapshot: BallBattleSnapshot? = null,
    val localFinished: Boolean = false,
    val remoteFinished: Boolean = false,
    val remoteWinner: Int? = null,
    val winner: Int? = null,
    val isFinished: Boolean = false,
)

@Immutable
data class NearbyXiangqiUiState(
    val board: List<XiangqiPiece?> = emptyList(),
    val sideToMove: XiangqiColor = XiangqiColor.RED,
    val selected: XiangqiPoint? = null,
    val legalTargets: List<XiangqiPoint> = emptyList(),
    val lastMove: XiangqiMove? = null,
    val outcome: XiangqiOutcome = XiangqiOutcome.ONGOING,
    val winner: XiangqiColor? = null,
    val inCheck: Boolean = false,
    val moveCount: Int = 0,
) {
    val isGameOver: Boolean get() = outcome != XiangqiOutcome.ONGOING
}

@Immutable
data class NearbyChessUiState(
    val board: List<ChessPiece?> = emptyList(),
    val sideToMove: ChessColor = ChessColor.WHITE,
    val selected: ChessSquare? = null,
    val legalTargets: List<ChessSquare> = emptyList(),
    val lastMove: ChessMove? = null,
    val outcome: ChessOutcome = ChessOutcome.ONGOING,
    val winner: ChessColor? = null,
    val inCheck: Boolean = false,
    val moveCount: Int = 0,
) {
    val isGameOver: Boolean get() = outcome != ChessOutcome.ONGOING
}

@Immutable
data class NearbyGomokuUiState(
    val board: List<Int> = List(225) { 0 },
    val currentPlayer: Int = 1,
    val winner: Int = 0,
    val isDraw: Boolean = false,
    val lastMove: GomokuMove? = null,
    val winningLine: List<GomokuPoint> = emptyList(),
) {
    val isGameOver: Boolean get() = winner != 0 || isDraw
}

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
    val ball: NearbyBallUiState = NearbyBallUiState(),
    val gomoku: NearbyGomokuUiState = NearbyGomokuUiState(),
    val chess: NearbyChessUiState = NearbyChessUiState(),
    val xiangqi: NearbyXiangqiUiState = NearbyXiangqiUiState(),
    val signalRssi: Int? = null,
    val signalQuality: NearbySignalQuality = NearbySignalQuality.UNKNOWN,
)
