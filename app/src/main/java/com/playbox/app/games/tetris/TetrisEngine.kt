package com.playbox.app.games.tetris

import kotlin.random.Random
import kotlinx.serialization.Serializable

const val TETRIS_COLUMNS = 10
const val TETRIS_ROWS = 20

@Serializable
enum class TetrominoType {
    I,
    J,
    L,
    O,
    S,
    T,
    Z,
}

data class TetrominoCell(
    val row: Int,
    val col: Int,
)

data class ActivePiece(
    val type: TetrominoType,
    val rotation: Int,
    val row: Int,
    val col: Int,
)

@Serializable
data class PieceSnapshot(
    val type: TetrominoType,
    val rotation: Int,
    val row: Int,
    val col: Int,
)

@Serializable
data class TetrisSnapshot(
    val board: List<Int>,
    val active: PieceSnapshot?,
    val queue: List<TetrominoType>,
    val score: Int,
    val lines: Int,
    val level: Int,
    val startLevel: Int,
    val gameOver: Boolean,
)

/**
 * 俄罗斯方块纯逻辑引擎：7-bag 随机、基础墙踢旋转、消行计分、等级加速。
 */
class TetrisEngine private constructor(
    private val random: Random,
    private var board: IntArray,
    private var active: ActivePiece?,
    private val queue: ArrayDeque<TetrominoType>,
    private var score: Int,
    private var lines: Int,
    private var level: Int,
    private val startLevel: Int,
    private var gameOver: Boolean,
) {
    val boardValues: List<Int> get() = board.toList()
    val currentScore: Int get() = score
    val clearedLines: Int get() = lines
    val currentLevel: Int get() = level
    val isGameOver: Boolean get() = gameOver
    val currentPiece: ActivePiece? get() = active
    val nextType: TetrominoType? get() = queue.firstOrNull()

    val activeCellPositions: List<TetrominoCell>
        get() = active?.let { piece -> absoluteCells(piece) }.orEmpty()

    fun tick() {
        if (gameOver) return
        val piece = active ?: return
        if (!collides(piece, rowOffset = 1)) {
            active = piece.copy(row = piece.row + 1)
        } else {
            lockAndSpawnNext()
        }
    }

    fun moveLeft(): Boolean {
        val piece = active ?: return false
        if (collides(piece, colOffset = -1)) return false
        active = piece.copy(col = piece.col - 1)
        return true
    }

    fun moveRight(): Boolean {
        val piece = active ?: return false
        if (collides(piece, colOffset = 1)) return false
        active = piece.copy(col = piece.col + 1)
        return true
    }

    fun rotate(): Boolean {
        val piece = active ?: return false
        val nextRotation = (piece.rotation + 1) % 4
        for (kick in KICK_OFFSETS) {
            if (!collides(piece, colOffset = kick, rotation = nextRotation)) {
                active = piece.copy(rotation = nextRotation, col = piece.col + kick)
                return true
            }
        }
        return false
    }

    fun softDrop(): Boolean {
        val piece = active ?: return false
        if (!collides(piece, rowOffset = 1)) {
            active = piece.copy(row = piece.row + 1)
            score += 1
        } else {
            lockAndSpawnNext()
        }
        return true
    }

    fun hardDrop() {
        if (gameOver) return
        var piece = active ?: return
        var droppedRows = 0
        while (!collides(piece, rowOffset = 1)) {
            piece = piece.copy(row = piece.row + 1)
            droppedRows += 1
        }
        score += droppedRows * 2
        active = piece
        lockAndSpawnNext()
    }

    fun gravityDelayMillis(): Long {
        val delay = START_GRAVITY_MILLIS - (level - 1) * 65L
        return delay.coerceAtLeast(MIN_GRAVITY_MILLIS)
    }

    fun snapshot(): TetrisSnapshot = TetrisSnapshot(
        board = board.toList(),
        active = active?.let { piece ->
            PieceSnapshot(
                type = piece.type,
                rotation = piece.rotation,
                row = piece.row,
                col = piece.col,
            )
        },
        queue = queue.toList(),
        score = score,
        lines = lines,
        level = level,
        startLevel = startLevel,
        gameOver = gameOver,
    )

    private fun lockAndSpawnNext() {
        val piece = active
        if (piece != null) {
            absoluteCells(piece).forEach { cell ->
                if (cell.row in 0 until TETRIS_ROWS && cell.col in 0 until TETRIS_COLUMNS) {
                    board[cell.row * TETRIS_COLUMNS + cell.col] = piece.type.ordinal + 1
                }
            }
        }
        active = null

        val cleared = clearCompletedLines()
        if (cleared > 0) {
            score += LINE_CLEAR_SCORES[cleared.coerceAtMost(4)] * level
            lines += cleared
            level = startLevel + lines / LINES_PER_LEVEL
        }
        spawnNextPiece()
    }

    private fun clearCompletedLines(): Int {
        val clearedBoard = IntArray(TETRIS_ROWS * TETRIS_COLUMNS)
        var writeRow = TETRIS_ROWS - 1
        var cleared = 0

        for (readRow in TETRIS_ROWS - 1 downTo 0) {
            val isFull = (0 until TETRIS_COLUMNS).all { column ->
                board[readRow * TETRIS_COLUMNS + column] != 0
            }
            if (isFull) {
                cleared += 1
            } else {
                for (column in 0 until TETRIS_COLUMNS) {
                    clearedBoard[writeRow * TETRIS_COLUMNS + column] =
                        board[readRow * TETRIS_COLUMNS + column]
                }
                writeRow -= 1
            }
        }

        board = clearedBoard
        return cleared
    }

    private fun spawnNextPiece() {
        ensureQueue()
        val type = queue.removeFirst()
        ensureQueue()
        val spawnColumn = when (type) {
            TetrominoType.I -> 3
            TetrominoType.O -> 4
            else -> 3
        }
        val piece = ActivePiece(
            type = type,
            rotation = 0,
            row = 0,
            col = spawnColumn,
        )
        if (collides(piece)) {
            active = null
            gameOver = true
        } else {
            active = piece
        }
    }

    private fun ensureQueue() {
        while (queue.size < 2) {
            val bag = TetrominoType.entries.toMutableList()
            bag.shuffle(random)
            queue.addAll(bag)
        }
    }

    private fun collides(
        piece: ActivePiece,
        rowOffset: Int = 0,
        colOffset: Int = 0,
        rotation: Int = piece.rotation,
    ): Boolean {
        val cells = rotationsFor(piece.type, rotation)
        for (cell in cells) {
            val row = piece.row + rowOffset + cell.row
            val col = piece.col + colOffset + cell.col
            if (col !in 0 until TETRIS_COLUMNS || row >= TETRIS_ROWS) return true
            if (row >= 0 && board[row * TETRIS_COLUMNS + col] != 0) return true
        }
        return false
    }

    private fun absoluteCells(piece: ActivePiece): List<TetrominoCell> =
        rotationsFor(piece.type, piece.rotation).map { cell ->
            TetrominoCell(
                row = piece.row + cell.row,
                col = piece.col + cell.col,
            )
        }

    companion object {
        private const val LINES_PER_LEVEL = 10
        private const val START_GRAVITY_MILLIS = 900L
        private const val MIN_GRAVITY_MILLIS = 80L
        private val KICK_OFFSETS = intArrayOf(0, -1, 1, -2, 2)
        private val LINE_CLEAR_SCORES = intArrayOf(0, 100, 300, 500, 800)

        fun newGame(
            random: Random = Random.Default,
            startLevel: Int = 1,
        ): TetrisEngine {
            val engine = TetrisEngine(
                random = random,
                board = IntArray(TETRIS_ROWS * TETRIS_COLUMNS),
                active = null,
                queue = ArrayDeque(),
                score = 0,
                lines = 0,
                level = startLevel.coerceIn(1, 20),
                startLevel = startLevel.coerceIn(1, 20),
                gameOver = false,
            )
            engine.spawnNextPiece()
            return engine
        }

        fun restore(
            snapshot: TetrisSnapshot,
            random: Random = Random.Default,
        ): TetrisEngine {
            val board = IntArray(TETRIS_ROWS * TETRIS_COLUMNS) { index ->
                snapshot.board.getOrNull(index)?.coerceIn(0, 7) ?: 0
            }
            val active = snapshot.active?.let { piece ->
                ActivePiece(
                    type = piece.type,
                    rotation = piece.rotation.mod(4),
                    row = piece.row,
                    col = piece.col,
                )
            }
            val engine = TetrisEngine(
                random = random,
                board = board,
                active = active,
                queue = ArrayDeque(snapshot.queue),
                score = snapshot.score.coerceAtLeast(0),
                lines = snapshot.lines.coerceAtLeast(0),
                level = snapshot.level.coerceAtLeast(1),
                startLevel = snapshot.startLevel.coerceAtLeast(1),
                gameOver = snapshot.gameOver,
            )
            if (!engine.gameOver) {
                engine.ensureQueue()
            }
            return engine
        }

        fun previewCells(
            type: TetrominoType,
            rotation: Int = 0,
        ): List<TetrominoCell> = rotationsFor(type, rotation.mod(4))
    }
}

