package com.playbox.app.games.nearby

import com.playbox.app.games.gomoku.GomokuEngine
import com.playbox.app.games.gomoku.GomokuMove
import com.playbox.app.games.gomoku.GomokuPoint
import com.playbox.app.games.chess.ChessColor
import com.playbox.app.games.chess.ChessEngine
import com.playbox.app.games.chess.ChessMove
import com.playbox.app.games.chess.ChessOutcome
import com.playbox.app.games.chess.ChessPiece
import com.playbox.app.games.chess.ChessPieceType
import com.playbox.app.games.chess.ChessSquare
import com.playbox.app.games.xiangqi.XiangqiColor
import com.playbox.app.games.xiangqi.XiangqiEngine
import com.playbox.app.games.xiangqi.XiangqiMove
import com.playbox.app.games.xiangqi.XiangqiOutcome
import com.playbox.app.games.xiangqi.XiangqiPiece
import com.playbox.app.games.xiangqi.XiangqiPoint
import com.playbox.app.games.uno.UnoCard
import com.playbox.app.games.uno.UnoColor
import com.playbox.app.games.uno.UnoKind
import com.playbox.app.games.doudizhu.DdzCard
import com.playbox.app.games.doudizhu.DdzPhase
import com.playbox.app.games.doudizhu.DdzPlay
import com.playbox.app.games.doudizhu.DdzSnapshot
import com.playbox.app.games.doudizhu.DdzSuit
import com.playbox.app.games.doudizhu.classifyDdz
import java.util.UUID
import kotlin.random.Random

object NearbyProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("d5b1f8e0-7c4a-4f6e-9b2d-1a3c5e7f9a11")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("d5b1f8e1-7c4a-4f6e-9b2d-1a3c5e7f9a11")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEFAULT_ROOM_NAME = "我的房间"
    const val ROOM_MANUFACTURER_ID = 0xFFFF
    const val MAX_ROOM_NAME_BYTES = 24
    const val GAME_RPS = "rps"
    const val GAME_ROULETTE = "rou"
    const val GAME_TTT = "ttt"
    const val GAME_BOMB = "bomb"
    const val GAME_BALL = "ball"
    const val GAME_GOMOKU = "gomoku"
    const val GAME_CHESS = "chess"
    const val GAME_XIANGQI = "xiangqi"
    const val GAME_UNO = "uno"
    const val GAME_DDZ = "ddz"

    fun encodeRoomName(name: String): ByteArray {
        val trimmed = name.trim().ifBlank { DEFAULT_ROOM_NAME }
        val builder = StringBuilder()
        for (char in trimmed) {
            val next = builder.toString() + char
            if (next.toByteArray(Charsets.UTF_8).size > MAX_ROOM_NAME_BYTES) break
            builder.append(char)
        }
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    fun decodeRoomName(bytes: ByteArray): String =
        String(bytes, Charsets.UTF_8).trim()
}

enum class NearbyGame(val wire: String) {
    RPS(NearbyProtocol.GAME_RPS),
    ROULETTE(NearbyProtocol.GAME_ROULETTE),
    TIC_TAC_TOE(NearbyProtocol.GAME_TTT),
    BOMB(NearbyProtocol.GAME_BOMB),
    BALL(NearbyProtocol.GAME_BALL),
    GOMOKU(NearbyProtocol.GAME_GOMOKU),
    CHESS(NearbyProtocol.GAME_CHESS),
    XIANGQI(NearbyProtocol.GAME_XIANGQI),
    UNO(NearbyProtocol.GAME_UNO),
    DDZ(NearbyProtocol.GAME_DDZ),
    ;

    companion object {
        fun fromWire(value: String): NearbyGame? = entries.firstOrNull { it.wire == value }
    }
}

enum class NearbyPlayer(val wire: String) {
    HOST("H"),
    GUEST("G"),
    ;

    fun other(): NearbyPlayer = if (this == HOST) GUEST else HOST

