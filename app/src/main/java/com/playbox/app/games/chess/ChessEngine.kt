package com.playbox.app.games.chess

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ChessColor {
    WHITE,
    BLACK;

    fun other(): ChessColor = if (this == WHITE) BLACK else WHITE
}

enum class ChessPieceType {
    KING,
    QUEEN,
    ROOK,
    BISHOP,
    KNIGHT,
    PAWN,
}

@Immutable
data class ChessPiece(
    val type: ChessPieceType,
    val color: ChessColor,
)

@Immutable
data class ChessSquare(
    val row: Int,
    val column: Int,
) {
    fun isValid(): Boolean = row in 0..7 && column in 0..7
}

@Immutable
data class ChessMove(
    val from: ChessSquare,
    val to: ChessSquare,
    val promotion: ChessPieceType? = null,
    val isCastling: Boolean = false,
    val isEnPassant: Boolean = false,
)

enum class ChessOutcome {
    ONGOING,
    CHECKMATE,
    STALEMATE,
    DRAW_FIFTY,
    DRAW_REPETITION,
    DRAW_MATERIAL,
}

@Immutable
data class ChessStatus(
    val outcome: ChessOutcome,
    val winner: ChessColor?,
    val inCheck: Boolean,
) {
    val isGameOver: Boolean get() = outcome != ChessOutcome.ONGOING
}

private data class CastleRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true,
)

private data class MoveRecord(
    val boardBefore: Array<ChessPiece?>,
    val sideBefore: ChessColor,
    val castleBefore: CastleRights,
    val enPassantBefore: ChessSquare?,
    val halfmoveBefore: Int,
    val fullmoveBefore: Int,
    val lastMoveBefore: ChessMove?,
)

class ChessEngine private constructor(initialized: Boolean) {
    private var board: Array<ChessPiece?> = arrayOfNulls(64)
    private var castling = CastleRights()
    private var history = mutableListOf<MoveRecord>()
    private val positionCounts = mutableMapOf<String, Int>()
    private var searchNodes = 0
    private var searchDeadline = 0L

    var sideToMove: ChessColor = ChessColor.WHITE
        private set
    var enPassantTarget: ChessSquare? = null
        private set
    var halfmoveClock: Int = 0
        private set
    var fullmoveNumber: Int = 1
        private set
    var lastMove: ChessMove? = null
        private set
    var status: ChessStatus = ChessStatus(ChessOutcome.ONGOING, null, false)
        private set
    val moveCount: Int get() = history.size

    constructor() : this(false)

    init {
        if (!initialized) {
            setupInitial()
            positionCounts[positionKey()] = 1
        }
    }

    private constructor(copy: ChessEngine) : this(true) {
        board = copy.board.copyOf()
        castling = copy.castling
        enPassantTarget = copy.enPassantTarget
        halfmoveClock = copy.halfmoveClock
        fullmoveNumber = copy.fullmoveNumber
        sideToMove = copy.sideToMove
        lastMove = copy.lastMove
        status = copy.status
    }

    fun pieceAt(row: Int, column: Int): ChessPiece? {
        if (row !in 0..7 || column !in 0..7) return null
        return board[row * 8 + column]
    }

    fun legalMoves(): List<ChessMove> = pseudoMoves(sideToMove).filter { isLegal(it, sideToMove) }

    fun legalMovesFrom(square: ChessSquare): List<ChessMove> =
        legalMoves().filter { it.from == square }

    fun makeMove(move: ChessMove): Boolean {
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
        castling = record.castleBefore
        enPassantTarget = record.enPassantBefore
        halfmoveClock = record.halfmoveBefore
        fullmoveNumber = record.fullmoveBefore
        lastMove = record.lastMoveBefore
        updateStatus()
        return true
    }

    fun reset() {
        board.fill(null)
        castling = CastleRights()
        history.clear()
        positionCounts.clear()
        sideToMove = ChessColor.WHITE
        enPassantTarget = null
        halfmoveClock = 0
        fullmoveNumber = 1
        lastMove = null
        status = ChessStatus(ChessOutcome.ONGOING, null, false)
        setupInitial()
        positionCounts[positionKey()] = 1
    }

