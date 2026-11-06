package com.playbox.app.games.game2048

import kotlin.random.Random
import kotlinx.serialization.Serializable

enum class MoveDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

@Serializable
data class Tile2048(
    val id: Int,
    val value: Int,
    val row: Int,
    val col: Int,
)

@Serializable
data class UndoState2048(
    val tiles: List<Tile2048>,
    val score: Int,
)

@Serializable
data class Snapshot2048(
    val tiles: List<Tile2048> = emptyList(),
    val score: Int = 0,
    val nextTileId: Int = 1,
    val isGameOver: Boolean = false,
    val undo: UndoState2048? = null,
    val hasUsedUndo: Boolean = false,
)

data class TileMotion(
    val tileId: Int,
    val value: Int,
    val fromRow: Int,
    val fromCol: Int,
    val toRow: Int,
    val toCol: Int,
)

data class MoveAnimation(
    val id: Long,
    val motions: List<TileMotion>,
)

data class MoveOutcome(
    val moved: Boolean,
    val scoreGained: Int,
    val animation: MoveAnimation?,
    val isGameOver: Boolean,
)

/**
 * 2048 的纯 Kotlin 引擎，不依赖 Compose 或 Android。
 *
 * 每次有效移动都会输出所有旧方块的起点/终点，界面用它绘制滑动补间；
 * 移动完成后再切换到引擎的新棋盘（包含随机生成的新方块）。
 */