    companion object {
        fun fromWire(value: String): NearbyPlayer? = entries.firstOrNull { it.wire == value }
    }
}

enum class RpsChoice(val label: String, val wire: String) {
    ROCK("石头", "R"),
    PAPER("布", "P"),
    SCISSORS("剪刀", "S"),
    ;

    fun beats(other: RpsChoice): Boolean = when (this) {
        ROCK -> other == SCISSORS
        PAPER -> other == ROCK
        SCISSORS -> other == PAPER
    }

    companion object {
        fun fromWire(value: String): RpsChoice? = entries.firstOrNull { it.wire == value }
    }
}

enum class RpsOutcome {
    WIN,
    LOSE,
    DRAW,
}

fun judgeRps(local: RpsChoice, remote: RpsChoice): RpsOutcome = when {
    local == remote -> RpsOutcome.DRAW
    local.beats(remote) -> RpsOutcome.WIN
    else -> RpsOutcome.LOSE
}

enum class RoulettePull {
    SAFE,
    BANG,
}

/**
 * 虚拟俄罗斯轮盘赌：6 个弹巢，其中 1 个为虚拟“砰”。
 * 只用于纯娱乐，不涉及任何真实危险物品。
 */
class RouletteEngine(
    private val random: Random = Random.Default,
) {
    private var bulletChamber = random.nextInt(CHAMBER_COUNT)
    private var pulled = 0
    var turn: NearbyPlayer = NearbyPlayer.HOST
        private set
    var isOver: Boolean = false
        private set
    var loser: NearbyPlayer? = null
        private set

    val chamber: Int get() = pulled

    fun reset() {
        bulletChamber = random.nextInt(CHAMBER_COUNT)
        pulled = 0
        turn = NearbyPlayer.HOST
        isOver = false
        loser = null
    }

    fun pull(): RoulettePull {
        if (isOver) return RoulettePull.SAFE
        val result = if (pulled == bulletChamber) RoulettePull.BANG else RoulettePull.SAFE
        pulled += 1
        if (result == RoulettePull.BANG) {
            isOver = true
            loser = turn
        } else {
            turn = turn.other()
        }
        return result
    }

    companion object {
        const val CHAMBER_COUNT = 6
    }
}


/**
 * 双人井字棋：host 执 X 先手，guest 执 O。
 * 双方只发送落子位置，棋盘由两端按相同规则推进。
 */
class TttMatch(
    val localMark: Int,
) {
    private val cells = MutableList(9) { 0 }
    var winner: Int = 0
        private set
    var isDraw: Boolean = false
        private set

    val board: List<Int> get() = cells.toList()
    val isGameOver: Boolean get() = winner != 0 || isDraw

    fun isLocalTurn(): Boolean {
        if (isGameOver) return false
        val nextMark = if (cells.count { it != 0 } % 2 == 0) 1 else 2
        return nextMark == localMark
    }

    fun playLocal(index: Int): Boolean {
        if (index !in 0..8 || cells[index] != 0 || !isLocalTurn()) return false
        cells[index] = localMark
        evaluate()
        return true
    }

    fun playRemote(index: Int): Boolean {
        if (index !in 0..8 || cells[index] != 0 || isGameOver) return false
        val nextMark = if (cells.count { it != 0 } % 2 == 0) 1 else 2
        if (nextMark == localMark) return false
        cells[index] = nextMark
        evaluate()
        return true
    }

    fun reset() {
        cells.fill(0)
        winner = 0
        isDraw = false
    }

    private fun evaluate() {
        val lines = listOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
        )
        for (line in lines) {
            val first = cells[line[0]]
            if (first != 0 && first == cells[line[1]] && first == cells[line[2]]) {
                winner = first
                return
            }
        }
        if (cells.none { it == 0 }) isDraw = true
    }
}


/**
 * 联机五子棋：host 执黑先手，guest 执白。
 * 只同步落子坐标，棋盘由两端使用同一个确定性引擎推进。
 */