    /**
     * 选择一步 AI 走法。使用带时间上限的 alpha-beta 搜索，适合手机端基础对弈。
     */
    fun chooseAiMove(depth: Int = 3): ChessMove? {
        val moves = legalMoves()
        if (moves.isEmpty()) return null
        searchDeadline = System.nanoTime() + 850_000_000L
        searchNodes = 0
        var bestMove = moves.first()
        var bestScore = Int.MIN_VALUE
        val ordered = moves.sortedByDescending { moveOrderScore(it) }
        for (move in ordered) {
            val copy = ChessEngine(this)
            copy.applyUnchecked(move)
            val score = -negamax(copy, depth - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1)
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            if (System.nanoTime() >= searchDeadline) break
        }
        return bestMove
    }

    private fun negamax(engine: ChessEngine, depth: Int, alpha: Int, beta: Int): Int {
        searchNodes += 1
        if ((searchNodes and 511) == 0 && System.nanoTime() >= searchDeadline) {
            return engine.evaluate()
        }
        val moves = engine.legalMoves()
        if (moves.isEmpty()) {
            return if (engine.isInCheck(engine.sideToMove)) {
                -MATE_SCORE - depth
            } else {
                0
            }
        }
        if (depth <= 0) return engine.evaluate()
        var localAlpha = alpha
        var localBeta = beta
        val ordered = moves.sortedByDescending { engine.moveOrderScore(it) }
        for (move in ordered) {
            val child = ChessEngine(engine)
            child.applyUnchecked(move)
            val score = -negamax(child, depth - 1, -localBeta, -localAlpha)
            if (score >= localBeta) return localBeta
            if (score > localAlpha) localAlpha = score
            if (System.nanoTime() >= searchDeadline) break
        }
        return localAlpha
    }

    private fun evaluate(): Int {
        var score = 0
        for (row in 0..7) {
            for (column in 0..7) {
                val piece = board[row * 8 + column] ?: continue
                val value = PIECE_VALUES[piece.type] ?: 0
                val positional = positionalValue(piece, row, column)
                score += if (piece.color == sideToMove) value + positional else -(value + positional)
            }
        }
        return score
    }

    private fun positionalValue(piece: ChessPiece, row: Int, column: Int): Int {
        val forward = if (piece.color == ChessColor.WHITE) 7 - row else row
        val centerDistance = abs(3.5f - column) + abs(3.5f - row)
        return when (piece.type) {
            ChessPieceType.PAWN -> forward * 9 - (centerDistance * 2f).toInt()
            ChessPieceType.KNIGHT -> (7f - centerDistance).toInt() * 8
            ChessPieceType.BISHOP -> (7f - centerDistance).toInt() * 4
            ChessPieceType.ROOK -> 0
            ChessPieceType.QUEEN -> (7f - centerDistance).toInt() * 2
            ChessPieceType.KING -> -((centerDistance * 3f).toInt())
        }
    }

    private fun moveOrderScore(move: ChessMove): Int {
        val captured = pieceAt(move.to.row, move.to.column)
        val captureScore = captured?.let { PIECE_VALUES[it.type] ?: 0 } ?: 0
        val promotionScore = move.promotion?.let { PIECE_VALUES[it] ?: 0 } ?: 0
        val castleScore = if (move.isCastling) 50 else 0
        return captureScore + promotionScore + castleScore
    }

    private fun setupInitial() {
        val backRank = listOf(
            ChessPieceType.ROOK,
            ChessPieceType.KNIGHT,
            ChessPieceType.BISHOP,
            ChessPieceType.QUEEN,
            ChessPieceType.KING,
            ChessPieceType.BISHOP,
            ChessPieceType.KNIGHT,
            ChessPieceType.ROOK,
        )
        for (column in 0..7) {
            board[0 * 8 + column] = ChessPiece(backRank[column], ChessColor.BLACK)
            board[1 * 8 + column] = ChessPiece(ChessPieceType.PAWN, ChessColor.BLACK)
            board[6 * 8 + column] = ChessPiece(ChessPieceType.PAWN, ChessColor.WHITE)
            board[7 * 8 + column] = ChessPiece(backRank[column], ChessColor.WHITE)
        }
    }

