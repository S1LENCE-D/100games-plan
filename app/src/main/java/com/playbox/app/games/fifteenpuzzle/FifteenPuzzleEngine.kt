package com.playbox.app.games.fifteenpuzzle

import kotlin.random.Random

/**
 * 4×4 数字华容道。0 表示空位；通过从完成局面随机移动生成保证有解的开局。
 */
class FifteenPuzzleEngine private constructor(
    private val cells: IntArray,
    private var blankIndex: Int,
    private var moves: Int,
) {
    fun cellAt(index: Int): Int = cells[index]
    fun currentMoves(): Int = moves
    fun isSolved(): Boolean = cells.indices.all { index ->
        if (index == 15) cells[index] == 0 else cells[index] == index + 1
    }

    fun move(index: Int): Boolean = slideTo(index) > 0

    /**
     * 点击同行/同列的数字时，把空位移动到目标格，中间所有滑块依次滑动。
     * 返回滑动滑块数量；返回 0 表示目标不在空位所在的行或列。
     */
    fun slideTo(index: Int): Int {
        if (index !in 0..15 || index == blankIndex) return 0
        val targetRow = index / 4
        val targetColumn = index % 4
        val blankRow = blankIndex / 4
        val blankColumn = blankIndex % 4
        if (targetRow != blankRow && targetColumn != blankColumn) return 0
        var moved = 0
        while (blankIndex != index) {
            val currentRow = blankIndex / 4
            val currentColumn = blankIndex % 4
            val next = when {
                targetRow == currentRow && targetColumn > currentColumn -> blankIndex + 1
                targetRow == currentRow && targetColumn < currentColumn -> blankIndex - 1
                targetColumn == currentColumn && targetRow > currentRow -> blankIndex + 4
                targetColumn == currentColumn && targetRow < currentRow -> blankIndex - 4
                else -> return moved
            }
            cells[blankIndex] = cells[next]
            cells[next] = 0
            blankIndex = next
            moves += 1
            moved += 1
        }
        return moved
    }

    /** 滑动方向：-1/0/1 的 dx、dy；滑块沿手势方向滑入空位。 */
    fun swipe(dx: Int, dy: Int): Int {
        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) != 1) return 0
        val row = blankIndex / 4
        val column = blankIndex % 4
        // 空位反方向上的滑块沿着手势方向移动；例如上滑时，空位下方的滑块上移。
        val targetRow = row - dy
        val targetColumn = column - dx
        if (targetRow !in 0..3 || targetColumn !in 0..3) return 0
        return slideTo(targetRow * 4 + targetColumn)
    }

    companion object {
        fun newGame(random: Random = Random.Default): FifteenPuzzleEngine {
            val cells = IntArray(16) { if (it == 15) 0 else it + 1 }
            var blank = 15
            repeat(220) {
                val candidates = mutableListOf<Int>()
                val row = blank / 4
                val column = blank % 4
                if (row > 0) candidates += blank - 4
                if (row < 3) candidates += blank + 4
                if (column > 0) candidates += blank - 1
                if (column < 3) candidates += blank + 1
                val next = candidates.random(random)
                cells[blank] = cells[next]
                cells[next] = 0
                blank = next
            }
            val engine = FifteenPuzzleEngine(cells = cells, blankIndex = blank, moves = 0)
            if (engine.isSolved()) {
                cells[blank] = cells[14]
                cells[14] = 0
                return FifteenPuzzleEngine(cells = cells, blankIndex = 14, moves = 0)
            }
            return engine
        }
    }
}