class GomokuMatch(
    val localMark: Int,
) {
    private var engine = GomokuEngine()

    val board: List<Int> get() = engine.board
    val currentPlayer: Int get() = engine.currentPlayer
    val winner: Int get() = engine.winner
    val isDraw: Boolean get() = engine.isDraw
    val lastMove: GomokuMove? get() = engine.lastMove
    val winningLine: List<GomokuPoint> get() = engine.winningLine
    val isGameOver: Boolean get() = engine.isGameOver

    fun isLocalTurn(): Boolean = !engine.isGameOver && engine.currentPlayer == localMark

    fun playLocal(row: Int, column: Int): Boolean {
        if (!isLocalTurn()) return false
        return engine.place(row, column)
    }

    fun playRemote(row: Int, column: Int): Boolean {
        if (engine.isGameOver) return false
        return engine.place(row, column)
    }

    fun reset() {
        engine = GomokuEngine()
    }
}

/**
 * 联机国际象棋：房主执白先手，客人执黑。
 * 只同步最终落子；升变统一选择后。
 */
class ChessMatch(
    val localColor: ChessColor,
) {
    private var engine = ChessEngine()
    var selected: ChessSquare? = null
        private set
    var legalTargets: List<ChessSquare> = emptyList()
        private set

    val board: List<ChessPiece?> get() = (0..63).map { engine.pieceAt(it / 8, it % 8) }
    val sideToMove: ChessColor get() = engine.sideToMove
    val lastMove: ChessMove? get() = engine.lastMove
    val outcome: ChessOutcome get() = engine.status.outcome
    val winner: ChessColor? get() = engine.status.winner
    val inCheck: Boolean get() = engine.status.inCheck
    val moveCount: Int get() = engine.moveCount
    val isGameOver: Boolean get() = engine.status.isGameOver
    val isLocalTurn: Boolean get() = !isGameOver && sideToMove == localColor

    fun tap(square: ChessSquare): ChessMove? {
        if (!isLocalTurn) return null
        val current = selected
        if (current == null) {
            val piece = engine.pieceAt(square.row, square.column) ?: return null
            if (piece.color != localColor) return null
            selected = square
            legalTargets = engine.legalMovesFrom(square).map { it.to }.distinct()
            return null
        }
        if (current == square) {
            clearSelection()
            return null
        }
        val candidates = engine.legalMovesFrom(current).filter { it.to == square }
        if (candidates.isEmpty()) {
            val piece = engine.pieceAt(square.row, square.column)
            if (piece != null && piece.color == localColor) {
                selected = square
                legalTargets = engine.legalMovesFrom(square).map { it.to }.distinct()
            }
            return null
        }
        clearSelection()
        val move = candidates.firstOrNull { it.promotion == ChessPieceType.QUEEN } ?: candidates.first()
        return if (engine.makeMove(move)) move else null
    }

    fun legalMovesFrom(square: ChessSquare): List<ChessMove> = engine.legalMovesFrom(square)

    fun applyRemote(move: ChessMove): Boolean {
        if (isGameOver) return false
        return engine.makeMove(move)
    }

    fun reset() {
        engine = ChessEngine()
        clearSelection()
    }

    private fun clearSelection() {
        selected = null
        legalTargets = emptyList()
    }
}

/** 联机中国象棋：房主执红先手，客人执黑。 */
class XiangqiMatch(val localColor: XiangqiColor) {
    private var engine = XiangqiEngine()
    var selected: XiangqiPoint? = null
        private set
    var legalTargets: List<XiangqiPoint> = emptyList()
        private set
    val board: List<XiangqiPiece?> get() = (0..89).map { engine.pieceAt(it / 9, it % 9) }
    val sideToMove: XiangqiColor get() = engine.sideToMove
    val lastMove: XiangqiMove? get() = engine.lastMove
    val outcome: XiangqiOutcome get() = engine.status.outcome
    val winner: XiangqiColor? get() = engine.status.winner
    val inCheck: Boolean get() = engine.status.inCheck
    val moveCount: Int get() = engine.moveCount
    val isGameOver: Boolean get() = engine.status.isGameOver
    val isLocalTurn: Boolean get() = !isGameOver && sideToMove == localColor