    private fun applyWithHistory(move: ChessMove) {
        history += MoveRecord(
            boardBefore = board.copyOf(),
            sideBefore = sideToMove,
            castleBefore = castling,
            enPassantBefore = enPassantTarget,
            halfmoveBefore = halfmoveClock,
            fullmoveBefore = fullmoveNumber,
            lastMoveBefore = lastMove,
        )
        applyUnchecked(move)
        val key = positionKey()
        positionCounts[key] = (positionCounts[key] ?: 0) + 1
    }

    private fun applyUnchecked(move: ChessMove) {
        val piece = board[move.from.row * 8 + move.from.column] ?: return
        val capturedSquare = if (move.isEnPassant) {
            ChessSquare(move.from.row, move.to.column)
        } else {
            move.to
        }
        val captured = board[capturedSquare.row * 8 + capturedSquare.column]
        if (captured != null && capturedSquare != move.to) {
            board[capturedSquare.row * 8 + capturedSquare.column] = null
        } else {
            board[move.to.row * 8 + move.to.column] = null
        }
        board[move.from.row * 8 + move.from.column] = null
        board[move.to.row * 8 + move.to.column] = if (move.promotion != null) {
            ChessPiece(move.promotion, piece.color)
        } else {
            piece
        }

        if (move.isCastling) {
            val rookFromColumn = if (move.to.column == 6) 7 else 0
            val rookToColumn = if (move.to.column == 6) 5 else 3
            val rook = board[move.from.row * 8 + rookFromColumn]
            board[move.from.row * 8 + rookFromColumn] = null
            board[move.from.row * 8 + rookToColumn] = rook
        }

        updateCastlingRights(piece, move, capturedSquare)
        enPassantTarget = if (piece.type == ChessPieceType.PAWN && abs(move.to.row - move.from.row) == 2) {
            ChessSquare((move.from.row + move.to.row) / 2, move.from.column)
        } else {
            null
        }
        halfmoveClock = if (piece.type == ChessPieceType.PAWN || captured != null) 0 else halfmoveClock + 1
        if (piece.color == ChessColor.BLACK) fullmoveNumber += 1
        sideToMove = sideToMove.other()
        lastMove = move
    }

    private fun updateCastlingRights(piece: ChessPiece, move: ChessMove, capturedSquare: ChessSquare) {
        if (piece.type == ChessPieceType.KING) {
            castling = if (piece.color == ChessColor.WHITE) {
                castling.copy(whiteKingSide = false, whiteQueenSide = false)
            } else {
                castling.copy(blackKingSide = false, blackQueenSide = false)
            }
        }
        if (piece.type == ChessPieceType.ROOK) {
            castling = when {
                piece.color == ChessColor.WHITE && move.from.row == 7 && move.from.column == 0 ->
                    castling.copy(whiteQueenSide = false)
                piece.color == ChessColor.WHITE && move.from.row == 7 && move.from.column == 7 ->
                    castling.copy(whiteKingSide = false)
                piece.color == ChessColor.BLACK && move.from.row == 0 && move.from.column == 0 ->
                    castling.copy(blackQueenSide = false)
                piece.color == ChessColor.BLACK && move.from.row == 0 && move.from.column == 7 ->
                    castling.copy(blackKingSide = false)
                else -> castling
            }
        }
        if (capturedSquare.row == 7 && capturedSquare.column == 0) castling = castling.copy(whiteQueenSide = false)
        if (capturedSquare.row == 7 && capturedSquare.column == 7) castling = castling.copy(whiteKingSide = false)
        if (capturedSquare.row == 0 && capturedSquare.column == 0) castling = castling.copy(blackQueenSide = false)
        if (capturedSquare.row == 0 && capturedSquare.column == 7) castling = castling.copy(blackKingSide = false)
    }

