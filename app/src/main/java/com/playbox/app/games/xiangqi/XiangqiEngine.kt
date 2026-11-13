package com.playbox.app.games.xiangqi

import androidx.compose.runtime.Immutable
import kotlin.math.abs

enum class XiangqiColor {
    RED,
    BLACK;

    fun other(): XiangqiColor = if (this == RED) BLACK else RED
}

enum class XiangqiPieceType {
    GENERAL,
    ADVISOR,
    ELEPHANT,
    HORSE,
    CHARIOT,
    CANNON,
    SOLDIER,
}

@Immutable
data class XiangqiPiece(
    val type: XiangqiPieceType,
    val color: XiangqiColor,
)

@Immutable
data class XiangqiPoint(
    val row: Int,
    val column: Int,
) {
    fun isValid(): Boolean = row in 0..9 && column in 0..8
}

@Immutable
data class XiangqiMove(
    val from: XiangqiPoint,
    val to: XiangqiPoint,
)

enum class XiangqiOutcome {
    ONGOING,
    CHECKMATE,
    STALEMATE,
    DRAW_REPETITION,
    DRAW_MOVE_LIMIT,
}

@Immutable
data class XiangqiStatus(
    val outcome: XiangqiOutcome,
    val winner: XiangqiColor?,
    val inCheck: Boolean,
) {
    val isGameOver: Boolean get() = outcome != XiangqiOutcome.ONGOING
}

private data class MoveRecord(
    val boardBefore: Array<XiangqiPiece?>,
    val sideBefore: XiangqiColor,
    val noCaptureBefore: Int,
    val lastMoveBefore: XiangqiMove?,
)

class XiangqiEngine private constructor(initialized: Boolean) {
    private var board: Array<XiangqiPiece?> = arrayOfNulls(90)
    private val history = mutableListOf<MoveRecord>()
    private val positionCounts = mutableMapOf<String, Int>()
    private var searchNodes = 0
    private var searchDeadline = 0L

    var sideToMove: XiangqiColor = XiangqiColor.RED
        private set
    var noCapturePlies: Int = 0
        private set
    var lastMove: XiangqiMove? = null
        private set
    var status: XiangqiStatus = XiangqiStatus(XiangqiOutcome.ONGOING, null, false)
        private set
    val moveCount: Int get() = history.size

    constructor() : this(false)

    init {
        if (!initialized) {
            setupInitial()
            positionCounts[positionKey()] = 1
        }
    }

    private constructor(copy: XiangqiEngine) : this(true) {
        board = copy.board.copyOf()
        sideToMove = copy.sideToMove
        noCapturePlies = copy.noCapturePlies
        lastMove = copy.lastMove
        status = copy.status
    }

    fun pieceAt(row: Int, column: Int): XiangqiPiece? {
        if (row !in 0..9 || column !in 0..8) return null
        return board[row * 9 + column]
    }

    fun legalMoves(): List<XiangqiMove> = pseudoMoves(sideToMove).filter { isLegal(it, sideToMove) }

    fun legalMovesFrom(from: XiangqiPoint): List<XiangqiMove> =
        legalMoves().filter { it.from == from }

    fun makeMove(move: XiangqiMove): Boolean {
        if (status.isGameOver) return false
        if (move !in legalMoves()) return false
        applyWithHistory(move)
        updateStatus()
        return true
    }

    fun undo(): Boolean {
        val record = history.removeLastOrNull() ?: return false
        val currentKey = positionKey()
        val count = positionCounts[currentKey] ?: 0
        if (count <= 1) positionCounts.remove(currentKey) else positionCounts[currentKey] = count - 1
        board = record.boardBefore.copyOf()
        sideToMove = record.sideBefore
        noCapturePlies = record.noCaptureBefore
        lastMove = record.lastMoveBefore
        updateStatus()
        return true
    }