    fun tap(point: XiangqiPoint): XiangqiMove? {
        if (!isLocalTurn) return null
        val current = selected
        if (current == null) {
            val piece = engine.pieceAt(point.row, point.column) ?: return null
            if (piece.color != localColor) return null
            selected = point
            legalTargets = engine.legalMovesFrom(point).map { it.to }.distinct()
            return null
        }
        if (current == point) { clear(); return null }
        val candidates = engine.legalMovesFrom(current).filter { it.to == point }
        if (candidates.isEmpty()) {
            val piece = engine.pieceAt(point.row, point.column)
            if (piece != null && piece.color == localColor) {
                selected = point
                legalTargets = engine.legalMovesFrom(point).map { it.to }.distinct()
            }
            return null
        }
        clear()
        val move = candidates.first()
        return if (engine.makeMove(move)) move else null
    }

    fun legalMovesFrom(point: XiangqiPoint): List<XiangqiMove> = engine.legalMovesFrom(point)
    fun applyRemote(move: XiangqiMove): Boolean = if (isGameOver) false else engine.makeMove(move)
    fun reset() { engine = XiangqiEngine(); clear() }
    private fun clear() { selected = null; legalTargets = emptyList() }
}

/** 双人联机 UNO 的紧凑状态，避免超过 BLE 单包载荷。 */
data class UnoOnlineState(
    val hands: List<List<UnoCard>> = listOf(emptyList(), emptyList()),
    val top: UnoCard? = null,
    val currentColor: UnoColor = UnoColor.WILD,
    val currentPlayer: Int = 0,
    val drawPileSize: Int = 0,
    val winner: Int? = null,
    val playableIndices: List<Int> = emptyList(),
)

object UnoWire {
    fun encode(state: UnoOnlineState): String = buildString {
        append("US|")
        append(state.hands.joinToString(";") { hand -> hand.joinToString(",") { cardCode(it) } })
        append('|')
        append(state.top?.let(::cardCode).orEmpty())
        append('|').append(state.currentColor.ordinal)
        append('|').append(state.currentPlayer)
        append('|').append(state.drawPileSize)
        append('|').append(state.winner ?: -1)
        append('|').append(state.playableIndices.joinToString(","))
    }

    fun decode(message: String): Pair<UnoOnlineState, Int>? {
        val parts = message.split('|')
        if (parts.size < 8 || parts[0] != "US") return null
        val hands = parts[1].split(';').map { hand ->
            if (hand.isBlank()) emptyList() else hand.split(',').mapNotNull(::decodeCard)
        }
        val top = decodeCard(parts[2])
        val color = UnoColor.entries.getOrNull(parts[3].toIntOrNull() ?: -1) ?: return null
        val current = parts[4].toIntOrNull() ?: return null
        val drawPile = parts[5].toIntOrNull() ?: return null
        val winner = (parts[6].toIntOrNull() ?: -1).takeIf { it >= 0 }
        val playable = parts.getOrNull(7)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        return UnoOnlineState(
            hands = hands,
            top = top,
            currentColor = color,
            currentPlayer = current,
            drawPileSize = drawPile,
            winner = winner,
            playableIndices = playable,
        ) to message.length
    }

    fun cardCode(card: UnoCard): String {
        val number = ((card.number ?: -1) + 1).coerceIn(0, 99)
        return "${card.color.ordinal}${card.kind.ordinal}${number.toString().padStart(2, '0')}${card.id}"
    }

    private fun decodeHand(value: String): List<UnoCard> =
        if (value.isBlank()) emptyList() else value.split(',').mapNotNull(::decodeCard)