    private fun updateStatus() {
        val moves = legalMoves()
        val check = isInCheck(sideToMove)
        val outcome = when {
            moves.isEmpty() && check -> ChessOutcome.CHECKMATE
            moves.isEmpty() -> ChessOutcome.STALEMATE
            halfmoveClock >= 100 -> ChessOutcome.DRAW_FIFTY
            (positionCounts[positionKey()] ?: 0) >= 3 -> ChessOutcome.DRAW_REPETITION
            insufficientMaterial() -> ChessOutcome.DRAW_MATERIAL
            else -> ChessOutcome.ONGOING
        }
        val winner = if (outcome == ChessOutcome.CHECKMATE) sideToMove.other() else null
        status = ChessStatus(outcome, winner, check)
    }

    private fun pseudoMoves(color: ChessColor, includeCastling: Boolean = true): List<ChessMove> {
        val result = mutableListOf<ChessMove>()
        for (row in 0..7) {
            for (column in 0..7) {
                val piece = board[row * 8 + column] ?: continue
                if (piece.color != color) continue
                when (piece.type) {
                    ChessPieceType.PAWN -> addPawnMoves(result, row, column, piece)
                    ChessPieceType.KNIGHT -> addKnightMoves(result, row, column, piece)
                    ChessPieceType.BISHOP -> addSlidingMoves(result, row, column, piece, BISHOP_DIRECTIONS)
                    ChessPieceType.ROOK -> addSlidingMoves(result, row, column, piece, ROOK_DIRECTIONS)
                    ChessPieceType.QUEEN -> addSlidingMoves(result, row, column, piece, QUEEN_DIRECTIONS)
                    ChessPieceType.KING -> addKingMoves(result, row, column, piece, includeCastling)
                }
            }
        }
        return result
    }

    private fun addPawnMoves(
        moves: MutableList<ChessMove>,
        row: Int,
        column: Int,
        piece: ChessPiece,
    ) {
        val direction = if (piece.color == ChessColor.WHITE) -1 else 1
        val startRow = if (piece.color == ChessColor.WHITE) 6 else 1
        val promotionRow = if (piece.color == ChessColor.WHITE) 0 else 7
        val nextRow = row + direction
        if (nextRow in 0..7 && board[nextRow * 8 + column] == null) {
            addPromotionOrMove(moves, row, column, nextRow, column, promotionRow)
            val doubleRow = row + direction * 2
            if (row == startRow && board[doubleRow * 8 + column] == null) {
                moves += ChessMove(ChessSquare(row, column), ChessSquare(doubleRow, column))
            }
        }
        for (dc in listOf(-1, 1)) {
            val targetRow = row + direction
            val targetColumn = column + dc
            if (targetRow !in 0..7 || targetColumn !in 0..7) continue
            val target = board[targetRow * 8 + targetColumn]
            if (target != null && target.color != piece.color) {
                addPromotionOrMove(moves, row, column, targetRow, targetColumn, promotionRow)
            } else if (target == null && enPassantTarget?.let { it.row == targetRow && it.column == targetColumn } == true) {
                moves += ChessMove(
                    ChessSquare(row, column),
                    ChessSquare(targetRow, targetColumn),
                    isEnPassant = true,
                )
            }
        }
    }

    private fun addPromotionOrMove(
        moves: MutableList<ChessMove>,
        fromRow: Int,
        fromColumn: Int,
        toRow: Int,
        toColumn: Int,
        promotionRow: Int,
    ) {
        val from = ChessSquare(fromRow, fromColumn)
        val to = ChessSquare(toRow, toColumn)
        if (toRow == promotionRow) {
            listOf(
                ChessPieceType.QUEEN,
                ChessPieceType.ROOK,
                ChessPieceType.BISHOP,
                ChessPieceType.KNIGHT,
            ).forEach { moves += ChessMove(from, to, promotion = it) }
        } else {
            moves += ChessMove(from, to)
        }
    }