class Game2048Engine private constructor(
    private val random: Random,
    initialSnapshot: Snapshot2048,
) {
    private val tiles: MutableList<Tile2048> = initialSnapshot.tiles.toMutableList()
    private var score: Int = initialSnapshot.score
    private var nextTileId: Int = initialSnapshot.nextTileId
    private var isGameOver: Boolean = initialSnapshot.isGameOver
    private var undoState: UndoState2048? = initialSnapshot.undo
    private var hasUsedUndo: Boolean = initialSnapshot.hasUsedUndo
    private var animationCounter: Long = 0L

    val boardTiles: List<Tile2048> get() = tiles.toList()
    val currentScore: Int get() = score
    val gameOver: Boolean get() = isGameOver
    val canUndo: Boolean get() = undoState != null && !hasUsedUndo
    val largestTile: Int get() = tiles.maxOfOrNull { it.value } ?: 0

    fun move(direction: MoveDirection): MoveOutcome {
        if (isGameOver) {
            return MoveOutcome(
                moved = false,
                scoreGained = 0,
                animation = null,
                isGameOver = true,
            )
        }

        val beforeTiles = tiles.toList()
        val beforeScore = score
        val newTiles = mutableListOf<Tile2048>()
        val motions = mutableListOf<TileMotion>()
        var gained = 0
        var merged = false
        var nextId = nextTileId

        for (lineIndex in 0 until BOARD_SIZE) {
            val line = tilesForLine(direction, lineIndex)
            var writeIndex = 0
            var index = 0

            while (index < line.size) {
                val first = line[index]
                val target = targetCell(direction, lineIndex, writeIndex)
                if (index + 1 < line.size && line[index + 1].value == first.value) {
                    val second = line[index + 1]
                    motions += first.toMotion(target)
                    motions += second.toMotion(target)
                    val mergedValue = first.value * 2
                    gained += mergedValue
                    merged = true
                    newTiles += Tile2048(
                        id = nextId++,
                        value = mergedValue,
                        row = target.row,
                        col = target.col,
                    )
                    index += 2
                } else {
                    motions += first.toMotion(target)
                    newTiles += first.copy(row = target.row, col = target.col)
                    index += 1
                }
                writeIndex += 1
            }
        }

        val movedByPosition = motions.any { motion ->
            motion.fromRow != motion.toRow || motion.fromCol != motion.toCol
        }
        if (!movedByPosition && !merged) {
            return MoveOutcome(
                moved = false,
                scoreGained = 0,
                animation = null,
                isGameOver = isGameOver,
            )
        }

        undoState = UndoState2048(tiles = beforeTiles, score = beforeScore)
        tiles.clear()
        tiles.addAll(newTiles)
        score = beforeScore + gained
        nextTileId = nextId
        spawnRandomTile()
        isGameOver = !hasAvailableMove()

        return MoveOutcome(
            moved = true,
            scoreGained = gained,
            animation = MoveAnimation(
                id = ++animationCounter,
                motions = motions,
            ),
            isGameOver = isGameOver,
        )
    }

    fun undo(): Boolean {
        if (hasUsedUndo) return false
        val previous = undoState ?: return false
        tiles.clear()
        tiles.addAll(previous.tiles)
        score = previous.score
        undoState = null
        hasUsedUndo = true
        isGameOver = false
        return true
    }

    fun snapshot(): Snapshot2048 = Snapshot2048(
        tiles = tiles.toList(),
        score = score,
        nextTileId = nextTileId,
        isGameOver = isGameOver,
        undo = undoState,
        hasUsedUndo = hasUsedUndo,
    )

    private fun tilesForLine(
        direction: MoveDirection,
        lineIndex: Int,
    ): List<Tile2048> {
        val line = when (direction) {
            MoveDirection.LEFT, MoveDirection.RIGHT -> {
                tiles.filter { it.row == lineIndex }
            }

            MoveDirection.UP, MoveDirection.DOWN -> {
                tiles.filter { it.col == lineIndex }
            }
        }

        return when (direction) {
            MoveDirection.LEFT, MoveDirection.UP -> line.sortedBy { if (direction == MoveDirection.LEFT) it.col else it.row }
            MoveDirection.RIGHT, MoveDirection.DOWN -> line.sortedByDescending { if (direction == MoveDirection.RIGHT) it.col else it.row }
        }
    }

    private fun targetCell(
        direction: MoveDirection,
        lineIndex: Int,
        writeIndex: Int,
    ): Cell = when (direction) {
        MoveDirection.LEFT -> Cell(lineIndex, writeIndex)
        MoveDirection.RIGHT -> Cell(lineIndex, BOARD_SIZE - 1 - writeIndex)
        MoveDirection.UP -> Cell(writeIndex, lineIndex)
        MoveDirection.DOWN -> Cell(BOARD_SIZE - 1 - writeIndex, lineIndex)
    }

    private fun Tile2048.toMotion(target: Cell) = TileMotion(
        tileId = id,
        value = value,
        fromRow = row,
        fromCol = col,
        toRow = target.row,
        toCol = target.col,
    )

    private fun spawnRandomTile() {
        val occupied = tiles.map { it.row to it.col }.toSet()
        val emptyCells = buildList {
            for (row in 0 until BOARD_SIZE) {
                for (col in 0 until BOARD_SIZE) {
                    if ((row to col) !in occupied) add(Cell(row, col))
                }
            }
        }
        if (emptyCells.isEmpty()) return

        val cell = emptyCells[random.nextInt(emptyCells.size)]
        val value = if (random.nextInt(10) == 0) 4 else 2
        tiles += Tile2048(
            id = nextTileId++,
            value = value,
            row = cell.row,
            col = cell.col,
        )
    }

    private fun hasAvailableMove(): Boolean {
        if (tiles.size < BOARD_SIZE * BOARD_SIZE) return true
        val values = Array(BOARD_SIZE) { IntArray(BOARD_SIZE) }
        tiles.forEach { tile -> values[tile.row][tile.col] = tile.value }
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                val value = values[row][col]
                if (row + 1 < BOARD_SIZE && values[row + 1][col] == value) return true
                if (col + 1 < BOARD_SIZE && values[row][col + 1] == value) return true
            }
        }
        return false
    }

    private data class Cell(val row: Int, val col: Int)

    companion object {
        const val BOARD_SIZE = 4

        fun newGame(random: Random = Random.Default): Game2048Engine {
            val engine = Game2048Engine(
                random = random,
                initialSnapshot = Snapshot2048(),
            )
            engine.spawnRandomTile()
            engine.spawnRandomTile()
            return engine
        }

        /**
         * 从快照恢复。也用于确定性测试和进程重建。
         */
        fun restore(
            snapshot: Snapshot2048,
            random: Random = Random.Default,
        ): Game2048Engine = Game2048Engine(
            random = random,
            initialSnapshot = snapshot,
        )
    }
}