    fun reset() {
        board.fill(null)
        history.clear()
        positionCounts.clear()
        sideToMove = XiangqiColor.RED
        noCapturePlies = 0
        lastMove = null
        status = XiangqiStatus(XiangqiOutcome.ONGOING, null, false)
        setupInitial()
        positionCounts[positionKey()] = 1
    }

    fun chooseAiMove(maxDepth: Int = 3): XiangqiMove? {
        val allMoves = legalMoves()
        if (allMoves.isEmpty()) return null
        val moves = allMoves.filterNot { isLosingCapture(it) }.ifEmpty { allMoves }
        searchDeadline = System.nanoTime() + 650_000_000L
        searchNodes = 0
        var ordered = moves.sortedByDescending { staticRootScore(it) }
        var best = ordered.firstOrNull() ?: return null
        for (depth in 2..maxDepth) {
            var iterationBest: XiangqiMove? = null
            var iterationScore = Int.MIN_VALUE
            var completed = true
            for (move in ordered) {
                if (timeUp()) {
                    completed = false
                    break
                }
                val copy = XiangqiEngine(this)
                copy.applyUnchecked(move)
                val score = -negamax(copy, depth - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1)
                if (score > iterationScore) {
                    iterationScore = score
                    iterationBest = move
                }
            }
            if (iterationBest != null) {
                best = iterationBest
                ordered = listOf(best) + ordered.filter { it != best }
            }
            if (!completed || timeUp()) break
        }
        return best
    }

    private fun timeUp(): Boolean = System.nanoTime() >= searchDeadline

    private fun isLosingCapture(move: XiangqiMove): Boolean {
        val captured = pieceAt(move.to.row, move.to.column) ?: return false
        val mover = pieceAt(move.from.row, move.from.column) ?: return false
        val copy = XiangqiEngine(this)
        copy.applyUnchecked(move)
        if (!copy.isAttacked(move.to, copy.sideToMove)) return false
        if (copy.isDefended(move.to, mover.color)) return false
        return copy.pieceValue(mover, move.to.row, move.to.column) > copy.pieceValue(captured, move.to.row, move.to.column)
    }

    private fun staticRootScore(move: XiangqiMove): Int {
        val copy = XiangqiEngine(this)
        val moved = copy.pieceAt(move.from.row, move.from.column) ?: return Int.MIN_VALUE
        copy.applyUnchecked(move)
        var score = -copy.evaluate()
        val attacked = copy.isAttacked(move.to, copy.sideToMove)
        if (attacked) {
            val defended = copy.isDefended(move.to, moved.color)
            val value = copy.pieceValue(moved, move.to.row, move.to.column)
            score -= if (defended) value / 4 else value
        }
        return score
    }

    private fun negamax(engine: XiangqiEngine, depth: Int, alpha: Int, beta: Int): Int {
        searchNodes += 1
        if (timeUp()) return engine.evaluate()
        val moves = engine.legalMoves()
        if (moves.isEmpty()) {
            return if (engine.isInCheck(engine.sideToMove)) -MATE_SCORE - depth else -MATE_SCORE / 2 - depth
        }
        if (depth <= 0) return engine.quiescence(alpha, beta, 1)
        var localAlpha = alpha
        var localBeta = beta
        val ordered = moves.sortedByDescending { engine.moveOrderScore(it) }
        for (move in ordered) {
            val child = XiangqiEngine(engine)
            child.applyUnchecked(move)
            val score = -negamax(child, depth - 1, -localBeta, -localAlpha)
            if (score >= localBeta) return localBeta
            if (score > localAlpha) localAlpha = score
            if (timeUp()) break
        }
        return localAlpha
    }

    private fun quiescence(alpha: Int, beta: Int, qDepth: Int): Int {
        val standPat = evaluate()
        if (qDepth <= 0 || timeUp()) return standPat
        var localAlpha = maxOf(alpha, standPat)
        if (localAlpha >= beta) return localAlpha
        val captures = legalMoves()
            .filter { pieceAt(it.to.row, it.to.column) != null }
            .sortedByDescending { moveOrderScore(it) }
        for (move in captures) {
            val child = XiangqiEngine(this)
            child.applyUnchecked(move)
            val score = -child.quiescence(-beta, -localAlpha, qDepth - 1)
            if (score >= beta) return beta
            if (score > localAlpha) localAlpha = score
            if (timeUp()) break
        }
        return localAlpha
    }