private fun rotationsFor(
    type: TetrominoType,
    rotation: Int,
): List<TetrominoCell> {
    val states = PIECE_ROTATIONS.getValue(type)
    return states[rotation.mod(states.size)]
}

private val PIECE_ROTATIONS: Map<TetrominoType, List<List<TetrominoCell>>> = buildMap {
    TetrominoType.entries.forEach { type ->
        val base = when (type) {
            TetrominoType.I -> listOf(
                TetrominoCell(1, 0),
                TetrominoCell(1, 1),
                TetrominoCell(1, 2),
                TetrominoCell(1, 3),
            )

            TetrominoType.O -> listOf(
                TetrominoCell(0, 0),
                TetrominoCell(0, 1),
                TetrominoCell(1, 0),
                TetrominoCell(1, 1),
            )

            TetrominoType.T -> listOf(
                TetrominoCell(0, 1),
                TetrominoCell(1, 0),
                TetrominoCell(1, 1),
                TetrominoCell(1, 2),
            )

            TetrominoType.J -> listOf(
                TetrominoCell(0, 0),
                TetrominoCell(1, 0),
                TetrominoCell(1, 1),
                TetrominoCell(1, 2),
            )

            TetrominoType.L -> listOf(
                TetrominoCell(0, 2),
                TetrominoCell(1, 0),
                TetrominoCell(1, 1),
                TetrominoCell(1, 2),
            )

            TetrominoType.S -> listOf(
                TetrominoCell(0, 1),
                TetrominoCell(0, 2),
                TetrominoCell(1, 0),
                TetrominoCell(1, 1),
            )

            TetrominoType.Z -> listOf(
                TetrominoCell(0, 0),
                TetrominoCell(0, 1),
                TetrominoCell(1, 1),
                TetrominoCell(1, 2),
            )
        }

        if (type == TetrominoType.O) {
            put(type, List(4) { base })
        } else {
            val states = mutableListOf<List<TetrominoCell>>()
            var current = base
            repeat(4) {
                states += current
                val matrixSize = if (type == TetrominoType.I) 4 else 3
                current = current.map { cell ->
                    TetrominoCell(
                        row = cell.col,
                        col = matrixSize - 1 - cell.row,
                    )
                }
            }
            put(type, states)
        }
    }
}