    private fun addKnightMoves(
        moves: MutableList<ChessMove>,
        row: Int,
        column: Int,
        piece: ChessPiece,
    ) {
        KNIGHT_OFFSETS.forEach { (dr, dc) ->
            val targetRow = row + dr
            val targetColumn = column + dc
            if (targetRow !in 0..7 || targetColumn !in 0..7) return@forEach
            val target = board[targetRow * 8 + targetColumn]
            if (target == null || target.color != piece.color) {
                moves += ChessMove(ChessSquare(row, column), ChessSquare(targetRow, targetColumn))
            }
        }
    }

    private fun addSlidingMoves(
        moves: MutableList<ChessMove>,
        row: Int,
        column: Int,
        piece: ChessPiece,
        directions: List<Pair<Int, Int>>,
    ) {
        directions.forEach { (dr, dc) ->
            var targetRow = row + dr
            var targetColumn = column + dc
            while (targetRow in 0..7 && targetColumn in 0..7) {
                val target = board[targetRow * 8 + targetColumn]
                if (target == null) {
                    moves += ChessMove(ChessSquare(row, column), ChessSquare(targetRow, targetColumn))
                } else {
                    if (target.color != piece.color) {
                        moves += ChessMove(ChessSquare(row, column), ChessSquare(targetRow, targetColumn))
                    }
                    break
                }
                targetRow += dr
                targetColumn += dc
            }
        }
    }

    private fun addKingMoves(
        moves: MutableList<ChessMove>,
        row: Int,
        column: Int,
        piece: ChessPiece,
        includeCastling: Boolean,
    ) {
        KING_OFFSETS.forEach { (dr, dc) ->
            val targetRow = row + dr
            val targetColumn = column + dc
            if (targetRow !in 0..7 || targetColumn !in 0..7) return@forEach
            val target = board[targetRow * 8 + targetColumn]
            if (target == null || target.color != piece.color) {
                moves += ChessMove(ChessSquare(row, column), ChessSquare(targetRow, targetColumn))
            }
        }
        if (!includeCastling) return
        if (piece.color == ChessColor.WHITE && row == 7 && column == 4) {
            if (castling.whiteKingSide && canCastle(row, column, 6, 7)) {
                moves += ChessMove(ChessSquare(row, column), ChessSquare(row, 6), isCastling = true)
            }
            if (castling.whiteQueenSide && canCastle(row, column, 2, 0)) {
                moves += ChessMove(ChessSquare(row, column), ChessSquare(row, 2), isCastling = true)
            }
        }
        if (piece.color == ChessColor.BLACK && row == 0 && column == 4) {
            if (castling.blackKingSide && canCastle(row, column, 6, 7)) {
                moves += ChessMove(ChessSquare(row, column), ChessSquare(row, 6), isCastling = true)
            }
            if (castling.blackQueenSide && canCastle(row, column, 2, 0)) {
                moves += ChessMove(ChessSquare(row, column), ChessSquare(row, 2), isCastling = true)
            }
        }
    }

    private fun canCastle(row: Int, kingColumn: Int, targetKingColumn: Int, rookColumn: Int): Boolean {
        val between = if (targetKingColumn > kingColumn) {
            (kingColumn + 1)..targetKingColumn
        } else {
            targetKingColumn..(kingColumn - 1)
        }
        if (between.any { board[row * 8 + it] != null }) return false
        val rook = board[row * 8 + rookColumn]
        if (rook?.type != ChessPieceType.ROOK) return false
        val king = board[row * 8 + kingColumn]
        if (king?.type != ChessPieceType.KING) return false
        if (isSquareAttacked(row, kingColumn, king.color)) return false
        val transitColumn = if (targetKingColumn > kingColumn) kingColumn + 1 else kingColumn - 1
        if (isSquareAttacked(row, transitColumn, king.color)) return false
        if (isSquareAttacked(row, targetKingColumn, king.color)) return false
        return true
    }