    private fun isAttacked(point: XiangqiPoint, byColor: XiangqiColor): Boolean =
        pseudoMoves(byColor).any { it.to == point }

    private fun isDefended(point: XiangqiPoint, color: XiangqiColor): Boolean =
        pseudoMoves(color).any { it.to == point }

    private fun evaluate(): Int {
        var score = 0
        for (row in 0..9) {
            for (column in 0..8) {
                val piece = board[row * 9 + column] ?: continue
                val value = pieceValue(piece, row, column)
                score += if (piece.color == sideToMove) value else -value
            }
        }
        return score
    }

    private fun pieceValue(piece: XiangqiPiece, row: Int, column: Int): Int {
        val base = when (piece.type) {
            XiangqiPieceType.GENERAL -> 10_000
            XiangqiPieceType.CHARIOT -> 900
            XiangqiPieceType.CANNON -> 450
            XiangqiPieceType.HORSE -> 400
            XiangqiPieceType.ADVISOR -> 200
            XiangqiPieceType.ELEPHANT -> 200
            XiangqiPieceType.SOLDIER -> 100
        }
        val positional = when (piece.type) {
            XiangqiPieceType.SOLDIER -> {
                if (piece.color == XiangqiColor.RED) {
                    val advanced = (6 - row).coerceAtLeast(0) * 14
                    advanced + if (row <= 4) 35 else 0
                } else {
                    val advanced = (row - 3).coerceAtLeast(0) * 14
                    advanced + if (row >= 5) 35 else 0
                }
            }
            XiangqiPieceType.HORSE -> {
                val homePenalty = if (
                    (piece.color == XiangqiColor.RED && row == 9) ||
                    (piece.color == XiangqiColor.BLACK && row == 0)
                ) -30 else 0
                homePenalty + (4 - kotlin.math.abs(column - 4)) * 5
            }
            XiangqiPieceType.CANNON -> (4 - kotlin.math.abs(column - 4)) * 6
            XiangqiPieceType.CHARIOT -> (4 - kotlin.math.abs(column - 4)) * 3
            XiangqiPieceType.ADVISOR,
            XiangqiPieceType.ELEPHANT,
            XiangqiPieceType.GENERAL,
            -> 0
        }
        return base + positional
    }

    private fun moveOrderScore(move: XiangqiMove): Int {
        val captured = pieceAt(move.to.row, move.to.column) ?: return 0
        return when (captured.type) {
            XiangqiPieceType.GENERAL -> 10_000
            XiangqiPieceType.CHARIOT -> 900
            XiangqiPieceType.CANNON -> 450
            XiangqiPieceType.HORSE -> 400
            else -> 200
        }
    }

    private fun setupInitial() {
        val blackBack = listOf(
            XiangqiPieceType.CHARIOT,
            XiangqiPieceType.HORSE,
            XiangqiPieceType.ELEPHANT,
            XiangqiPieceType.ADVISOR,
            XiangqiPieceType.GENERAL,
            XiangqiPieceType.ADVISOR,
            XiangqiPieceType.ELEPHANT,
            XiangqiPieceType.HORSE,
            XiangqiPieceType.CHARIOT,
        )
        for (column in 0..8) {
            board[0 * 9 + column] = XiangqiPiece(blackBack[column], XiangqiColor.BLACK)
        }
        board[2 * 9 + 1] = XiangqiPiece(XiangqiPieceType.CANNON, XiangqiColor.BLACK)
        board[2 * 9 + 7] = XiangqiPiece(XiangqiPieceType.CANNON, XiangqiColor.BLACK)
        for (column in listOf(0, 2, 4, 6, 8)) {
            board[3 * 9 + column] = XiangqiPiece(XiangqiPieceType.SOLDIER, XiangqiColor.BLACK)
        }
        for (column in listOf(0, 2, 4, 6, 8)) {
            board[6 * 9 + column] = XiangqiPiece(XiangqiPieceType.SOLDIER, XiangqiColor.RED)
        }
        board[7 * 9 + 1] = XiangqiPiece(XiangqiPieceType.CANNON, XiangqiColor.RED)
        board[7 * 9 + 7] = XiangqiPiece(XiangqiPieceType.CANNON, XiangqiColor.RED)
        for (column in 0..8) {
            board[9 * 9 + column] = XiangqiPiece(blackBack[column], XiangqiColor.RED)
        }
    }