    fun decodeCard(value: String): UnoCard? {
        if (value.length < 5) return null
        val color = UnoColor.entries.getOrNull(value[0] - '0') ?: return null
        val kind = UnoKind.entries.getOrNull(value[1] - '0') ?: return null
        val number = value.substring(2, 4).toIntOrNull()?.minus(1) ?: return null
        val id = value.substring(4).toIntOrNull() ?: return null
        return UnoCard(
            id = id,
            color = color,
            kind = kind,
            number = number.takeIf { it >= 0 },
        )
    }
}

/** 斗地主联机的紧凑快照；房主是唯一权威端。 */
object DdzWire {
    fun cardCode(card: DdzCard): String = "${card.rank}.${card.suit.ordinal}.${card.id}"

    fun encode(s: DdzSnapshot): String = buildString {
        append("DS")
        append('|').append(s.phase.ordinal)
        append('|').append(s.hands.joinToString(";") { h -> h.joinToString(",") { cardCode(it) } })
        append('|').append(s.bottomCards.joinToString(",") { cardCode(it) })
        append('|').append(if (s.bottomRevealed) 1 else 0)
        append('|').append(s.currentPlayer)
        append('|').append(s.currentBidder)
        append('|').append(s.highestBid)
        append('|').append(s.highestBidder)
        append('|').append(s.landlord)
        append('|').append(s.lastPlay?.player ?: -1)
        append('|').append(s.lastPlay?.cards?.joinToString(",") { cardCode(it) }.orEmpty())
        append('|').append(s.passCount)
        append('|').append(s.multiplier)
        append('|').append(s.winner ?: -1)
        append('|').append(s.landlordWon?.let { if (it) 1 else 0 } ?: -1)
        append('|').append(s.lastAction)
    }

    fun decode(message: String): DdzSnapshot? {
        val p = message.split('|')
        if (p.size < 17 || p[0] != "DS") return null
        val phase = DdzPhase.entries.getOrNull(p[1].toIntOrNull() ?: -1) ?: return null
        val hands = p[2].split(';').map { h -> h.split(',').mapNotNull(::decodeCard) }
        val bottom = p[3].split(',').mapNotNull(::decodeCard)
        val lastCards = p[11].split(',').mapNotNull(::decodeCard)
        val lastPlayer = p[10].toIntOrNull() ?: -1
        val lastPlay = if (lastPlayer >= 0 && lastCards.isNotEmpty()) {
            val combo = classifyDdz(lastCards) ?: return null
            DdzPlay(lastPlayer, lastCards, combo)
        } else null
        return DdzSnapshot(
            phase = phase,
            hands = hands,
            bottomCards = bottom,
            bottomRevealed = p[4] == "1",
            currentPlayer = p[5].toIntOrNull() ?: 0,
            currentBidder = p[6].toIntOrNull() ?: 0,
            highestBid = p[7].toIntOrNull() ?: 0,
            highestBidder = p[8].toIntOrNull() ?: -1,
            landlord = p[9].toIntOrNull() ?: -1,
            lastPlay = lastPlay,
            passCount = p[12].toIntOrNull() ?: 0,
            multiplier = p[13].toIntOrNull() ?: 1,
            lastAction = p.getOrNull(16).orEmpty(),
            winner = (p[14].toIntOrNull() ?: -1).takeIf { it >= 0 },
            landlordWon = p[15].toIntOrNull()?.takeIf { it >= 0 }?.let { it == 1 },
            spring = false,
            antiSpring = false,
        )
    }

    fun decodeCard(value: String): DdzCard? {
        val p = value.split('.')
        if (p.size != 3) return null
        val rank = p[0].toIntOrNull() ?: return null
        val suit = DdzSuit.entries.getOrNull(p[1].toIntOrNull() ?: -1) ?: return null
        val id = p[2].toIntOrNull() ?: return null
        return DdzCard(id, rank, suit)
    }
}
