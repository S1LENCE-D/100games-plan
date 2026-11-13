package com.playbox.app.games.gomoku

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.random.Random

const val GOMOKU_BOARD_SIZE = 15
const val GOMOKU_BLACK = 1
const val GOMOKU_WHITE = 2

@Immutable
data class GomokuPoint(
    val row: Int,
    val column: Int,
)

@Immutable
data class GomokuMove(
    val row: Int,
    val column: Int,
    val player: Int,
)

enum class GomokuMode {
    AI,
    LOCAL,
}

class GomokuEngine(
    val size: Int = GOMOKU_BOARD_SIZE,
    private val random: Random = Random.Default,
) {
    private val cells = IntArray(size * size)
    private val moves = mutableListOf<GomokuMove>()
    var currentPlayer: Int = GOMOKU_BLACK
        private set
    var winner: Int = 0
        private set
    var winningLine: List<GomokuPoint> = emptyList()
        private set
    var lastMove: GomokuMove? = null
        private set

    val board: List<Int> get() = cells.toList()
    val moveCount: Int get() = moves.size
    val isDraw: Boolean
        get() = winner == 0 && moves.size >= size * size
    val isGameOver: Boolean get() = winner != 0 || isDraw

    fun cell(row: Int, column: Int): Int {
        if (row !in 0 until size || column !in 0 until size) return -1
        return cells[row * size + column]
    }

    fun place(row: Int, column: Int): Boolean {
        if (isGameOver || row !in 0 until size || column !in 0 until size) return false
        if (cells[row * size + column] != 0) return false
        val player = currentPlayer
        cells[row * size + column] = player
        val move = GomokuMove(row, column, player)
        moves += move
        lastMove = move
        val win = findWinAt(row, column, player)
        if (win.isNotEmpty()) {
            winner = player
            winningLine = win
        } else {
            currentPlayer = other(player)
        }
        return true
    }

    fun undo(): Boolean {
        val move = moves.removeLastOrNull() ?: return false
        cells[move.row * size + move.column] = 0
        winner = 0
        winningLine = emptyList()
        currentPlayer = move.player
        lastMove = moves.lastOrNull()
        return true
    }

    fun reset() {
        cells.fill(0)
        moves.clear()
        currentPlayer = GOMOKU_BLACK
        winner = 0
        winningLine = emptyList()
        lastMove = null
    }

    fun aiMove(): GomokuMove? {
        if (isGameOver) return null
        val ai = currentPlayer
        val human = other(ai)
        val candidates = candidateCells()
        if (candidates.isEmpty()) {
            val center = size / 2
            return if (place(center, center)) moves.last() else null
        }

        candidates.firstOrNull { wouldWin(it, ai) }?.let { point ->
            return if (place(point.row, point.column)) moves.last() else null
        }
        candidates.firstOrNull { wouldWin(it, human) }?.let { point ->
            return if (place(point.row, point.column)) moves.last() else null
        }

        var bestScore = Long.MIN_VALUE
        val best = mutableListOf<GomokuPoint>()
        candidates.forEach { point ->
            val attack = lineScore(point.row, point.column, ai)
            val defense = lineScore(point.row, point.column, human)
            val center = size - abs(point.row - size / 2) - abs(point.column - size / 2)
            val score = attack * 11 + defense * 10 + center
            when {
                score > bestScore -> {
                    bestScore = score
                    best.clear()
                    best += point
                }
                score == bestScore -> best += point
            }
        }
        val point = best.randomOrNull(random) ?: return null
        return if (place(point.row, point.column)) moves.last() else null
    }

    private fun candidateCells(): List<GomokuPoint> {
        if (moves.isEmpty()) return listOf(GomokuPoint(size / 2, size / 2))
        val seen = BooleanArray(size * size)
        val result = mutableListOf<GomokuPoint>()
        moves.forEach { move ->
            for (dr in -2..2) {
                for (dc in -2..2) {
                    if (dr == 0 && dc == 0) continue
                    val row = move.row + dr
                    val column = move.column + dc
                    if (row !in 0 until size || column !in 0 until size) continue
                    val index = row * size + column
                    if (cells[index] == 0 && !seen[index]) {
                        seen[index] = true
                        result += GomokuPoint(row, column)
                    }
                }
            }
        }
        return result
    }

    private fun wouldWin(point: GomokuPoint, player: Int): Boolean {
        cells[point.row * size + point.column] = player
        val win = findWinAt(point.row, point.column, player).isNotEmpty()
        cells[point.row * size + point.column] = 0
        return win
    }

    private fun lineScore(row: Int, column: Int, player: Int): Long {
        cells[row * size + column] = player
        var total = 0L
        for (direction in DIRECTIONS) {
            total += patternScore(row, column, direction.first, direction.second, player)
        }
        cells[row * size + column] = 0
        return total
    }

    private fun patternScore(row: Int, column: Int, dr: Int, dc: Int, player: Int): Long {
        var count = 1
        var openA = false
        var openB = false
        var r = row + dr
        var c = column + dc
        while (r in 0 until size && c in 0 until size && cells[r * size + c] == player) {
            count += 1
            r += dr
            c += dc
        }
        if (r in 0 until size && c in 0 until size && cells[r * size + c] == 0) openA = true
        r = row - dr
        c = column - dc
        while (r in 0 until size && c in 0 until size && cells[r * size + c] == player) {
            count += 1
            r -= dr
            c -= dc
        }
        if (r in 0 until size && c in 0 until size && cells[r * size + c] == 0) openB = true
        val openEnds = (if (openA) 1 else 0) + (if (openB) 1 else 0)
        return when {
            count >= 5 -> 10_000_000L
            count == 4 && openEnds == 2 -> 1_000_000L
            count == 4 && openEnds == 1 -> 120_000L
            count == 3 && openEnds == 2 -> 100_000L
            count == 3 && openEnds == 1 -> 12_000L
            count == 2 && openEnds == 2 -> 2_000L
            count == 2 && openEnds == 1 -> 300L
            count == 1 && openEnds == 2 -> 40L
            else -> 0L
        }
    }

    private fun findWinAt(row: Int, column: Int, player: Int): List<GomokuPoint> {
        for ((dr, dc) in DIRECTIONS) {
            val line = mutableListOf<GomokuPoint>()
            var r = row
            var c = column
            while (r in 0 until size && c in 0 until size && cells[r * size + c] == player) {
                line += GomokuPoint(r, c)
                r -= dr
                c -= dc
            }
            line.reverse()
            r = row + dr
            c = column + dc
            while (r in 0 until size && c in 0 until size && cells[r * size + c] == player) {
                line += GomokuPoint(r, c)
                r += dr
                c += dc
            }
            if (line.size >= 5) return line
        }
        return emptyList()
    }

    companion object {
        private val DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

        fun other(player: Int): Int = if (player == GOMOKU_BLACK) GOMOKU_WHITE else GOMOKU_BLACK
    }
}