    private fun applyWithHistory(move: XiangqiMove) {
        history += MoveRecord(
            boardBefore = board.copyOf(),
            sideBefore = sideToMove,
            noCaptureBefore = noCapturePlies,
            lastMoveBefore = lastMove,
        )
        applyUnchecked(move)
        val key = positionKey()
        positionCounts[key] = (positionCounts[key] ?: 0) + 1
    }

    private fun applyUnchecked(move: XiangqiMove) {
        val piece = board[move.from.row * 9 + move.from.column] ?: return
        val captured = board[move.to.row * 9 + move.to.column]
        board[move.from.row * 9 + move.from.column] = null
        board[move.to.row * 9 + move.to.column] = piece
        noCapturePlies = if (captured != null) 0 else noCapturePlies + 1
        sideToMove = sideToMove.other()
        lastMove = move
    }

    private fun updateStatus() {
        val moves = legalMoves()
        val check = isInCheck(sideToMove)
        val outcome = when {
            moves.isEmpty() -> if (check) XiangqiOutcome.CHECKMATE else XiangqiOutcome.STALEMATE
            noCapturePlies >= 120 -> XiangqiOutcome.DRAW_MOVE_LIMIT
            (positionCounts[positionKey()] ?: 0) >= 3 -> XiangqiOutcome.DRAW_REPETITION
            else -> XiangqiOutcome.ONGOING
        }
        val winner = when (outcome) {
            XiangqiOutcome.CHECKMATE,
            XiangqiOutcome.STALEMATE,
            -> sideToMove.other()
            else -> null
        }
        status = XiangqiStatus(outcome, winner, check)
    }

    private fun pseudoMoves(color: XiangqiColor): List<XiangqiMove> {
        val result = mutableListOf<XiangqiMove>()
        for (row in 0..9) {
            for (column in 0..8) {
                val piece = board[row * 9 + column] ?: continue
                if (piece.color != color) continue
                when (piece.type) {
                    XiangqiPieceType.GENERAL -> addGeneralMoves(result, row, column, piece)
                    XiangqiPieceType.ADVISOR -> addAdvisorMoves(result, row, column, piece)
                    XiangqiPieceType.ELEPHANT -> addElephantMoves(result, row, column, piece)
                    XiangqiPieceType.HORSE -> addHorseMoves(result, row, column, piece)
                    XiangqiPieceType.CHARIOT -> addChariotMoves(result, row, column, piece)
                    XiangqiPieceType.CANNON -> addCannonMoves(result, row, column, piece)
                    XiangqiPieceType.SOLDIER -> addSoldierMoves(result, row, column, piece)
                }
            }
        }
        return result
    }