    private fun isLegal(move: ChessMove, color: ChessColor): Boolean {
        val copy = ChessEngine(this)
        copy.applyUnchecked(move)
        return !copy.isInCheck(color)
    }

    private fun isInCheck(color: ChessColor): Boolean {
        val king = findKing(color) ?: return true
        return isSquareAttacked(king.row, king.column, color)
    }

    private fun isSquareAttacked(row: Int, column: Int, color: ChessColor): Boolean {
        return pseudoMoves(color.other(), includeCastling = false)
            .any { it.to.row == row && it.to.column == column }
    }

    private fun findKing(color: ChessColor): ChessSquare? {
        for (row in 0..7) {
            for (column in 0..7) {
                val piece = board[row * 8 + column]
                if (piece?.type == ChessPieceType.KING && piece.color == color) {
                    return ChessSquare(row, column)
                }
            }
        }
        return null
    }

    private fun insufficientMaterial(): Boolean {
        val pieces = board.filterNotNull()
        if (pieces.size == 2) return true
        if (pieces.size == 3) {
            return pieces.any { it.type == ChessPieceType.BISHOP || it.type == ChessPieceType.KNIGHT }
        }
        if (pieces.size == 4) {
            val bishops = pieces.filter { it.type == ChessPieceType.BISHOP }
            if (bishops.size == 2 && pieces.count { it.type == ChessPieceType.KING } == 2) {
                val squares = mutableListOf<Int>()
                for (row in 0..7) for (column in 0..7) {
                    if (board[row * 8 + column]?.type == ChessPieceType.BISHOP) {
                        squares += (row + column) % 2
                    }
                }
                return squares.size == 2 && squares[0] == squares[1]
            }
        }
        return false
    }

    private fun positionKey(): String {
        val builder = StringBuilder(72)
        board.forEach { piece ->
            builder.append(
                when (piece?.type) {
                    ChessPieceType.KING -> if (piece.color == ChessColor.WHITE) 'K' else 'k'
                    ChessPieceType.QUEEN -> if (piece.color == ChessColor.WHITE) 'Q' else 'q'
                    ChessPieceType.ROOK -> if (piece.color == ChessColor.WHITE) 'R' else 'r'
                    ChessPieceType.BISHOP -> if (piece.color == ChessColor.WHITE) 'B' else 'b'
                    ChessPieceType.KNIGHT -> if (piece.color == ChessColor.WHITE) 'N' else 'n'
                    ChessPieceType.PAWN -> if (piece.color == ChessColor.WHITE) 'P' else 'p'
                    null -> '.'
                },
            )
        }
        builder.append(if (sideToMove == ChessColor.WHITE) 'w' else 'b')
        builder.append(
            (if (castling.whiteKingSide) "K" else "") +
                (if (castling.whiteQueenSide) "Q" else "") +
                (if (castling.blackKingSide) "k" else "") +
                (if (castling.blackQueenSide) "q" else ""),
        )
        builder.append(enPassantTarget?.let { "${it.row}${it.column}" } ?: "-")
        return builder.toString()
    }

    companion object {
        private const val MATE_SCORE = 100_000
        private val PIECE_VALUES = mapOf(
            ChessPieceType.PAWN to 100,
            ChessPieceType.KNIGHT to 320,
            ChessPieceType.BISHOP to 330,
            ChessPieceType.ROOK to 500,
            ChessPieceType.QUEEN to 900,
            ChessPieceType.KING to 20_000,
        )
        private val KNIGHT_OFFSETS = listOf(2 to 1, 2 to -1, -2 to 1, -2 to -1, 1 to 2, 1 to -2, -1 to 2, -1 to -2)
        private val KING_OFFSETS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val ROOK_DIRECTIONS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val BISHOP_DIRECTIONS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val QUEEN_DIRECTIONS = ROOK_DIRECTIONS + BISHOP_DIRECTIONS
    }
}