    private fun addGeneralMoves(
        moves: MutableList<XiangqiMove>,
        row: Int,
        column: Int,
        piece: XiangqiPiece,
    ) {
        ORTHOGONAL.forEach { (dr, dc) ->
            val targetRow = row + dr
            val targetColumn = column + dc
            if (!inPalace(piece.color, targetRow, targetColumn)) return@forEach
            val target = board[targetRow * 9 + targetColumn]
            if (target == null || target.color != piece.color) {
                moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, targetColumn))
            }
        }
        // 飞将
        for (direction in listOf(-1, 1)) {
            var targetRow = row + direction
            while (targetRow in 0..9) {
                val target = board[targetRow * 9 + column]
                if (target != null) {
                    if (target.type == XiangqiPieceType.GENERAL && target.color != piece.color) {
                        moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, column))
                    }
                    break
                }
                targetRow += direction
            }
        }
    }

    private fun addAdvisorMoves(
        moves: MutableList<XiangqiMove>,
        row: Int,
        column: Int,
        piece: XiangqiPiece,
    ) {
        DIAGONAL.forEach { (dr, dc) ->
            val targetRow = row + dr
            val targetColumn = column + dc
            if (!inPalace(piece.color, targetRow, targetColumn)) return@forEach
            val target = board[targetRow * 9 + targetColumn]
            if (target == null || target.color != piece.color) {
                moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, targetColumn))
            }
        }
    }

    private fun addElephantMoves(
        moves: MutableList<XiangqiMove>,
        row: Int,
        column: Int,
        piece: XiangqiPiece,
    ) {
        DIAGONAL_TWO.forEach { (dr, dc) ->
            val targetRow = row + dr
            val targetColumn = column + dc
            if (targetRow !in 0..9 || targetColumn !in 0..8) return@forEach
            if (!ownSideOfRiver(piece.color, targetRow)) return@forEach
            if (board[(row + dr / 2) * 9 + (column + dc / 2)] != null) return@forEach
            val target = board[targetRow * 9 + targetColumn]
            if (target == null || target.color != piece.color) {
                moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, targetColumn))
            }
        }
    }

    private fun addHorseMoves(
        moves: MutableList<XiangqiMove>,
        row: Int,
        column: Int,
        piece: XiangqiPiece,
    ) {
        HORSE_MOVES.forEach { step ->
            val targetRow = row + step.dr
            val targetColumn = column + step.dc
            if (targetRow !in 0..9 || targetColumn !in 0..8) return@forEach
            if (board[(row + step.legRow) * 9 + (column + step.legColumn)] != null) return@forEach
            val target = board[targetRow * 9 + targetColumn]
            if (target == null || target.color != piece.color) {
                moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, targetColumn))
            }
        }
    }

    private fun addChariotMoves(
        moves: MutableList<XiangqiMove>,
        row: Int,
        column: Int,
        piece: XiangqiPiece,
    ) {
        ORTHOGONAL.forEach { (dr, dc) ->
            var targetRow = row + dr
            var targetColumn = column + dc
            while (targetRow in 0..9 && targetColumn in 0..8) {
                val target = board[targetRow * 9 + targetColumn]
                if (target == null) {
                    moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, targetColumn))
                } else {
                    if (target.color != piece.color) {
                        moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, targetColumn))
                    }
                    break
                }
                targetRow += dr
                targetColumn += dc
            }
        }
    }

    private fun addCannonMoves(
        moves: MutableList<XiangqiMove>,
        row: Int,
        column: Int,
        piece: XiangqiPiece,
    ) {
        ORTHOGONAL.forEach { (dr, dc) ->
            var targetRow = row + dr
            var targetColumn = column + dc
            var screenFound = false
            while (targetRow in 0..9 && targetColumn in 0..8) {
                val target = board[targetRow * 9 + targetColumn]
                if (!screenFound) {
                    if (target == null) {
                        moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, targetColumn))
                    } else {
                        screenFound = true
                    }
                } else if (target != null) {
                    if (target.color != piece.color) {
                        moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, targetColumn))
                    }
                    break
                }
                targetRow += dr
                targetColumn += dc
            }
        }
    }

    private fun addSoldierMoves(
        moves: MutableList<XiangqiMove>,
        row: Int,
        column: Int,
        piece: XiangqiPiece,
    ) {
        val forward = if (piece.color == XiangqiColor.RED) -1 else 1
        val candidates = mutableListOf(row + forward to column)
        val crossedRiver = if (piece.color == XiangqiColor.RED) row <= 4 else row >= 5
        if (crossedRiver) {
            candidates += row to (column - 1)
            candidates += row to (column + 1)
        }
        candidates.forEach { (targetRow, targetColumn) ->
            if (targetRow !in 0..9 || targetColumn !in 0..8) return@forEach
            val target = board[targetRow * 9 + targetColumn]
            if (target == null || target.color != piece.color) {
                moves += XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(targetRow, targetColumn))
            }
        }
    }

    private fun isLegal(move: XiangqiMove, color: XiangqiColor): Boolean {
        val copy = XiangqiEngine(this)
        copy.applyUnchecked(move)
        return !copy.isInCheck(color)
    }

    private fun isInCheck(color: XiangqiColor): Boolean {
        val general = findGeneral(color) ?: return true
        if (generalsFacing()) return true
        return pseudoMoves(color.other()).any { it.to == general }
    }

    private fun generalsFacing(): Boolean {
        val red = findGeneral(XiangqiColor.RED) ?: return false
        val black = findGeneral(XiangqiColor.BLACK) ?: return false
        if (red.column != black.column) return false
        val start = minOf(red.row, black.row)
        val end = maxOf(red.row, black.row)
        for (row in start + 1 until end) {
            if (board[row * 9 + red.column] != null) return false
        }
        return true
    }

    private fun findGeneral(color: XiangqiColor): XiangqiPoint? {
        for (row in 0..9) {
            for (column in 0..8) {
                val piece = board[row * 9 + column]
                if (piece?.type == XiangqiPieceType.GENERAL && piece.color == color) {
                    return XiangqiPoint(row, column)
                }
            }
        }
        return null
    }

    private fun inPalace(color: XiangqiColor, row: Int, column: Int): Boolean {
        if (column !in 3..5) return false
        return if (color == XiangqiColor.RED) row in 7..9 else row in 0..2
    }

    private fun ownSideOfRiver(color: XiangqiColor, row: Int): Boolean =
        if (color == XiangqiColor.RED) row >= 5 else row <= 4

    private fun positionKey(): String {
        val builder = StringBuilder(100)
        board.forEach { piece ->
            builder.append(
                when (piece?.type) {
                    XiangqiPieceType.GENERAL -> if (piece.color == XiangqiColor.RED) 'K' else 'k'
                    XiangqiPieceType.ADVISOR -> if (piece.color == XiangqiColor.RED) 'A' else 'a'
                    XiangqiPieceType.ELEPHANT -> if (piece.color == XiangqiColor.RED) 'B' else 'b'
                    XiangqiPieceType.HORSE -> if (piece.color == XiangqiColor.RED) 'N' else 'n'
                    XiangqiPieceType.CHARIOT -> if (piece.color == XiangqiColor.RED) 'R' else 'r'
                    XiangqiPieceType.CANNON -> if (piece.color == XiangqiColor.RED) 'C' else 'c'
                    XiangqiPieceType.SOLDIER -> if (piece.color == XiangqiColor.RED) 'P' else 'p'
                    null -> '.'
                },
            )
        }
        builder.append(if (sideToMove == XiangqiColor.RED) 'r' else 'b')
        return builder.toString()
    }

    companion object {
        private const val MATE_SCORE = 100_000
        private val ORTHOGONAL = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val DIAGONAL = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val DIAGONAL_TWO = listOf(2 to 2, 2 to -2, -2 to 2, -2 to -2)
        private data class HorseStep(
            val dr: Int,
            val dc: Int,
            val legRow: Int,
            val legColumn: Int,
        )

        private val HORSE_MOVES = listOf(
            HorseStep(2, 1, 1, 0),
            HorseStep(2, -1, 1, 0),
            HorseStep(-2, 1, -1, 0),
            HorseStep(-2, -1, -1, 0),
            HorseStep(1, 2, 0, 1),
            HorseStep(-1, 2, 0, 1),
            HorseStep(1, -2, 0, -1),
            HorseStep(-1, -2, 0, -1),
        )
    }
}
